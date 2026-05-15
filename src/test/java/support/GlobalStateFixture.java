package support;

import agent.Agent;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import session.Session;
import trade.Exchange;
import trade.TradingCycle;
import utilities.RandomProvider;

/**
 * Base class for tests that touch any global or static state. Resets all
 * known static state and seeds RandomProvider before every test, then clears
 * the test seed afterwards so leftover global state doesn't bleed into
 * non-fixture tests in the same JVM.
 */
public abstract class GlobalStateFixture {

    protected static final long DEFAULT_TEST_SEED = 42L;

    @BeforeEach
    protected void resetGlobalState() {
        RandomProvider.setForTest(DEFAULT_TEST_SEED);
        Session.resetForTest();
        Good.resetForTest();
        Agent.resetForTest();
        Exchange.resetForTest();
        TradingCycle.resetForTest();
        Offer.resetForTest();
    }

    @AfterEach
    void clearRandomProvider() {
        RandomProvider.clear();
    }
}
