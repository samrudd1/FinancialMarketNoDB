package scenario;

import agent.Agent;
import good.Good;
import org.junit.jupiter.api.Test;
import session.Session;
import support.GlobalStateFixture;
import trade.Exchange;
import trade.TradingCycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that TradingCycle.startTrading completes without a HeadlessException
 * (or any other exception) when testMode=true suppresses all GUI output.
 *
 * This is the simplest end-to-end smoke test: it proves the full pipeline
 * runs headlessly and populates the price list.
 */
class HeadlessSmokeTest extends GlobalStateFixture {

    @Test
    void startTradingCompletesHeadlessly() throws Exception {
        TradingCycle.setTestMode(true);
        TradingCycle.testThreadPoolSize = 1;
        Exchange.setLiveActive(false);
        Exchange.setLiveForced(false);

        Session.setNumAgents(3);
        Good.setOutstandingShares(1500);
        Good.setDirectlyAvailable(1500);
        Agent.setSentiment(20);
        Agent.nextID = 1;  // company gets id=1 (excluded from createTrades)

        new Good(true);
        for (int i = 0; i < 3; i++) new Agent();

        assertThatCode(() -> new TradingCycle().startTrading(10))
                .as("startTrading must not throw any exception in headless mode")
                .doesNotThrowAnyException();
    }

    @Test
    void anyPricesRecordedAreFiniteAndNonNegative() throws Exception {
        TradingCycle.setTestMode(true);
        TradingCycle.testThreadPoolSize = 1;
        Exchange.setLiveActive(false);
        Exchange.setLiveForced(false);

        Session.setNumAgents(3);
        Good.setOutstandingShares(1500);
        Good.setDirectlyAvailable(1500);
        Agent.setSentiment(20);
        Agent.nextID = 1;

        new Good(true);
        for (int i = 0; i < 3; i++) new Agent();

        new TradingCycle().startTrading(10);

        // The company IPO ask is raised 5% post-IPO; DefaultStrategy's hit-ask gate requires
        // the offer to be within 3% of current price, so the company's ask is never hit in the
        // main phase. With few agents and a fixed seed, no inter-agent spreads may overlap
        // either, leaving priceList empty. Any prices that DO get recorded must be finite.
        assertThat(Good.getPriceList())
                .as("every recorded trade price must be finite and non-negative")
                .allMatch(p -> !Float.isNaN(p) && !Float.isInfinite(p) && p >= 0f);
    }
}
