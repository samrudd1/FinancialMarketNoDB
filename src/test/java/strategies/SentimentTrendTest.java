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
 * Verifies SentimentTrend decision logic.
 *
 * Buys from lowest ask when Agent.sentiment > 21 (bullish).
 * Sells to highest bid when Agent.sentiment < 19 (bearish).
 * Does nothing when sentiment is in the neutral band [19, 21].
 */
class SentimentTrendTest extends GlobalStateFixture {

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

    // ── buys when sentiment is bullish ────────────────────────────────────────

    @Test
    void buysWhenSentimentIsHighPositive() throws InterruptedException {
        Agent.setSentiment(25); // > 21 → buy branch

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 200); // at base, within 2% of priceCheck ✓
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        new SentimentTrend(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares after bullish sentiment trade").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }

    // ── sells when sentiment is bearish ───────────────────────────────────────

    @Test
    void sellsWhenSentimentIsNegative() throws InterruptedException {
        Agent.setSentiment(15); // < 19 → sell branch

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        float bidPrice = base * 0.99f; // close enough: bidPrice > priceCheck*0.98 ✓
        Offer bid = new Offer(bidPrice, buyer, good, 200);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - bidPrice * 200);
        buyer.setPlacedBid(true);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        new SentimentTrend(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned()).as("seller shares should decrease after bearish sentiment trade")
                .isLessThan(500);
    }

    // ── no action in neutral band ─────────────────────────────────────────────

    @Test
    void noActionWhenSentimentIsNeutral() throws InterruptedException {
        Agent.setSentiment(20); // in [19, 21] → neither branch

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 400, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 200);
        good.addAsk(ask);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        int tradesBefore = Good.getNumTrades();
        new SentimentTrend(buyer, tc, 1).run();

        assertThat(Good.getNumTrades()).as("no trade in neutral sentiment band").isEqualTo(tradesBefore);
        assertThat(buyer.getGoodsOwned()).as("buyer should not receive shares with neutral sentiment").isEmpty();
    }
}
