package good;

import agent.Agent;
import lombok.Getter;
import lombok.Setter;

/**
 * used as bid and ask offers, created by agents to add to the order book
 * @version 1.0
 * @since 17/01/22
 * @author github.com/samrudd1
 */
public class Offer implements Comparable<Offer> {
    @Getter @Setter private float price; //price willing to buy or sell at
    @Getter private final Agent offerMaker; //agent that made the offer
    @Getter private final Good good; //reference to the stock object
    @Getter @Setter private int numOffered; //quantity of shares available to buy or sell

    /**
     * constructor used by agents
     * @param price price to place the offer
     * @param offerMaker agent that created the object
     * @param good //reference to the stock object
     * @param numOffered //number of shares to buy or sell
     */
    public Offer(float price, Agent offerMaker, Good good, int numOffered) {
        this.price = price;
        this.offerMaker = offerMaker;
        this.good = good;
        this.numOffered = numOffered;
    }

    /**
     * used in the order book lists to order them by price
     * @param o the object to be compared.
     * @return the price
     */
    @Override
    public int compareTo(Offer o) {
        //orders the offer objects in the list by price
        return Math.round((price - o.getPrice()) * 100);
    }
}
