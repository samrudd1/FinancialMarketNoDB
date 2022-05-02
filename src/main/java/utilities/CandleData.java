package utilities;

import lombok.Getter;
import trade.TradeData;

import java.util.ArrayList;

/**
 * compiles TradeData objects into CandleData objects for the candle stick chart
 * @version 1.0
 * @since 13/04/22
 * @author github.com/samrudd1
 */
public class CandleData {
    @Getter private final float open;
    @Getter private final float close;
    @Getter private final float low;
    @Getter private final float high;
    @Getter private final int round;
    @Getter private final int volume;

    /**
     * creates new data object for candlestick chart
     * @param o opening price
     * @param c closing price
     * @param l lowest price
     * @param h highest price
     * @param r round number
     * @param v volume
     */
    public CandleData(float o, float c, float l, float h, int r, int v) {
        open = o;
        close = c;
        low = l;
        high = h;
        round = r;
        volume = v;
    }

    /**
     * static method that creates CandleData objects from the list of TradeData
     * @param trades TradeData list
     * @return list of CandleData objects
     */
    public static ArrayList<CandleData> createCandles(ArrayList<TradeData> trades) {
        ArrayList<CandleData> candles = new ArrayList<>(); //list to hold objects
        int maxRound = trades.get(trades.size() - 1).getRound();
        ArrayList<Float> temp = new ArrayList<>();
        int candleCount = 0;
        for (int i = 1; i < maxRound; i++) { // cycles through all rounds
            temp.clear();
            float open = 0;
            float close = 0;
            float low = 99999;
            float high = 0;
            int volume = 0;
            for (TradeData trade: trades) { //cycles through all trades
                if (trade.getRound() == i) { //finds trades at the right round
                    temp.add(trade.getPrice());
                    volume += trade.getVolume();
                    if (trade.getPrice() < low) {
                        low = trade.getPrice();
                    }
                    if (trade.getPrice() > high) {
                        high = trade.getPrice();
                    }
                }
            }
            if (temp.size() > 1) {
                //creates CnadleData object and adds to list
                candleCount++;
                open = temp.get(0);
                close = temp.get(temp.size() - 1);
                candles.add(new CandleData(open, close, low, high, candleCount, volume));
            }
        }
        return candles;
    }
}
