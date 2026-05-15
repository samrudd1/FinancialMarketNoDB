package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;
import trade.TradingCycle;

import static org.assertj.core.api.Assertions.assertThat;

class AgentFundAccountingTest extends GlobalStateFixture {

    /** Minimal subclass that exposes the package-private AbstractStrategy methods for testing. */
    private static class StrategyUnderTest extends AbstractStrategy implements Runnable {
        StrategyUnderTest(Agent agent, TradingCycle tc, int roundNum) {
            super(agent, tc, roundNum);
        }
        @Override public synchronized void run() {}

        void bid(float price, Good good, int qty) throws InterruptedException {
            createBid(price, good, qty);
        }
        void ask(float price, OwnedGood good, int qty) throws InterruptedException {
            createAsk(price, good, qty);
        }
        void clean(Agent agent, float price) throws InterruptedException {
            cleanOffers(agent, price);
        }
    }

    private Good good;
    private Agent agent;
    private StrategyUnderTest strategy;

    @BeforeEach
    void setUp() throws InterruptedException {
        Good.setDirectlyAvailable(100); // ensure IPO ask has positive quantity
        good = new Good(true);
        agent = new Agent();
        strategy = new StrategyUnderTest(agent, new TradingCycle(), 0);
    }

    // ── createBid ──────────────────────────────────────────────────────────────

    @Test
    void createBidReservesFundsFromAgent() throws InterruptedException {
        float initialFunds = agent.getFunds();
        float bidPrice = Good.getPrice() * 0.90f; // below lowestAsk (IPO at price)
        int qty = 10;

        strategy.bid(bidPrice, good, qty);

        assertThat(agent.getFunds())
                .as("createBid should deduct price*qty from agent funds")
                .isCloseTo(initialFunds - bidPrice * qty, org.assertj.core.api.Assertions.within(0.5f));
        assertThat(Good.getBid()).hasSize(1);
        assertThat(agent.getPlacedBid()).isTrue();
    }

    @Test
    void createBidNotPlacedWhenPriceAboveLowestAsk() throws InterruptedException {
        float initialFunds = agent.getFunds();
        float priceAboveAsk = Good.getPrice() * 1.05f;

        strategy.bid(priceAboveAsk, good, 10);

        assertThat(agent.getFunds()).isEqualTo(initialFunds);
        assertThat(Good.getBid()).isEmpty();
    }

    @Test
    void createBidAddsOfferToAgentBidsList() throws InterruptedException {
        float bidPrice = Good.getPrice() * 0.88f;
        strategy.bid(bidPrice, good, 5);
        assertThat(agent.getBidsPlaced()).hasSize(1);
        assertThat(agent.getBidsPlaced().get(0).getPrice()).isEqualTo(bidPrice);
    }

    // ── createAsk ─────────────────────────────────────────────────────────────

    @Test
    void createAskLocksSharesFromOwnedGood() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, agent, good, 5)); // allow asks

        OwnedGood owned = new OwnedGood(agent, good, 100, 100, base, true);
        agent.getGoodsOwned().add(0, owned);

        strategy.ask(base * 1.05f, owned, 20);

        assertThat(owned.getNumAvailable()).isEqualTo(80);
        assertThat(agent.getPlacedAsk()).isTrue();
        assertThat(agent.getAsksPlaced()).hasSize(1);
    }

    @Test
    void createAskNotPlacedWhenPriceBelowHighestBid() throws InterruptedException {
        float base = Good.getPrice();
        float highBidPrice = base * 0.95f;
        good.addBid(new Offer(highBidPrice, agent, good, 5));

        OwnedGood owned = new OwnedGood(agent, good, 100, 100, base, true);
        agent.getGoodsOwned().add(0, owned);

        int asksBefore = Good.getAsk().size();
        strategy.ask(highBidPrice * 0.99f, owned, 10); // below highestBid

        assertThat(Good.getAsk()).hasSize(asksBefore); // no new ask
        assertThat(owned.getNumAvailable()).isEqualTo(100);
    }

    // ── cleanOffers ───────────────────────────────────────────────────────────

    @Test
    void cleanOffersRemovesStaleBidBelowThreshold() throws InterruptedException {
        float base = Good.getPrice();
        // Stale bid: price < base * 0.7
        float staleBidPrice = base * 0.65f;
        strategy.bid(staleBidPrice, good, 10);
        float fundsAfterBid = agent.getFunds();

        strategy.clean(agent, base);

        assertThat(Good.getBid()).isEmpty();
        assertThat(agent.getFunds()).isGreaterThan(fundsAfterBid); // funds returned
    }

    @Test
    void cleanOffersKeepsFreshBid() throws InterruptedException {
        float base = Good.getPrice();
        // Fresh bid: price >= base * 0.7 (just inside threshold)
        float freshBidPrice = base * 0.75f;
        strategy.bid(freshBidPrice, good, 10);

        strategy.clean(agent, base);

        assertThat(Good.getBid()).hasSize(1);
    }

    @Test
    void cleanOffersRemovesStaleAskAboveThreshold() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, agent, good, 5));

        OwnedGood owned = new OwnedGood(agent, good, 100, 100, base, true);
        agent.getGoodsOwned().add(0, owned);

        // Stale ask: price > base * 1.4
        float staleAskPrice = base * 1.5f;
        strategy.ask(staleAskPrice, owned, 10);
        int availAfterAsk = owned.getNumAvailable(); // 90 after locking 10

        strategy.clean(agent, base);

        assertThat(owned.getNumAvailable()).isEqualTo(availAfterAsk + 10); // restored
    }

    @Test
    void cleanOffersKeepsFreshAsk() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, agent, good, 5));

        OwnedGood owned = new OwnedGood(agent, good, 100, 100, base, true);
        agent.getGoodsOwned().add(0, owned);

        // Fresh ask: price <= base * 1.4
        strategy.ask(base * 1.35f, owned, 10);

        strategy.clean(agent, base);

        assertThat(owned.getNumAvailable()).isEqualTo(90); // still locked
    }
}
