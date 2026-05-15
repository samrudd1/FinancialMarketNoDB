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
 * Verifies RSI strategy decision logic.
 *
 * Buys from lowest ask when RSI < 20 (oversold).
 * Sells to highest bid when RSI > 80 (overbought).
 * Does nothing when RSI is NaN (no valid price history).
 *
 * Exchange.rsi=0 after reset, which is < 20, so the buy branch fires naturally.
 * Getting rsi=100 requires pre-populating roundFinalPrice with strictly increasing
 * values ending just below base, then executing a trade at base (all-gains).
 */
class RSIStrategyTest extends GlobalStateFixture {

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

    // ── buys when RSI is oversold (< 20) ─────────────────────────────────────

    @Test
    void buysWhenRsiIsOversold() throws InterruptedException {
        // Exchange.rsi=0 (after reset) < 20 → buy branch fires
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 200); // within 3% of price ✓
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new RSI(buyer, tc, 17).run(); // roundNum > 16 for RSI gate

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares when RSI signals oversold").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }

    // ── sells when RSI is overbought (> 80) ──────────────────────────────────

    @Test
    void sellsWhenRsiIsOverbought() throws InterruptedException {
        // Pre-populate with 16 strictly increasing prices ending at base-1,
        // then trade at base (new high) → all 14 diffs are gains → rsi=100
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 16; i++) rfp.add(base - 16f + i); // base-16 to base-1

        Agent setupSeller = new Agent();
        OwnedGood setupOwned = new OwnedGood(setupSeller, good, 200, 190, base, true);
        setupSeller.getGoodsOwned().add(0, setupOwned);
        Offer setupAsk = new Offer(base, setupSeller, good, 5);
        good.addAsk(setupAsk);
        Agent setupBuyer = new Agent();
        setupBuyer.setFunds(base * 1000f);
        Exchange.getInstance().execute(setupBuyer, setupSeller, setupAsk, 5, tc, 1);
        // rsi is now computed = 100 (all gains)

        // Add a bid for RSI strategy to sell into
        Agent buyer = new Agent();
        buyer.setFunds(base * 10000f);
        float bidPrice = base * 0.99f;
        Offer bid = new Offer(bidPrice, buyer, good, 200);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - bidPrice * 200);
        buyer.setPlacedBid(true);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 1000, 1000, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        new RSI(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned()).as("seller shares should decrease when RSI signals overbought")
                .isLessThan(1000);
    }

    // ── no action when RSI is NaN ─────────────────────────────────────────────

    @Test
    void noActionWhenRsiIsNaN() throws InterruptedException {
        // Pre-populate with 16 equal prices → avgGain=0, avgLoss=0 → rsi=NaN → strategy skips all action
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 16; i++) rfp.add(50f);

        Agent setupSeller = new Agent();
        OwnedGood setupOwned = new OwnedGood(setupSeller, good, 200, 190, base, true);
        setupSeller.getGoodsOwned().add(0, setupOwned);
        Offer setupAsk = new Offer(base, setupSeller, good, 5);
        good.addAsk(setupAsk);
        Agent setupBuyer = new Agent();
        setupBuyer.setFunds(base * 1000f);
        Exchange.getInstance().execute(setupBuyer, setupSeller, setupAsk, 5, tc, 1);
        // rsi = NaN because avgGain=0 and avgLoss=0 → 0/0

        Agent agent = new Agent();
        agent.setFunds(base * 5000f);
        int tradesBefore = Good.getNumTrades();

        new RSI(agent, tc, 1).run();

        assertThat(Good.getNumTrades()).as("no trade when RSI is NaN (invalid)").isEqualTo(tradesBefore);
    }
}
