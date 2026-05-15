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
 * Verifies DefaultStrategy decision logic: bid/ask placement and direct execution
 * against existing book orders.
 *
 * To avoid the random changeTargetPrice() call that fires at the start of run(),
 * we set targetPrice to extremes (very high for buy/execution tests, zero for
 * sell/execution tests) that remain valid even after the random 0.6-1.0 multiplier.
 */
class DefaultStrategyTest extends GlobalStateFixture {

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

    // ── bid placement ─────────────────────────────────────────────────────────

    @Test
    void bidPlacedWhenAgentHasFundsAndNoBidOnBook() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 1000f);
        // targetPrice=base*2 → after changeTargetPrice worst case base*2*0.6=base*1.2 > price*0.9 ✓
        agent.setTargetPrice(base * 2f);
        // agent has no shares → ask block skipped; book empty → lowestAsk=99999 > targetPrice trivially
        new DefaultStrategy(agent, tc, 0).run();

        assertThat(Good.getBid()).as("bid should be placed when funds available and target in range").hasSize(1);
        assertThat(agent.getPlacedBid()).isTrue();
    }

    // ── ask placement ─────────────────────────────────────────────────────────

    @Test
    void askPlacedWhenAgentHasSharesAndTargetAboveHighestBid() throws InterruptedException {
        Agent agent = new Agent();
        OwnedGood owned = new OwnedGood(agent, good, 500, 500, base, true);
        agent.getGoodsOwned().add(0, owned);
        // targetPrice=base*1.05 → after changeTargetPrice range [base*0.63, base*1.05],
        // all > highestBid(0) and < price*1.1(base*1.1) ✓
        agent.setTargetPrice(base * 1.05f);

        new DefaultStrategy(agent, tc, 0).run();

        assertThat(Good.getAsk()).as("ask should be placed on empty book").hasSize(1);
        assertThat(agent.getPlacedAsk()).isTrue();
    }

    // ── placedBid / placedAsk flags prevent double offers ─────────────────────

    @Test
    void flagsPreventsNewOffersWhenAlreadyPlaced() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 1000f);
        agent.setTargetPrice(base * 0.95f);
        agent.setPlacedBid(true);
        agent.setPlacedAsk(true);

        new DefaultStrategy(agent, tc, 0).run();

        assertThat(Good.getBid()).as("no bid when placedBid=true").isEmpty();
        assertThat(Good.getAsk()).as("no ask when placedAsk=true").isEmpty();
    }

    // ── execution: buyer hits lowest ask ──────────────────────────────────────

    @Test
    void hitsLowestAskFromDifferentSeller() throws InterruptedException {
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 200, 100, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 100);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(100);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10000f);
        // targetPrice >> lowestAsk (base) even after changeTargetPrice → hit-ask branch fires
        buyer.setTargetPrice(base * 20f);

        new DefaultStrategy(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).as("buyer should own shares after hitting ask").isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }

    // ── execution: sweeps multiple ask levels up to target price ────────────

    @Test
    void buyerSweepsMultipleAskLevelsUpToTargetPrice() throws InterruptedException {
        // Seed bid so subsequent addAsk calls aren't rejected by the one-sided-book guard.
        Agent stubBidder = new Agent();
        stubBidder.setFunds(base * 100f);
        good.addBid(new Offer(base * 0.5f, stubBidder, good, 1));

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer a1 = new Offer(base,         seller, good, 5);
        Offer a2 = new Offer(base * 1.02f, seller, good, 5);
        Offer a3 = new Offer(base * 1.04f, seller, good, 5);
        good.addAsk(a1); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a2); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        good.addAsk(a3); sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10000f);
        // Generous target → cap is base*20, sweep should drain all three levels
        // (each level still inside Exchange's ±5%/-4% band so execute won't reject).
        buyer.setTargetPrice(base * 20f);

        new DefaultStrategy(buyer, tc, 1).run();

        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned())
                .as("default-strategy sweep should drain past the top-of-book ask")
                .isGreaterThan(5);
    }

    // ── execution: seller hits highest bid ────────────────────────────────────

    @Test
    void hitsHighestBidFromDifferentBuyer() throws InterruptedException {
        Agent buyer = new Agent();
        buyer.setFunds(base * 10000f);
        float bidPrice = base * 0.99f;
        Offer bid = new Offer(bidPrice, buyer, good, 100);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - bidPrice * 100);
        buyer.setPlacedBid(true);

        Agent seller = new Agent();
        seller.setFunds(0f); // prevents seller from placing a bid
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        // targetPrice=0 → stays 0 after changeTargetPrice (0*multiplier=0); 0 < highestBid ✓
        seller.setTargetPrice(0f);

        new DefaultStrategy(seller, tc, 1).run();

        assertThat(sellerOwned.getNumOwned()).as("seller shares should decrease after hitting bid").isLessThan(500);
    }
}
