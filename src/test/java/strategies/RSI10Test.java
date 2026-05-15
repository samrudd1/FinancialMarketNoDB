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

    // ── sweeps multiple ask levels in a single oversold round ───────────────

    @Test
    void sweepsMultipleAskLevelsWhenOversold() throws InterruptedException {
        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        Offer stub = new Offer(base * 0.5f, stubBidder, good, 1);
        good.addBid(stub);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer a1 = new Offer(base * 1.00f, seller, good, 3);
        Offer a2 = new Offer(base * 1.01f, seller, good, 3);
        Offer a3 = new Offer(base * 1.02f, seller, good, 3);
        good.addAsk(a1); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 3);
        good.addAsk(a2); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 3);
        good.addAsk(a3); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 3);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new RSI10(buyer, tc, 161).run();

        assertThat(buyer.getGoodsOwned()).as("RSI10 must buy when oversold").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned())
                .as("RSI10 sweep should drain more than just the top-of-book ask")
                .isGreaterThan(3);
    }

    // ── boundary cap stops sweep tighter than the 3% safety ─────────────────

    @Test
    void sweepCapTightensWhenRsi10BoundaryIsBelowSafetyCeiling() throws InterruptedException {
        // RSI10 uses 14 diffs at stride 10 (looks back 140 rounds, needs rfp.size() >= 141).
        // History: 140 entries at base, then one 2% drop. The stride-10 diff at i=140 is -0.02
        // (rfp[140]/rfp[130]), all others are 0, and the dropping diff at i=10 is 0. With
        // targetRatio = 30/70 ≈ 0.4286, newDiff = 0.4286 * 0.02 ≈ 0.00857, so the boundary
        // sits at rfp[131] * 1.00857 ≈ base * 1.00857 — well below the 3% safety ceiling.
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 140; i++) rfp.add(base);
        rfp.add(base * 0.98f);

        Agent dummy = new Agent();
        good.setPrice(new Offer(base * 0.98f, dummy, good, 1), 1);
        Exchange.lastPrice = base * 0.98f;
        Exchange.getInstance().setPriceCheck(base * 0.98f);

        // Seed bid side so subsequent addAsk calls aren't rejected by the one-sided guard.
        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        // Two asks below the ~1.00857*base boundary, two above (but still within 3% safety).
        Offer a1 = new Offer(base * 0.990f, seller, good, 5); // below boundary
        Offer a2 = new Offer(base * 1.005f, seller, good, 5); // below boundary
        Offer a3 = new Offer(base * 1.010f, seller, good, 5); // above boundary, below 3% safety
        Offer a4 = new Offer(base * 1.020f, seller, good, 5); // well above boundary
        good.addAsk(a1); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a2); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a3); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a4); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new RSI10(buyer, tc, 161).run();

        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned())
                .as("RSI10 boundary cap should stop the sweep short of the +1%/+2% asks")
                .isEqualTo(10);
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
