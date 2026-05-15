package support;

import agent.Agent;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.Test;
import session.Session;
import trade.Exchange;
import trade.TradingCycle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift-detector for the resetForTest() methods. If someone adds a new non-final
 * static field to Exchange / Good / Agent / Session / TradingCycle but forgets to
 * include it in the matching resetForTest(), this test fails.
 *
 * Design:
 *  1. Run all resetForTest() methods to capture the baseline value of every
 *     non-final static field.
 *  2. Mutate every such field to a non-baseline value (and pollute the static
 *     final Collections / Maps that resetForTest is expected to .clear()).
 *  3. Run resetForTest() again.
 *  4. Assert every non-final static is back at its baseline AND every final
 *     Collection / Map is empty.
 */
class StaticStateInvariantTest {

    private static final List<Class<?>> CLASSES = List.of(
            Exchange.class, Good.class, Agent.class, Session.class, TradingCycle.class);

    @Test
    void allResetForTestMethodsRunWithoutError() {
        Exchange.resetForTest();
        Good.resetForTest();
        Agent.resetForTest();
        Session.resetForTest();
        TradingCycle.resetForTest();
    }

    @Test
    void resetRestoresAllNonFinalStaticsToBaseline() throws Exception {
        for (Class<?> clazz : CLASSES) {
            verifyResetCoversAllNonFinalStatics(clazz);
        }
    }

    @Test
    void resetEmptiesAllStaticFinalCollections() throws Exception {
        // Pollute typed Collections/Maps with values they accept.
        Good.getBid().add(new Offer(10f, null, null, 5));
        Good.getAsk().add(new Offer(20f, null, null, 5));
        Good.getPriceList().add(50f);
        Good.getAvgPriceList().add(50f);
        Good.getPriceData().put(1, 50f);
        Exchange.getRoundFinalPrice().add(50f);
        Exchange.getRsiList().add(50f);
        Exchange.getRsiPList().add(50f);
        Exchange.getInstance().getGoods().add(null);
        Exchange.getInstance().getAvg20().add(50f);
        // Session.agents / goods / ownerships kept untouched: their key/value
        // types require constructing real domain objects with side-effects.

        Exchange.resetForTest();
        Good.resetForTest();
        Agent.resetForTest();
        Session.resetForTest();
        TradingCycle.resetForTest();

        for (Class<?> clazz : CLASSES) {
            assertAllStaticFinalCollectionsEmpty(clazz);
        }
        // Singleton instance fields on Exchange.exchange need a hand-roll check —
        // they're not statics but they are persistent across the JVM lifetime.
        assertThat(Exchange.getInstance().getGoods())
                .as("Exchange.exchange.goods should be cleared by resetForTest")
                .isEmpty();
        assertThat(Exchange.getInstance().getAvg20())
                .as("Exchange.exchange.avg20 should be cleared by resetForTest")
                .isEmpty();
    }

    private static void verifyResetCoversAllNonFinalStatics(Class<?> clazz) throws Exception {
        runReset(clazz);

        Map<Field, Object> baseline = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && !Modifier.isFinal(mod) && !f.isSynthetic()) {
                f.setAccessible(true);
                baseline.put(f, f.get(null));
            }
        }

        for (Map.Entry<Field, Object> e : baseline.entrySet()) {
            mutate(e.getKey(), e.getValue());
        }

        runReset(clazz);

        for (Map.Entry<Field, Object> e : baseline.entrySet()) {
            Field f = e.getKey();
            assertThat(f.get(null))
                    .as("%s.%s was not reset by %s.resetForTest()",
                            clazz.getSimpleName(), f.getName(), clazz.getSimpleName())
                    .isEqualTo(e.getValue());
        }
    }

    private static void assertAllStaticFinalCollectionsEmpty(Class<?> clazz) throws Exception {
        for (Field f : clazz.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isFinal(mod) && !f.isSynthetic()) {
                f.setAccessible(true);
                Object val = f.get(null);
                if (val instanceof Collection<?> c) {
                    assertThat(c)
                            .as("Static final Collection %s.%s should be empty after reset",
                                    clazz.getSimpleName(), f.getName())
                            .isEmpty();
                } else if (val instanceof Map<?, ?> m) {
                    assertThat(m)
                            .as("Static final Map %s.%s should be empty after reset",
                                    clazz.getSimpleName(), f.getName())
                            .isEmpty();
                }
            }
        }
    }

    private static void runReset(Class<?> clazz) {
        if (clazz == Exchange.class) Exchange.resetForTest();
        else if (clazz == Good.class) Good.resetForTest();
        else if (clazz == Agent.class) Agent.resetForTest();
        else if (clazz == Session.class) Session.resetForTest();
        else if (clazz == TradingCycle.class) TradingCycle.resetForTest();
        else throw new IllegalArgumentException("No reset hook for " + clazz);
    }

    private static void mutate(Field f, Object original) throws Exception {
        Class<?> type = f.getType();
        Object value;
        if (type == int.class) value = ((int) original) + 999;
        else if (type == long.class) value = ((long) original) + 999L;
        else if (type == float.class) value = ((float) original) + 999f;
        else if (type == double.class) value = ((double) original) + 999.0;
        else if (type == boolean.class) value = !((boolean) original);
        else if (type == String.class) value = "MUTATED_FOR_TEST";
        else if (type == Integer.class) value = (original == null ? 999 : (Integer) original + 999);
        else value = null; // for object refs — covers cases like Good.company
        f.set(null, value);
    }
}
