package trade;

import agent.Agent;
import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that TradingCycle.createTrades dispatches the correct strategy at the
 * documented round-number thresholds. Uses agent.fundData.size() as the observable
 * proxy: every strategy's run() ends with agent.addValue() which appends to fundData,
 * so fundData.size() == 2 means the strategy ran, 1 means it was skipped.
 *
 * Setup note: after resetForTest() nextID=0. new Good(true) creates the company
 * (id=0). A dummy Agent (id=1) is then excluded by createTrades' id != 1 guard.
 * The test agent (id=2) is the one that createTrades processes.
 */
class StrategyDispatchTest extends GlobalStateFixture {

    private float base;
    private TradingCycle tc;
    private Agent testAgent;

    @BeforeEach
    void setUp() throws InterruptedException {
        new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
        tc = new TradingCycle();
        new Agent(); // consumes id=1 (excluded by createTrades' id != 1 check)
        testAgent = new Agent(); // id=2 → processed by createTrades
    }

    // ── Momentum (strategy 8): gate roundNum > 100 ────────────────────────────

    @Test
    void momentumNotDispatchedBelowRound100() throws InterruptedException {
        testAgent.setStrategy(8);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(50); // roundNum=50 ≤ 100 → Momentum skipped

        assertThat(testAgent.getFundData().size())
                .as("Momentum must not run when roundNum <= 100")
                .isEqualTo(fundDataBefore);
    }

    @Test
    void momentumDispatchedAboveRound100() throws InterruptedException {
        testAgent.setStrategy(8);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(101); // roundNum=101 > 100 → Momentum dispatched → addValue called

        assertThat(testAgent.getFundData().size())
                .as("Momentum must run when roundNum > 100")
                .isEqualTo(fundDataBefore + 1);
    }

    // ── SentimentTrend (strategy 2): gate roundNum > 10 ──────────────────────

    @Test
    void sentimentTrendNotDispatchedBelowRound10() throws InterruptedException {
        testAgent.setStrategy(2);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(5); // roundNum=5 ≤ 10 → SentimentTrend skipped

        assertThat(testAgent.getFundData().size())
                .as("SentimentTrend must not run when roundNum <= 10")
                .isEqualTo(fundDataBefore);
    }

    @Test
    void sentimentTrendDispatchedAboveRound10() throws InterruptedException {
        testAgent.setStrategy(2);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(11); // roundNum=11 > 10 → SentimentTrend dispatched

        assertThat(testAgent.getFundData().size())
                .as("SentimentTrend must run when roundNum > 10")
                .isEqualTo(fundDataBefore + 1);
    }

    // ── RSI (strategy 4): gate roundNum > 16 ──────────────────────────────────

    @Test
    void rsiNotDispatchedBelowRound16() throws InterruptedException {
        testAgent.setStrategy(4);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(10); // roundNum=10 ≤ 16 → RSI skipped

        assertThat(testAgent.getFundData().size())
                .as("RSI must not run when roundNum <= 16")
                .isEqualTo(fundDataBefore);
    }

    @Test
    void rsiDispatchedAboveRound16() throws InterruptedException {
        testAgent.setStrategy(4);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(17); // roundNum=17 > 16 → RSI dispatched

        assertThat(testAgent.getFundData().size())
                .as("RSI must run when roundNum > 16")
                .isEqualTo(fundDataBefore + 1);
    }

    // ── RSI10 (strategy 5): gate roundNum > 160 ───────────────────────────────

    @Test
    void rsi10NotDispatchedBelowRound160() throws InterruptedException {
        testAgent.setStrategy(5);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(100); // roundNum=100 ≤ 160 → RSI10 skipped

        assertThat(testAgent.getFundData().size())
                .as("RSI10 must not run when roundNum <= 160")
                .isEqualTo(fundDataBefore);
    }

    @Test
    void rsi10DispatchedAboveRound160() throws InterruptedException {
        testAgent.setStrategy(5);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(161); // roundNum=161 > 160 → RSI10 dispatched

        assertThat(testAgent.getFundData().size())
                .as("RSI10 must run when roundNum > 160")
                .isEqualTo(fundDataBefore + 1);
    }

    // ── DefaultStrategy (strategy 0): always dispatched ─────────────────────

    @Test
    void defaultStrategyAlwaysDispatched() throws InterruptedException {
        testAgent.setStrategy(0);
        int fundDataBefore = testAgent.getFundData().size();

        tc.createTrades(0); // roundNum=0 → DefaultStrategy has no round gate

        assertThat(testAgent.getFundData().size())
                .as("DefaultStrategy must always run regardless of round number")
                .isEqualTo(fundDataBefore + 1);
    }
}
