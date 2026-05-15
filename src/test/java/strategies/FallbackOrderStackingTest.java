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
 * Regression test for the "stacked fallback orders" bug.
 *
 * <p>Symptom in the dashboard: an agent's portfolio collapses over many
 * rounds while the visible trade list shows only a handful of small fills.
 * Root cause: each tick where the buy/sell signal fired but no qualifying
 * counter-offer was takeable, the strategy fell back to {@code createBid}
 * / {@code createAsk} unconditionally — locking another slice of cash (or
 * shares) in a NEW resting order. Across many ticks the agent stacked
 * dozens of standing bids at progressively-trended-against prices, all of
 * which then filled near the trend peak and baked in mark-to-market losses.
 *
 * <p>Fix: only post a fallback order when the agent has no live order of
 * the same side already standing.
 *
 * <p>Both sides of the book need orders for the test to meaningfully
 * reproduce — the {@code addBid}/{@code addAsk} one-sided book guards
 * otherwise mask the issue by silently rejecting the stacked orders.
 * Production has many agents quoting both sides, so the guards rarely
 * fire there.
 */
class FallbackOrderStackingTest extends GlobalStateFixture {

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

    private void primeRoundHistory(int count, float closePrice) {
        for (int i = 0; i < count; i++) Exchange.getRoundFinalPrice().add(closePrice);
    }

    private void primePrice(float newPrice) {
        Agent dummy = new Agent();
        good.setPrice(new Offer(newPrice, dummy, good, 1), 1);
        Exchange.lastPrice = newPrice;
        Exchange.getInstance().setPriceCheck(newPrice);
    }

    /** Place a single ask above the strategy's takeable ceiling so the taker fails and the fallback path fires. */
    private void placeUntouchableAsk(float askPrice) throws InterruptedException {
        Agent maker = new Agent();
        OwnedGood owned = new OwnedGood(maker, good, 100000, 100000, base, true);
        maker.getGoodsOwned().add(0, owned);
        good.addAsk(new Offer(askPrice, maker, good, 1));
        owned.setNumAvailable(99999);
    }

    /** Place a single bid below the strategy's takeable floor so the taker fails and the fallback path fires. */
    private void placeUntouchableBid(float bidPrice) throws InterruptedException {
        Agent maker = new Agent();
        maker.setFunds(base * 100000f);
        good.addBid(new Offer(bidPrice, maker, good, 1));
        maker.setFunds(maker.getFunds() - bidPrice);
    }

    private int liveBidCount(Agent a) {
        int n = 0;
        for (Offer o : a.getBidsPlaced()) {
            if (Good.getBid().contains(o) && o.getNumOffered() > 0) n++;
        }
        return n;
    }

    private int liveAskCount(Agent a) {
        int n = 0;
        for (Offer o : a.getAsksPlaced()) {
            if (Good.getAsk().contains(o) && o.getNumOffered() > 0) n++;
        }
        return n;
    }

    @Test
    void vwapDoesNotStackBidsAcrossTicks() throws InterruptedException {
        primeVwap(base, 1000);
        primePrice(base * 1.02f);
        placeUntouchableAsk(base * 1.04f);  // above price * 1.01 takeable ceiling

        Agent vwap = new Agent();
        float startingFunds = base * 100000f;
        vwap.setFunds(startingFunds);

        for (int round = 1; round <= 10; round++) {
            new VWAP(vwap, tc, round).run();
        }

        assertThat(liveBidCount(vwap))
                .as("VWAP must place at most one fallback bid at a time, not stack across ticks")
                .isLessThanOrEqualTo(1);
        assertThat(vwap.getFunds())
                .as("most cash should still be liquid — at most one bid's worth locked")
                .isGreaterThan(startingFunds * 0.5f);
    }

    @Test
    void momentumDoesNotStackBidsAcrossTicks() throws InterruptedException {
        primeRoundHistory(6, base);
        primePrice(base * 1.05f);
        placeUntouchableAsk(base * 1.07f);  // above price * 1.005 takeable ceiling

        Agent mom = new Agent();
        float startingFunds = base * 100000f;
        mom.setFunds(startingFunds);

        for (int round = 7; round <= 16; round++) {
            new Momentum(mom, tc, round).run();
        }

        assertThat(liveBidCount(mom))
                .as("Momentum must place at most one fallback bid at a time, not stack across ticks")
                .isLessThanOrEqualTo(1);
        assertThat(mom.getFunds())
                .as("most cash should still be liquid — at most one bid's worth locked")
                .isGreaterThan(startingFunds * 0.5f);
    }

    @Test
    void vwapMRDoesNotStackBidsAcrossTicks() throws InterruptedException {
        primeVwap(base * 1.02f, 1000);
        primePrice(base);
        placeUntouchableAsk(base * 1.02f);  // above price * 1.01 takeable ceiling

        Agent mr = new Agent();
        float startingFunds = base * 100000f;
        mr.setFunds(startingFunds);

        for (int round = 1; round <= 10; round++) {
            new VWAPMeanReversion(mr, tc, round).run();
        }

        assertThat(liveBidCount(mr))
                .as("VWAPMeanReversion must place at most one fallback bid at a time")
                .isLessThanOrEqualTo(1);
        assertThat(mr.getFunds())
                .as("most cash should still be liquid — at most one bid's worth locked")
                .isGreaterThan(startingFunds * 0.5f);
    }

    @Test
    void momentumDoesNotStackAsksAcrossTicks() throws InterruptedException {
        primeRoundHistory(6, base * 1.05f);
        primePrice(base);
        placeUntouchableBid(base * 0.98f);  // below price * 0.995 takeable floor

        Agent mom = new Agent();
        OwnedGood owned = new OwnedGood(mom, good, 100000, 100000, base, true);
        mom.getGoodsOwned().add(0, owned);

        for (int round = 7; round <= 16; round++) {
            new Momentum(mom, tc, round).run();
        }

        assertThat(liveAskCount(mom))
                .as("Momentum must place at most one fallback ask at a time, not stack across ticks")
                .isLessThanOrEqualTo(1);
    }

    @Test
    void vwapDoesNotStackAsksAcrossTicks() throws InterruptedException {
        primeVwap(base * 1.05f, 1000);
        primePrice(base);
        placeUntouchableBid(base * 0.97f);  // below price * 0.99 takeable floor

        Agent vwap = new Agent();
        OwnedGood owned = new OwnedGood(vwap, good, 100000, 100000, base, true);
        vwap.getGoodsOwned().add(0, owned);

        for (int round = 1; round <= 10; round++) {
            new VWAP(vwap, tc, round).run();
        }

        assertThat(liveAskCount(vwap))
                .as("VWAP must place at most one fallback ask at a time, not stack across ticks")
                .isLessThanOrEqualTo(1);
    }

    @Test
    void vwapMRDoesNotStackAsksAcrossTicks() throws InterruptedException {
        primeVwap(base * 0.95f, 1000);
        primePrice(base);
        placeUntouchableBid(base * 0.97f);

        Agent mr = new Agent();
        OwnedGood owned = new OwnedGood(mr, good, 100000, 100000, base, true);
        mr.getGoodsOwned().add(0, owned);

        for (int round = 1; round <= 10; round++) {
            new VWAPMeanReversion(mr, tc, round).run();
        }

        assertThat(liveAskCount(mr))
                .as("VWAPMeanReversion must place at most one fallback ask at a time")
                .isLessThanOrEqualTo(1);
    }
}
