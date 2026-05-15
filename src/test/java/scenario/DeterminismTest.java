package scenario;

import agent.Agent;
import good.Good;
import org.junit.jupiter.api.Test;
import session.Session;
import support.GlobalStateFixture;
import trade.Exchange;
import trade.TradingCycle;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that two runs with identical seeds produce identical results.
 *
 * With testThreadPoolSize=1 the thread pool is single-threaded, so strategy
 * tasks execute sequentially in a deterministic order. Combined with the seeded
 * RandomProvider from GlobalStateFixture, the entire RNG call sequence is
 * reproducible, and both runs must produce bit-for-bit identical price lists
 * and trade counts.
 *
 * If this test fails it means a hidden randomness source (e.g. a stray
 * ThreadLocalRandom, HashMap iteration order instability, or an undetected
 * concurrent race) was missed during the Phase 3 seam work.
 */
class DeterminismTest extends GlobalStateFixture {

    private static final int NUM_AGENTS = 3;
    private static final int NUM_SHARES = 2000;
    private static final int NUM_ROUNDS = 15;

    @Test
    void identicalSeedsProduceIdenticalPriceList() throws Exception {
        // ── first run ─────────────────────────────────────────────────────────
        setUpAndRun();

        List<Float> firstPrices   = new ArrayList<>(Good.getPriceList());
        int         firstTrades   = Good.getNumTrades();
        float       firstLastPrice = Exchange.lastPrice;

        // ── reset everything, re-seed with the same 42L ────────────────────
        resetGlobalState(); // inherited from GlobalStateFixture; re-seeds RandomProvider(42L)

        // ── second run, byte-for-byte identical setup ─────────────────────
        setUpAndRun();

        List<Float> secondPrices   = new ArrayList<>(Good.getPriceList());
        int         secondTrades   = Good.getNumTrades();
        float       secondLastPrice = Exchange.lastPrice;

        // ── assertions ───────────────────────────────────────────────────────
        assertThat(secondPrices)
                .as("same seed must yield the same price list on both runs")
                .isEqualTo(firstPrices);

        assertThat(secondTrades)
                .as("same seed must yield the same total trade count on both runs")
                .isEqualTo(firstTrades);

        assertThat(secondLastPrice)
                .as("same seed must yield the same final price on both runs")
                .isEqualTo(firstLastPrice);
    }

    @Test
    void identicalSeedsProduceIdenticalStrategyCounts() throws Exception {
        // ── first run ─────────────────────────────────────────────────────────
        setUpAndRun();

        int firstDefault    = (int) Exchange.getDefaultCount();
        int firstSentiment  = (int) Exchange.getSentCount();
        int firstRsi        = (int) Exchange.getRsiCount();
        int firstAggressive = (int) Exchange.getAggressiveCount();

        // ── reset and second run ──────────────────────────────────────────────
        resetGlobalState();
        setUpAndRun();

        assertThat((int) Exchange.getDefaultCount())
                .as("DefaultStrategy trade count must be identical across seeded runs")
                .isEqualTo(firstDefault);

        assertThat((int) Exchange.getSentCount())
                .as("SentimentTrend trade count must be identical across seeded runs")
                .isEqualTo(firstSentiment);

        assertThat((int) Exchange.getRsiCount())
                .as("RSI trade count must be identical across seeded runs")
                .isEqualTo(firstRsi);

        assertThat((int) Exchange.getAggressiveCount())
                .as("AggressiveOffers trade count must be identical across seeded runs")
                .isEqualTo(firstAggressive);
    }

    private void setUpAndRun() throws Exception {
        TradingCycle.setTestMode(true);
        TradingCycle.testThreadPoolSize = 1;
        Exchange.setLiveActive(false);
        Exchange.setLiveForced(false);

        Session.setNumAgents(NUM_AGENTS);
        Good.setOutstandingShares(NUM_SHARES);
        Good.setDirectlyAvailable(NUM_SHARES);
        Agent.setSentiment(20);
        Agent.nextID = 1;

        new Good(true);
        for (int i = 0; i < NUM_AGENTS; i++) new Agent();

        new TradingCycle().startTrading(NUM_ROUNDS);
    }
}
