package good;

import agent.Agent;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * used as bid and ask offers, created by agents to add to the order book
 * @version 1.0
 * @since 17/01/22
 * @author github.com/samrudd1
 */
public class Offer implements Comparable<Offer> {
    /**
     * Per-Offer unique id. Used as a tiebreaker in {@link #compareTo} so that
     * offers at the same price remain distinguishable to the order-book tree.
     *
     * <p>Without this, the vendored {@code SortedList}'s {@code contains}/
     * {@code remove} methods — which compare purely via the comparator — would
     * silently match a different agent's same-price offer and remove the wrong
     * node from the book, leaking the original agent's locked funds.
     */
    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    @Getter @Setter private float price; //price willing to buy or sell at
    @Getter private final Agent offerMaker; //agent that made the offer
    @Getter private final Good good; //reference to the stock object
    @Getter @Setter private int numOffered; //quantity of shares available to buy or sell
    @Getter private final long id; //unique per-offer id; tiebreaker for compareTo so same-price offers stay distinct in the order book

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
        this.id = NEXT_ID.incrementAndGet();
    }

    /**
     * Orders offers by price first, then by id (older first) as a tiebreaker.
     * The id tiebreaker is load-bearing: the order book's {@code SortedList} uses
     * {@code compareTo} alone for {@code contains}/{@code remove}, so two offers
     * at the same price would otherwise be treated as one node and the wrong
     * agent's funds would be left locked when one of them filled.
     */
    @Override
    public int compareTo(Offer o) {
        int priceCmp = Math.round((price - o.getPrice()) * 100);
        if (priceCmp != 0) return priceCmp;
        return Long.compare(id, o.id);
    }

    /**
     * Resets the per-offer id sequence. For test fixtures only — keeps offer
     * ids deterministic across {@code @Test} methods that share a JVM.
     */
    public static void resetForTest() {
        NEXT_ID.set(0);
    }
}
