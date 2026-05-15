package utilities;

import trade.TradeData;

import java.util.List;
import java.util.TreeMap;

/**
 * Aggregates raw {@link TradeData} into a single OHLCV candle for one round.
 * Extracted from {@code LineChartLive} so the live chart and the post-run
 * dashboard share one binning implementation.
 *
 * <p>Mutable by design — call {@link #add(float, int)} as trades arrive in
 * round order. Use {@link #binByRound(List)} to bin a whole trade list at once.
 */
public class CandleAccum {

    public float open  = Float.NaN;
    public float close = 0f;
    public float high  = -Float.MAX_VALUE;
    public float low   =  Float.MAX_VALUE;
    public int totalVolume = 0;

    public void add(float price, int vol) {
        if (Float.isNaN(open)) open = price;
        close = price;
        if (price > high) high = price;
        if (price < low)  low  = price;
        totalVolume += vol;
    }

    public boolean isEmpty() { return Float.isNaN(open); }

    /**
     * Bins a list of trades into per-round candles, sorted by round ascending.
     * Trades are grouped by {@link TradeData#getRound()}; within each round,
     * order is the input order — callers should pass trades in execution order
     * so {@code open}/{@code close} reflect the first and last trade of the round.
     */
    public static TreeMap<Integer, CandleAccum> binByRound(List<TradeData> trades) {
        TreeMap<Integer, CandleAccum> byRound = new TreeMap<>();
        for (TradeData td : trades) {
            byRound.computeIfAbsent(td.getRound(), k -> new CandleAccum())
                   .add(td.getPrice(), td.getVolume());
        }
        return byRound;
    }
}
