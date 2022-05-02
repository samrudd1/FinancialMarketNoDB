package trade;

import agent.OwnedGood;
import lombok.Getter;

import java.util.logging.Logger;

/**
 * used to store data from each trade
 * @version 1.0
 * @since 13/04/22
 * @author github.com/samrudd1
 */
public class TradeData implements Comparable {
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
    public int compareTo(Object o) {
        try{
            TradeData other = (TradeData) o;
            return Integer.compare(this.getRound(), other.getRound()); //orders objects by round number
        } catch (Exception e){
            return 1;
        }
    }
}
