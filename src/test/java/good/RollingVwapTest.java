package good;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies {@link Good#getRollingVwap(int)} computes a volume-weighted
 * average across the most recent {@code window} trade records.
 */
class RollingVwapTest extends GlobalStateFixture {

    @BeforeEach
    void setUp() throws InterruptedException {
        new Good(true);
    }

    @Test
    void fallsBackToCurrentPriceWhenTradeHistoryEmpty() {
        assertThat(Good.getRollingVwap(50)).isEqualTo(Good.getPrice());
    }

    @Test
    void volumeWeightedAcrossAllEntriesWhenWindowExceedsHistory() {
        Good.addTradeData(10f, 100, 0);
        Good.addTradeData(20f, 300, 1);
        // (10*100 + 20*300) / (100+300) = 7000 / 400 = 17.5
        assertThat(Good.getRollingVwap(50)).isCloseTo(17.5f, within(1e-3f));
    }

    @Test
    void windowSlidesOverOnlyMostRecentEntries() {
        Good.addTradeData(5f, 1000, 0);   // old, must be excluded by a window=2
        Good.addTradeData(10f, 100, 1);
        Good.addTradeData(20f, 300, 2);
        // window=2 → only the last two trades count: (10*100 + 20*300)/400 = 17.5
        assertThat(Good.getRollingVwap(2)).isCloseTo(17.5f, within(1e-3f));
    }

    @Test
    void singleTradeReturnsItsOwnPrice() {
        Good.addTradeData(42.5f, 50, 0);
        assertThat(Good.getRollingVwap(10)).isCloseTo(42.5f, within(1e-3f));
    }
}
