package trade;

import lombok.Getter;

/**
 * used to store data from each trade
 * @version 1.0
 * @since 13/04/22
 * @author github.com/samrudd1
 */
public class TradeData implements Comparable<TradeData> {
    @Getter private final float price; //price of the trade
    @Getter private final int volume; //number of shares traded
    @Getter private final int round; //the round the trade was processed
    public TradeData(float price, int volume, int round) {
        this.price = price;
        this.volume = volume;
        this.round = round;
    }

    /**
     * used to order the objects by round
     * @param o the object to be compared.
     * @return comparison of rounds
     */
    @Override
    public int compareTo(TradeData o) {
        return Integer.compare(this.round, o.round);
    }
}
