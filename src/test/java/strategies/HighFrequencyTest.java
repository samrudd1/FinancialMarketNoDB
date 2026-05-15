package strategies;

import agent.Agent;
import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;
import trade.TradingCycle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic tests for the HighFrequency strategy.
 *
 * Note: HighFrequency is NOT wired into TradingCycle.createTrades and is therefore
 * never activated by the normal simulation flow. It exists as an experimental
 * strategy. Its run() contains a while loop guarded by
 * {@code tc.getNumOfRounds() < (finalRound - 1)}, so setting finalRound <= 1
 * causes the loop to exit immediately.
 */
class HighFrequencyTest extends GlobalStateFixture {

    private TradingCycle tc;

    @BeforeEach
    void setUp() throws InterruptedException {
        new Good(true);
        Good.getAsk().clear();
        tc = new TradingCycle();
    }

    // ── loop exits immediately when final round already reached ───────────────

    @Test
    void runReturnsImmediatelyWhenFinalRoundAlreadyPassed() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(Good.getPrice() * 1000f);
        // tc.numOfRounds=0, finalRound=1 → condition: 0 < (1-1)=0 → false → loop never runs
        HighFrequency hf = new HighFrequency(agent, tc, 0, 1);
        hf.run();

        // If we reach here the loop did not hang; addValue() was called after the loop
        assertThat(agent.getFundData()).as("addValue should be called once after the loop exits").hasSize(2);
    }

    // ── same result with finalRound=0 (condition: 0 < -1 → false) ────────────

    @Test
    void runReturnsImmediatelyWhenFinalRoundIsZero() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(Good.getPrice() * 1000f);
        HighFrequency hf = new HighFrequency(agent, tc, 0, 0);
        hf.run();

        assertThat(agent.getFundData()).hasSize(2);
    }
}
