package utilities;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Centralised access to a {@link Random} instance.
 * <p>
 * Production: {@link #get()} returns {@link ThreadLocalRandom#current()} — fast and
 * per-thread. Tests: call {@link #setForTest(long)} to fix a seed; {@link #get()}
 * then returns a single seeded {@link Random} until {@link #clear()} is invoked.
 */
public final class RandomProvider {

    private static volatile Random testRandom;

    private RandomProvider() {}

    public static Random get() {
        Random r = testRandom;
        return (r != null) ? r : ThreadLocalRandom.current();
    }

    public static void setForTest(long seed) {
        testRandom = new Random(seed);
    }

    public static void clear() {
        testRandom = null;
    }
}
