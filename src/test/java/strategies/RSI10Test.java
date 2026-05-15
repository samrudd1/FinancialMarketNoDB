package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;
import trade.Exchange;
import trade.TradingCycle;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RSI10 strategy decision logic.
 *
 * Buys from lowest ask when RSI10 (rsiP) < 30 (oversold).
 * Sells to highest bid when RSI10 > 70 (overbought).
 * Does nothing when rsiP is NaN.
 *
 * Exchange.rsiP=0 after reset, triggering the buy branch naturally.
 * Getting rsiP=NaN uses 151 equal prices → avgGain=avgLoss=0 → 0/0.
 */
class RSI10Test extends GlobalStateFixture {

    private Good good;
    private float base;
    private TradingCycle tc;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
        tc = new TradingCycle();
    }

    // ── buys when RSI10 is oversold (< 30) ───────────────────────────────────

    @Test
    void buysWhenRsiPIsOversold() throws InterruptedException {
        // Exchange.rsiP=0 after reset < 30 → buy branch fires
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 200); // within 3% of price ✓
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new RSI10(buyer, tc, 161).run(); // roundNum > 160 for RSI10 gate

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares when RSI10 signals oversold").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }

    // ── no action when rsiP is NaN ────────────────────────────────────────────

    @Test
    void noActionWhenRsiPIsNaN() throws InterruptedException {
        // 151 equal prices → avgGain=avgLoss=0 → rsiP=NaN after execution triggers RSI10 calc
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 151; i++) rfp.add(50f);

        Agent setupSeller = new Agent();
        OwnedGood setupOwned = new OwnedGood(setupSeller, good, 200, 190, base, true);
        setupSeller.getGoodsOwned().add(0, setupOwned);
        Offer setupAsk = new Offer(base, setupSeller, good, 5);
        good.addAsk(setupAsk);
        Agent setupBuyer = new Agent();
        setupBuyer.setFunds(base * 1000f);
        Exchange.getInstance().execute(setupBuyer, setupSeller, setupAsk, 5, tc, 1);
        // rsiP = NaN because all prices equal → no diff variation

        Agent agent = new Agent();
        agent.setFunds(base * 5000f);
        int tradesBefore = Good.getNumTrades();

        new RSI10(agent, tc, 1).run();

        assertThat(Good.getNumTrades()).as("no trade when RSI10 value is NaN").isEqualTo(tradesBefore);
    }

    // ── no trade when no ask on book despite oversold RSI ────────────────────

    @Test
    void noTradeWhenNoAskAvailableEvenIfOversold() throws InterruptedException {
        // rsiP=0 (default) < 30 → wants to buy, but book is empty → null ask → no trade
        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        int tradesBefore = Good.getNumTrades();
        new RSI10(buyer, tc, 161).run();

        assertThat(Good.getNumTrades()).as("no trade when ask book is empty even with oversold RSI10")
                .isEqualTo(tradesBefore);
        assertThat(buyer.getGoodsOwned()).isEmpty();
    }
}
