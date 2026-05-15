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
 * Verifies that the per-round VWAP capture used by the dashboard is appended
 * exactly once per round, lines up with sentimentList in length (so the two
 * series can share the dashboard's X axis without padding), and contains only
 * finite, non-negative values that can be plotted directly.
 */
class VwapSeriesTest extends GlobalStateFixture {

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
    void vwapListHasOneEntryPerRound() {
        assertThat(Exchange.getVwapList())
                .as("vwapList must contain exactly one tick per round")
                .hasSize(NUM_ROUNDS);
    }

    @Test
    void vwapAndSentimentSeriesAreSameLength() {
        // The dashboard plots them on the same X axis. If they ever drift apart,
        // the rendering will misalign — fail loud here instead.
        assertThat(Exchange.getVwapList()).hasSameSizeAs(Exchange.getSentimentList());
    }

    @Test
    void vwapValuesAreFiniteAndNonNegative() {
        assertThat(Exchange.getVwapList())
                .as("every VWAP tick must be plottable: finite, non-negative")
                .allMatch(v -> !Float.isNaN(v) && !Float.isInfinite(v) && v >= 0f);
    }

    @Test
    void vwapListIsClearedByReset() {
        assertThat(Exchange.getVwapList()).isNotEmpty();
        Exchange.resetForTest();
        assertThat(Exchange.getVwapList())
                .as("Exchange.resetForTest must clear vwapList")
                .isEmpty();
    }
}
