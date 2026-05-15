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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mean-reversion VWAP decision logic.
 *
 * <p>Mirrors {@link VWAP} but with inverted triggers: buys when price has
 * fallen meaningfully below the rolling VWAP, sells when it has risen
 * meaningfully above. {@code vwapMRPosition} is the per-agent position
 * counter and ±5 cap.
 */
class VWAPMeanReversionTest extends GlobalStateFixture {

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

    private void primeVwap(float priceLevel, int volume) {
        Good.addTradeData(priceLevel, volume, 0);
    }

    private void primePrice(float newPrice) {
        Agent dummy = new Agent();
        good.setPrice(new Offer(newPrice, dummy, good, 1), 1);
        Exchange.lastPrice = newPrice;
        Exchange.getInstance().setPriceCheck(newPrice);
    }

    // ── buys when price is meaningfully below VWAP (bargain hunting) ─────────

    @Test
    void buysWhenPriceBelowVwap() throws InterruptedException {
        // VWAP at base, current price 1% below → bargain
        primeVwap(base * 1.02f, 1000);
        primePrice(base);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("MR should buy when price is below VWAP").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
        assertThat(buyer.getVwapMRPosition()).isEqualTo(1);
    }

    // ── sells when price is meaningfully above VWAP (profit taking) ──────────

    @Test
    void sellsWhenPriceAboveVwap() throws InterruptedException {
        primeVwap(base, 1000);
        primePrice(base * 1.01f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        Agent bidder = new Agent();
        bidder.setFunds(base * 10000f);
        float bidPrice = base * 1.01f;
        Offer bid = new Offer(bidPrice, bidder, good, 100);
        good.addBid(bid);
        bidder.setFunds(bidder.getFunds() - bidPrice * 100);

        int ownedBefore = sellerOwned.getNumOwned();
        new VWAPMeanReversion(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned())
                .as("MR should sell when price is above VWAP")
                .isLessThan(ownedBefore);
        assertThat(seller.getVwapMRPosition()).isEqualTo(-1);
    }

    // ── sweeps multiple ask levels when price is below VWAP ─────────────────

    @Test
    void sweepsMultipleAskLevelsWhenPriceBelowVwap() throws InterruptedException {
        primeVwap(base * 1.02f, 1000);
        primePrice(base);

        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        // Two small asks both within MR's price * 1.01 buy ceiling.
        Offer a1 = new Offer(base, seller, good, 5);
        Offer a2 = new Offer(base * 1.005f, seller, good, 5);
        good.addAsk(a1); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a2); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("MR must trade when below VWAP").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned())
                .as("MR sweep should consume more than a single top-of-book level")
                .isGreaterThan(5);
        // Position counter increments per call, not per level swept.
        assertThat(buyer.getVwapMRPosition()).as("position counter increments once per sweep").isEqualTo(1);
    }

    // ── no action when deviation is below threshold ──────────────────────────

    @Test
    void noActionWhenDeviationBelowThreshold() throws InterruptedException {
        // 0.1% deviation — below the 0.5% envelope
        primeVwap(base, 1000);
        primePrice(base * 0.999f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        int tradesBefore = Good.getNumTrades();
        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(Good.getNumTrades()).as("no trade inside the envelope").isEqualTo(tradesBefore);
    }

    // ── sigma-based trigger in a quiet market fires on sub-0.5% deviations ──

    @Test
    void quietMarketSigmaTriggerFiresOnSmallDeviation() throws InterruptedException {
        // Tightly-clustered priors → tiny stddev → sigma trigger is much looser than the
        // legacy 0.5% absolute threshold, so a 0.3% deviation now counts as a real signal.
        for (int i = 0; i < 10; i++) {
            Good.addTradeData(i % 2 == 0 ? base * 0.9995f : base * 1.0005f, 1, 0);
        }
        primePrice(base * 0.997f); // ~0.3% below VWAP — would NOT fire under the old threshold

        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 0.997f, seller, good, 50));
        sellerOwned.setNumAvailable(450);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned())
                .as("0.3% dev in a quiet market is multiple sigma — sigma-based trigger should fire")
                .isNotEmpty();
        assertThat(buyer.getVwapMRPosition()).isEqualTo(1);
    }

    // ── sigma-based trigger in a noisy market suppresses the same deviation ─

    @Test
    void noisyMarketSigmaTriggerSuppressesSmallDeviation() throws InterruptedException {
        // Wildly-scattered priors → big stddev → sigma trigger is much tighter than the legacy
        // 0.5% absolute, so a 0.6% deviation is now just noise.
        for (int i = 0; i < 10; i++) {
            Good.addTradeData(i % 2 == 0 ? base * 0.95f : base * 1.05f, 1, 0);
        }
        primePrice(base * 0.994f); // ~0.6% below VWAP — old threshold fires, sigma trigger doesn't

        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 0.994f, seller, good, 50));
        sellerOwned.setNumAvailable(450);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        int tradesBefore = Good.getNumTrades();

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(Good.getNumTrades())
                .as("0.6% dev in a noisy market is < 1 sigma — sigma trigger should NOT fire")
                .isEqualTo(tradesBefore);
        assertThat(buyer.getGoodsOwned()).isEmpty();
    }

    // ── sweep cap at VWAP — won't pay above fair value ──────────────────────

    @Test
    void sweepCapStopsAtVwap() throws InterruptedException {
        // Pre-load enough trades for a non-zero stddev. All clustered around base*1.02 so VWAP
        // sits at base*1.02 with very small sigma → strong, easy-to-trigger signal.
        for (int i = 0; i < 10; i++) {
            Good.addTradeData(base * 1.02f + (i % 2 == 0 ? -0.001f : 0.001f), 1, 0);
        }
        primePrice(base);

        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        // Two asks below VWAP (~base*1.02), two above. Without the VWAP cap the agent would
        // happily sweep the whole stack at 3% below VWAP and 1% above.
        Offer a1 = new Offer(base * 1.00f, seller, good, 5); // below VWAP
        Offer a2 = new Offer(base * 1.01f, seller, good, 5); // below VWAP
        Offer a3 = new Offer(base * 1.03f, seller, good, 5); // ABOVE VWAP — should be skipped
        Offer a4 = new Offer(base * 1.04f, seller, good, 5); // above VWAP
        good.addAsk(a1); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a2); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a3); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a4); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10_000f);

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned())
                .as("sweep should stop at VWAP — the two above-VWAP asks must NOT be filled")
                .isEqualTo(10);
    }

    // ── doesn't share state with the trend-following VWAP ────────────────────

    @Test
    void doesNotShareStateWithTrendVwap() throws InterruptedException {
        // Set up the buy condition for MR (price below VWAP)
        primeVwap(base * 1.02f, 1000);
        primePrice(base);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        // Pretend the trend-following VWAP and Momentum have both been firing,
        // putting their per-strategy counters at their caps. MR's counter is
        // independent so it must still be free to trade.
        buyer.setVwapPosition(5);
        buyer.setMomentumPosition(5);

        new VWAPMeanReversion(buyer, tc, 1).run();

        assertThat(buyer.getVwapMRPosition())
                .as("MR position must increment even when VWAP and Momentum are capped")
                .isEqualTo(1);
        assertThat(buyer.getVwapPosition()).isEqualTo(5);  // untouched
        assertThat(buyer.getMomentumPosition()).isEqualTo(5);  // untouched
    }
}
