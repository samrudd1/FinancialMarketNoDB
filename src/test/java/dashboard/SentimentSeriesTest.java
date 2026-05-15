package dashboard;

import agent.Agent;
import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import session.Session;
import support.GlobalStateFixture;
import trade.Exchange;
import trade.TradingCycle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code TradingCycle.mutate} appends exactly one sentiment value
 * to {@code Exchange.sentimentList} per round, and that all values are drawn
 * from the legitimate sentiment band the dashboard heatmap will render. The
 * dashboard's sentiment strip relies on this 1:1 round-to-tick relationship.
 */
class SentimentSeriesTest extends GlobalStateFixture {

    private static final int NUM_AGENTS = 5;
    private static final int NUM_SHARES = 5000;
    private static final int NUM_ROUNDS = 50;

    @BeforeEach
    void runScenario() throws Exception {
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

    @Test
    void sentimentListHasOneEntryPerRound() {
        assertThat(Exchange.getSentimentList())
                .as("sentimentList must contain exactly one tick per round (mutate is called once per createTrades)")
                .hasSize(NUM_ROUNDS);
    }

    @Test
    void sentimentValuesAreInPlausibleBand() {
        // mutate() only assigns sentiment values in the set {15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26}.
        // Any value outside the documented band would mean a code path is writing somewhere unexpected.
        assertThat(Exchange.getSentimentList())
                .as("every captured sentiment must be in the band mutate() can set")
                .allMatch(s -> s >= 15 && s <= 26);
    }

    @Test
    void sentimentListIsClearedByReset() {
        // resetGlobalState (from @BeforeEach in the next test invocation) must clear it.
        // This test runs a scenario, then asserts a manual Exchange.resetForTest wipes the list.
        assertThat(Exchange.getSentimentList()).isNotEmpty();
        Exchange.resetForTest();
        assertThat(Exchange.getSentimentList())
                .as("Exchange.resetForTest must clear sentimentList — required by StaticStateInvariantTest contract")
                .isEmpty();
    }
}
