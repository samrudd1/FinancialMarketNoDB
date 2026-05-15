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
 * Verifies the trend-following VWAP decision logic.
 *
 * <p>Buys when {@code price ≥ rollingVwap * (1 + 0.005)} and the agent's
 * {@code vwapPosition} counter is still inside the ±5 cap. Sells on the
 * mirror condition below VWAP.
 *
 * <p>Setup trick: synthesize trades via {@link Good#setPrice} to push the
 * rolling VWAP, then change the current price separately to create the
 * deviation we want to test. {@code Exchange.lastPrice} and {@code priceCheck}
 * are aligned with the current price so the Exchange's price-band guard
 * doesn't reject the test trade.
 */
class VWAPTest extends GlobalStateFixture {

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

    /** Push a synthetic trade record into {@link Good#getTradeData()} so the rolling VWAP has weight at {@code priceLevel}. */
    private void primeVwap(float priceLevel, int volume) {
        Good.addTradeData(priceLevel, volume, 0);
    }

    /** Move the current price (and price-band reference) to {@code newPrice}. */
    private void primePrice(float newPrice) {
        Agent dummy = new Agent();
        good.setPrice(new Offer(newPrice, dummy, good, 1), 1);
        Exchange.lastPrice = newPrice;
        Exchange.getInstance().setPriceCheck(newPrice);
    }

    // ── buys when price is meaningfully above VWAP ────────────────────────────

    @Test
    void buysWhenPriceMeaningfullyAboveVwap() throws InterruptedException {
        // Rolling VWAP anchored at base, current price 1% above
        primeVwap(base, 1000);
        primePrice(base * 1.01f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 1.01f, seller, good, 200));
        sellerOwned.setNumAvailable(300);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new VWAP(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares after VWAP buy signal").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
        assertThat(buyer.getVwapPosition()).isEqualTo(1);
    }

    // ── no buy when deviation below threshold ────────────────────────────────

    @Test
    void noBuyWhenDeviationBelowThreshold() throws InterruptedException {
        // Only 0.1% above VWAP — under the 0.5% threshold
        primeVwap(base, 1000);
        primePrice(base * 1.001f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        int tradesBefore = Good.getNumTrades();
        new VWAP(buyer, tc, 1).run();

        assertThat(Good.getNumTrades())
                .as("no trade when |dev| < 0.005 envelope")
                .isEqualTo(tradesBefore);
        assertThat(buyer.getVwapPosition()).isZero();
    }

    // ── sells when price is meaningfully below VWAP ──────────────────────────

    @Test
    void sellsWhenPriceMeaningfullyBelowVwap() throws InterruptedException {
        // Rolling VWAP anchored above current price
        primeVwap(base * 1.02f, 1000);
        primePrice(base);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        Agent bidder = new Agent();
        bidder.setFunds(base * 10000f);
        float bidPrice = base * 0.999f;
        Offer bid = new Offer(bidPrice, bidder, good, 100);
        good.addBid(bid);
        bidder.setFunds(bidder.getFunds() - bidPrice * 100);

        int ownedBefore = sellerOwned.getNumOwned();
        new VWAP(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned())
                .as("seller's shares must decrease after VWAP sell signal")
                .isLessThan(ownedBefore);
        assertThat(seller.getVwapPosition()).isEqualTo(-1);
    }

    // ── position cap blocks further buys ─────────────────────────────────────

    @Test
    void positionCapBlocksBuyAtCap() throws InterruptedException {
        primeVwap(base, 1000);
        primePrice(base * 1.01f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 1.01f, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        buyer.setVwapPosition(5);  // at cap

        int tradesBefore = Good.getNumTrades();
        new VWAP(buyer, tc, 1).run();

        assertThat(Good.getNumTrades())
                .as("vwapPosition at +5 cap must block further buys")
                .isEqualTo(tradesBefore);
    }
}
