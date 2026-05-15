package dashboard;

import org.junit.jupiter.api.Test;
import trade.TradeData;
import utilities.CandleAccum;

import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests the OHLCV binning that both the live chart and the post-run dashboard's
 * Overview tab depend on. {@link CandleAccum} was extracted from
 * {@code LineChartLive} so both consumers share one implementation.
 */
class CandleAccumTest {

    @Test
    void addCapturesFirstPriceAsOpenAndLastAsClose() {
        CandleAccum c = new CandleAccum();
        c.add(100f, 5);
        c.add(105f, 3);
        c.add(102f, 2);
        assertThat(c.open).isEqualTo(100f);
        assertThat(c.close).isEqualTo(102f);
    }

    @Test
    void addTracksHighLowAndCumulativeVolume() {
        CandleAccum c = new CandleAccum();
        c.add(100f, 5);
        c.add(120f, 3); // new high
        c.add(95f, 2);  // new low
        c.add(110f, 4);
        assertThat(c.high).isEqualTo(120f);
        assertThat(c.low).isEqualTo(95f);
        assertThat(c.totalVolume).isEqualTo(14);
    }

    @Test
    void isEmptyReturnsTrueBeforeFirstAdd() {
        assertThat(new CandleAccum().isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseAfterAdd() {
        CandleAccum c = new CandleAccum();
        c.add(50f, 1);
        assertThat(c.isEmpty()).isFalse();
    }

    @Test
    void binByRoundGroupsTradesByRound() {
        List<TradeData> trades = List.of(
            new TradeData(100f, 10, 1),
            new TradeData(105f, 5,  1),
            new TradeData(110f, 7,  2),
            new TradeData(108f, 3,  2),
            new TradeData(115f, 4,  3)
        );

        TreeMap<Integer, CandleAccum> byRound = CandleAccum.binByRound(trades);

        assertThat(byRound).containsOnlyKeys(1, 2, 3);

        CandleAccum r1 = byRound.get(1);
        assertThat(r1.open).isEqualTo(100f);
        assertThat(r1.close).isEqualTo(105f);
        assertThat(r1.high).isEqualTo(105f);
        assertThat(r1.low).isEqualTo(100f);
        assertThat(r1.totalVolume).isEqualTo(15);

        CandleAccum r2 = byRound.get(2);
        assertThat(r2.open).isEqualTo(110f);
        assertThat(r2.close).isEqualTo(108f);
        assertThat(r2.totalVolume).isEqualTo(10);

        CandleAccum r3 = byRound.get(3);
        assertThat(r3.open).isEqualTo(115f);
        assertThat(r3.close).isEqualTo(115f);
        assertThat(r3.high).isEqualTo(r3.low, within(0.0001f));
    }

    @Test
    void binByRoundReturnsEmptyMapForEmptyInput() {
        assertThat(CandleAccum.binByRound(List.of())).isEmpty();
    }

    @Test
    void binByRoundKeysAreSortedAscending() {
        // Pass trades in scrambled round order; TreeMap keys must come back sorted
        // because the dashboard X axis depends on round-ordering.
        List<TradeData> scrambled = List.of(
            new TradeData(100f, 1, 5),
            new TradeData(101f, 1, 2),
            new TradeData(102f, 1, 8),
            new TradeData(103f, 1, 1)
        );
        TreeMap<Integer, CandleAccum> byRound = CandleAccum.binByRound(scrambled);
        assertThat(byRound.keySet()).containsExactly(1, 2, 5, 8);
    }
}
