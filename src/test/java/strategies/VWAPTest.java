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
 * Verifies VWAP decision logic.
 *
 * Buys from lowest ask when price > VWAP and !prevPriceUp.
 * Sells to highest bid when price < VWAP and prevPriceUp.
 *
 * VWAP is set directly via Good.setPrice() to avoid going through
 * the full Exchange.execute flow just to prime the VWAP field.
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

    // ── buys when price > VWAP ────────────────────────────────────────────────

    @Test
    void buysWhenPriceAboveVwap() throws InterruptedException {
        // After reset, vwap=0 and price=base → price(base) > vwap(0) ✓
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 200); // within 1% of current price ✓
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        buyer.setPrevPriceUp(false); // buy branch requires !prevPriceUp ✓

        new VWAP(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares after VWAP buy signal").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }

    // ── sells when price < VWAP ───────────────────────────────────────────────

    @Test
    void sellsWhenPriceBelowVwap() throws InterruptedException {
        // Prime vwap > price by calling setPrice directly:
        // trade at 2x base → price=2*base, vwap=2*base
        // trade at base → price=base, vwap=(2*base*1 + base*1)/(2) = 1.5*base
        Agent dummy = new Agent();
        good.setPrice(new Offer(base * 2f, dummy, good, 1), 1);
        good.setPrice(new Offer(base, dummy, good, 1), 1);
        // Good.getPrice()=base, Good.getVwap()=1.5*base → price < vwap ✓

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        float bidPrice = base; // exactly at base; bidPrice > price*0.99=base*0.99 ✓
        Offer bid = new Offer(bidPrice, buyer, good, 100);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - bidPrice * 100);
        buyer.setPlacedBid(true);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        seller.setPrevPriceUp(true); // sell branch requires prevPriceUp ✓

        new VWAP(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned()).as("seller shares should decrease after VWAP sell signal")
                .isLessThan(500);
    }

    // ── no action when neither condition met ──────────────────────────────────

    @Test
    void noActionWhenPriceAboveVwapButPrevPriceUp() throws InterruptedException {
        // vwap=0, price=base → price > vwap (buy branch condition), but prevPriceUp=true blocks it
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base, seller, good, 100));

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        buyer.setPrevPriceUp(true); // blocks buy even though price > vwap

        int tradesBefore = Good.getNumTrades();
        new VWAP(buyer, tc, 1).run();

        assertThat(Good.getNumTrades()).as("no trade when prevPriceUp blocks the buy branch")
                .isEqualTo(tradesBefore);
    }
}
