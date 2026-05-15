package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import trade.TradingCycle;

import java.util.ArrayList;

/**
 * abstract class used as a template for all strategies
 * @version 1.0
 * @since 10/03/22
 * @author github.com/samrudd1
 */
public abstract class AbstractStrategy {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    /**
     * super constructor for all strategies
     * @param agent agent using the strategy
     * @param tc reference to TradingCycle
     * @param roundNum the current round
     */
    public AbstractStrategy(Agent agent, TradingCycle tc, int roundNum) {
        this.agent = agent;
        this.tc = tc;
        this.roundNum = roundNum;
    }

    /**
     * method used to place a bid offer on the order book
     * @param price price to place the bid
     * @param good reference to the stock object
     * @param numOffered number of shares willing to buy
     * @throws InterruptedException from wait() function in the addBid() method
     */
    void createBid(float price, Good good, int numOffered) throws InterruptedException {
        if ((price < good.getLowestAsk()) && (numOffered > 0)) { //checks if bid is valid
            Offer newBid = new Offer(price, agent, good, numOffered);
            // Only commit funds and track the offer when the book actually accepted it.
            // addBid can silently drop offers when the ask side is empty and bids exist;
            // deducting funds for a dropped offer would permanently lose that money.
            if (good.addBid(newBid)) {
                agent.setFunds((agent.getFunds() - (price * numOffered))); //takes money from agent to cover the bid
                agent.getBidsPlaced().add(newBid); //adds bid to list so agent can access it later
                agent.setPlacedBid(true); //stops agents placing lots of bids consecutively
            }
        }
    }

    /**
     * method used to add an ask offer to the order book
     * @param price price willing to sell shares at
     * @param good reference to the owned stock
     * @param numOffered number of shares willing to sell
     * @throws InterruptedException from the wait() function in the addAsk() method
     */
    void createAsk(float price, OwnedGood good, int numOffered) throws InterruptedException {
        if ((price > good.getGood().getHighestBid()) && (numOffered > 0)) { // checks if offer is valid
            Offer newAsk = new Offer(price, agent, good.getGood(), numOffered);
            // Only lock shares and track the offer when the book actually accepted it.
            // addAsk can silently drop offers when the bid side is empty and asks exist;
            // decrementing numAvailable for a dropped offer would permanently lock those shares.
            if (good.getGood().addAsk(newAsk)) {
                good.setNumAvailable((good.getNumAvailable() - numOffered)); //makes shares unavailable
                agent.getAsksPlaced().add(newAsk); //adds offer to list so agent can access it later
                agent.setPlacedAsk(true); //stops agents placing lots of asks consecutively
            }
        }
    }

    /**
     * used by agents to remove their offers that are far from the current price
     * this allows them to place another offer at a more competitive price
     * @param agent agent to check offers for
     * @param price current price of the stock
     * @throws InterruptedException from the wait() function in the stock object
     */
    void cleanOffers(Agent agent, float price) throws InterruptedException {
        if (!agent.getBidsPlaced().isEmpty()) {
            // First pass: collect stale (too far below market) and dead (already filled/removed) bids.
            // Two-pass pattern avoids ConcurrentModificationException on bidsPlaced.
            ArrayList<Offer> stale = new ArrayList<>();
            ArrayList<Offer> dead  = new ArrayList<>();
            for (Offer offer : agent.getBidsPlaced()) {
                if (!Good.getBid().contains(offer)) {
                    dead.add(offer); // filled or previously cleaned — remove from tracking only
                } else if (offer.getPrice() < (price * 0.7)) { // >30% below market — cancel
                    stale.add(offer);
                } else if (offer.getPrice() >= (price * 1.05)) { // at/above Exchange price-band ceiling — can never fill
                    stale.add(offer);
                }
            }
            // Second pass: cancel stale bids from the book (refunds funds via Agent.removedBid).
            for (Offer offer : stale) {
                offer.getGood().removeBid(offer);
            }
            agent.getBidsPlaced().removeAll(stale);
            agent.getBidsPlaced().removeAll(dead);
        }
        if (!agent.getAsksPlaced().isEmpty()) {
            ArrayList<Offer> stale = new ArrayList<>();
            ArrayList<Offer> dead  = new ArrayList<>();
            for (Offer offer : agent.getAsksPlaced()) {
                if (!Good.getAsk().contains(offer)) {
                    dead.add(offer); // filled or previously cleaned — remove from tracking only
                } else if (offer.getPrice() > (price * 1.4)) { // >40% above market — cancel
                    stale.add(offer);
                } else if (offer.getPrice() <= (price * 0.96)) { // at/below Exchange price-band floor — can never fill
                    stale.add(offer);
                }
            }
            for (Offer offer : stale) {
                offer.getGood().removeAsk(offer);
            }
            agent.getAsksPlaced().removeAll(stale);
            agent.getAsksPlaced().removeAll(dead);
        }
    }
}
