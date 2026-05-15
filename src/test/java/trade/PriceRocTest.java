package trade;

import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies {@link Exchange#getPriceRoc(int)} computes the rate of change
 * between {@link Exchange#lastPrice} and the round close {@code window}
 * rounds ago.
 */
class PriceRocTest extends GlobalStateFixture {

    @BeforeEach
    void setUp() throws InterruptedException {
        new Good(true);
    }

    @Test
    void returnsZeroWhenHistoryShorterThanWindow() {
        Exchange.lastPrice = 100f;
        // Only 3 entries vs. a 5-round window
        Exchange.getRoundFinalPrice().add(100f);
        Exchange.getRoundFinalPrice().add(100f);
        Exchange.getRoundFinalPrice().add(100f);
        assertThat(Exchange.getPriceRoc(5)).isZero();
    }

    @Test
    void returnsZeroWhenWindowIsZeroOrNegative() {
        Exchange.lastPrice = 100f;
        for (int i = 0; i < 10; i++) Exchange.getRoundFinalPrice().add(100f);
        assertThat(Exchange.getPriceRoc(0)).isZero();
        assertThat(Exchange.getPriceRoc(-1)).isZero();
    }

    @Test
    void computesRocAgainstWindowAgoClose() {
        // History: 100, 100, 100, 100, 100, 100 (six entries)
        for (int i = 0; i < 6; i++) Exchange.getRoundFinalPrice().add(100f);
        Exchange.lastPrice = 105f;
        // size=6, window=5 → past = roundFinalPrice.get(6-1-5)=get(0)=100 → roc=(105-100)/100=0.05
        assertThat(Exchange.getPriceRoc(5)).isCloseTo(0.05f, within(1e-4f));
    }

    @Test
    void negativeRocOnDowntrend() {
        for (int i = 0; i < 6; i++) Exchange.getRoundFinalPrice().add(100f);
        Exchange.lastPrice = 95f;
        assertThat(Exchange.getPriceRoc(5)).isCloseTo(-0.05f, within(1e-4f));
    }

    @Test
    void returnsZeroWhenPastPriceIsZero() {
        Exchange.getRoundFinalPrice().add(0f);
        for (int i = 0; i < 5; i++) Exchange.getRoundFinalPrice().add(100f);
        Exchange.lastPrice = 105f;
        // past = get(0) = 0 → guard returns 0
        assertThat(Exchange.getPriceRoc(5)).isZero();
    }
}
