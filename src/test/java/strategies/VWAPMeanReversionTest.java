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
