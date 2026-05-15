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
 * Verifies OfferOnly strategy behaviour.
 *
 * OfferOnly places bids and asks on the order book but NEVER calls
 * Exchange.execute directly — it is purely a market-maker / liquidity strategy.
 *
 * For targetPrice stability: same technique as DefaultStrategyTest — set an extreme
 * value that survives the random 0.6-1.0 changeTargetPrice() multiplier.
 */
class OfferOnlyTest extends GlobalStateFixture {

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

    // ── ask placement ─────────────────────────────────────────────────────────

    @Test
    void placesAskWhenSharesAvailableAndTargetAboveHighestBid() throws InterruptedException {
        Agent agent = new Agent();
        OwnedGood owned = new OwnedGood(agent, good, 500, 500, base, true);
        agent.getGoodsOwned().add(0, owned);
        // targetPrice=base*1.05 → after changeTargetPrice range [base*0.63, base*1.05]
        // all > highestBid(0) and < price*1.2(base*1.2) ✓
        agent.setTargetPrice(base * 1.05f);

        new OfferOnly(agent, tc, 0).run();

        assertThat(Good.getAsk()).as("OfferOnly should place an ask when conditions are met").hasSize(1);
        assertThat(agent.getPlacedAsk()).isTrue();
    }

    // ── bid placement ─────────────────────────────────────────────────────────

    @Test
    void placesBidWhenFundsAvailableAndTargetBelowLowestAsk() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 1000f);
        // targetPrice=base*1.5 → after changeTargetPrice range [base*0.9, base*1.5]
        // all > price*0.8(base*0.8) ✓ and < lowestAsk=99999 ✓
        agent.setTargetPrice(base * 1.5f);
        // no shares → ask block skipped

        new OfferOnly(agent, tc, 0).run();

        assertThat(Good.getBid()).as("OfferOnly should place a bid when conditions are met").hasSize(1);
        assertThat(agent.getPlacedBid()).isTrue();
    }

    // ── never executes trades directly ────────────────────────────────────────

    @Test
    void doesNotExecuteTradesEvenWithMatchableOffers() throws InterruptedException {
        // Put a bid and ask on the book from other agents; OfferOnly should not hit them.
        Agent otherBuyer = new Agent();
        otherBuyer.setFunds(base * 1000f);
        Offer bid = new Offer(base * 0.98f, otherBuyer, good, 50);
        good.addBid(bid);
        otherBuyer.setFunds(otherBuyer.getFunds() - base * 0.98f * 50);
        otherBuyer.setPlacedBid(true);

        Agent otherSeller = new Agent();
        OwnedGood otherOwned = new OwnedGood(otherSeller, good, 200, 150, base, true);
        otherSeller.getGoodsOwned().add(0, otherOwned);
        Offer ask = new Offer(base * 1.01f, otherSeller, good, 50);
        good.addAsk(ask);
        otherSeller.setPlacedAsk(true);

        Agent offerOnlyAgent = new Agent();
        offerOnlyAgent.setFunds(base * 5000f);
        OwnedGood agentOwned = new OwnedGood(offerOnlyAgent, good, 500, 500, base, true);
        offerOnlyAgent.getGoodsOwned().add(0, agentOwned);
        offerOnlyAgent.setTargetPrice(base);

        int tradesBefore = Good.getNumTrades();
        new OfferOnly(offerOnlyAgent, tc, 0).run();

        assertThat(Good.getNumTrades()).as("OfferOnly must never call Exchange.execute directly")
                .isEqualTo(tradesBefore);
    }
}
