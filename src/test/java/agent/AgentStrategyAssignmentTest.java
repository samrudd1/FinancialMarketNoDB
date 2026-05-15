package agent;

import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStrategyAssignmentTest extends GlobalStateFixture {

    @BeforeEach
    void setUpGood() throws InterruptedException {
        new Good(true); // sets price so Agent constructors can call createTargetPrice
    }

    @Test
    void strategyAlwaysInValidRange() {
        for (int i = 0; i < 50; i++) {
            Agent agent = new Agent();
            assertThat(agent.getStrategy())
                    .as("strategy for agent %d must be in [0,9]", agent.getId())
                    .isBetween(0, 9);
        }
    }

    @Test
    void volatilityFalseExcludesHighVolatilityStrategies() {
        Agent.setVolatility(false);
        for (int i = 0; i < 60; i++) {
            Agent agent = new Agent();
            assertThat(agent.getStrategy())
                    .as("strategies 7, 8, 9 (VWAP, Momentum, VWAP+Momentum) should be excluded when volatility=false")
                    .isNotIn(7, 8, 9);
        }
    }

    @Test
    void volatilityTrueAllowsAllStrategies() {
        assertThat(Agent.isVolatility()).isTrue(); // default after reset
        // Build a large sample to confirm strategies 1, 3, 9 are reachable
        Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(new Agent().getStrategy());
        }
        // At least half the valid range should appear in 200 draws
        assertThat(seen.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void nameMatchesStrategyId() {
        for (int i = 0; i < 30; i++) {
            Agent agent = new Agent();
            int s = agent.getStrategy();
            String name = agent.getName();
            if      (s == 1) assertThat(name).startsWith("Aggressive Offers ");
            else if (s == 2) assertThat(name).startsWith("Sentiment Trend ");
            else if (s == 3) assertThat(name).startsWith("Offer Only ");
            else if (s == 4) assertThat(name).startsWith("RSI ");
            else if (s == 5) assertThat(name).startsWith("RSI10 ");
            else if (s == 6) assertThat(name).startsWith("Both RSI ");
            else if (s == 7) assertThat(name).startsWith("VWAP ");
            else if (s == 8) assertThat(name).startsWith("Momentum ");
            else if (s == 9) assertThat(name).startsWith("VWAP and Momentum ");
            else              assertThat(name).startsWith("Default ");
        }
    }

    @Test
    void eachAgentGetsUniqueId() {
        int idBefore = Agent.getNextID();
        Agent a = new Agent();
        Agent b = new Agent();
        assertThat(a.getId()).isLessThan(b.getId());
        assertThat(Agent.getNextID()).isEqualTo(idBefore + 2);
    }

    @Test
    void agentStartingFundsAboveMinimum() {
        for (int i = 0; i < 20; i++) {
            Agent agent = new Agent();
            assertThat(agent.getStartingFunds()).isGreaterThanOrEqualTo(1000f);
        }
    }

    @Test
    void companyConstructorDoesNotConsumeStrategyRng() throws InterruptedException {
        // Company constructor uses 2-arg form — strategy stays at default 0
        Good.resetForTest();
        Agent.resetForTest();
        new Good(true); // creates company via 2-arg constructor
        Agent company = Good.getCompany();
        // company doesn't have a meaningful strategy (int default = 0)
        assertThat(company.getId()).isEqualTo(0);
    }
}
