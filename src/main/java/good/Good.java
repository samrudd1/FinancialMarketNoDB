package good;

import agent.Agent;
import agent.OwnedGood;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import session.Session;
import trade.Exchange;
import trade.TradeData;

import java.util.*;
import java.util.logging.Logger;
import utilities.SortedListPackage.*;

/**
 * controls all information related to the stock, especially the order book
 * @version 1.0
 * @since 21/12/21
 * @author github.com/samrudd1
 */
@Log
@EqualsAndHashCode
public class Good {
    private static final Logger LOGGER = Logger.getLogger(Good.class.getName());
    @Getter private final int id;
    @Getter static private String name = "Stock";
    @Getter static private float prevPrice;
    @Getter static private float price;
    @Getter static private float startingPrice;
    @Getter static private float vwap = 0; //Volume Weighted Average Price, average price all shares have traded at
    @Getter static private double volume = 0; //total number of shares traded
    @Getter static private float lowest = 110; //ensures lowest and highest will change instantly
    @Getter static private float highest = 1;
    @Getter @Setter static private Integer numTrades = 0;

    //both the bid and ask use a sorted list from Scott Logic that was much faster than an Array List
    static private final NaturalSortedList<Offer> bid = new NaturalSortedList<>(); //bid side of the order book
    static private final NaturalSortedList<Offer> ask = new NaturalSortedList<>(); //ask side of the order book
    static private boolean bidLock; //concurrency lock for the bid list, keeps the list thread-safe
    static private boolean askLock; //concurrency lock for the ask list, keeps the list thread-safe
    @Getter private static Agent company; //Agent object of the company, used for the Initial offering
    @Getter @Setter static private int outstandingShares;
    @Getter @Setter static private int directlyAvailable;
    @Getter private static final Map<Integer,Float> priceData = new HashMap<>(); //tracks the history of the price and its round after each trade
    @Getter private static final ArrayList<Float> priceList = new ArrayList<>(); //list holding each price from all trades, used in chart at the end
    @Getter private static final ArrayList<Float> avgPriceList = new ArrayList<>(); //uses prices that are the average of the last 20 trades
    @Getter private static final ArrayList<TradeData> tradeData = new ArrayList<>(); //TradeData objects used for candlestick chart

    /**
     * Constructor used in the RunMarket class
     * @param isNew if the object has been saved to the database
     */
    public Good(boolean isNew) throws InterruptedException {
        id = 1;
        if (Session.getGoods().isEmpty()) {
            Session.getGoods().add(0, this);
        } else {
            Session.getGoods().set(0, this);
        }
        company = new Agent((getName() + " company"), true);
        createPrice();
        Exchange.getInstance().addGood(this);
        getCompany().getGoodsOwned().add(0, new OwnedGood(getCompany(), this, directlyAvailable, 0, 0, true));
        directlyAvailable = 0;
        addAsk(new Offer(price, getCompany(), this, getCompany().getGoodsOwned().get(0).getNumOwned()));
    }

    /**
     * Used by the populateGoods() method in the Session class
     * @param id object id
     * @param name company name
     * @param price starting price
     * @param prevPrice last price
     * @param amountAvailable amount for sale
     */
    public Good(int id, String name, float price, float prevPrice, int amountAvailable) throws InterruptedException {
        this.id = id;
        Good.name = name;
        if (Session.getGoods().isEmpty()) {
            Session.getGoods().add(0, this);
        } else {
            Session.getGoods().set(0, this);
        }
        company = new Agent((getName() + " company"), true);
        createPrice();
        Exchange.getInstance().addGood(this);
        getCompany().getGoodsOwned().add(0, new OwnedGood(getCompany(), this, directlyAvailable, 0, 0, true));
        directlyAvailable = 0;
        addAsk(new Offer(price, getCompany(), this, getCompany().getGoodsOwned().get(0).getNumOwned()));
    }

    public static NaturalSortedList<Offer> getBid() { return Good.bid; }
    public static NaturalSortedList<Offer> getAsk() { return Good.ask; }

    /**
     * gets the price of the highest bid
     * @return the price of the highest bid
     * @throws InterruptedException from the wait() function
     */
    public synchronized float getHighestBid() throws InterruptedException {
        while (bidLock) wait(); //concurrency locks
        bidLock = true;
        if (bid.size() > 0) {
            bidLock = false;
            notify();
            return bid.get(bid.size() - 1).getPrice();
        }
        bidLock = false;
        notify();
        return 0; //shows the bid is empty
    }

    /**
     * gets the price of the lowest ask
     * @return the price of the lowest ask
     * @throws InterruptedException from the wait() function
     */
    public synchronized float getLowestAsk() throws InterruptedException {
        while (askLock) wait(); //concurrency locks
        askLock = true;
        if (ask.size() > 0) {
            askLock = false;
            notify();
            return ask.get(0).getPrice();
        }
        askLock = false;
        notify();
        return 99999; //shows the ask is empty
    }

    /**
     * was used by the high frequency algorithm
     * @return the price of the second highest bid
     * @throws InterruptedException from the wait condition
     */
    public synchronized float getSecondHighestBid() throws InterruptedException {
        while (bidLock) wait();
        bidLock = true;
        if (bid.size() > 1) {
            bidLock = false;
            notify();
            return bid.get(bid.size() - 2).getPrice();
        }
        bidLock = false;
        notify();
        return 0;
    }

    /**
     * was used by the high frequency algorithm
     * @return the price of the second lowest ask
     * @throws InterruptedException from the wait() function
     */
    public synchronized float getSecondLowestAsk() throws InterruptedException {
        while (askLock) wait();
        askLock = true;
        if (ask.size() > 1) {
            askLock = false;
            notify();
            return ask.get(1).getPrice();
        }
        askLock = false;
        notify();
        return 99999;
    }

    /**
     * gets the offer of the highest bid
     * @return the offer of the lowest ask
     * @throws InterruptedException from the wait() function
     */
    public synchronized Offer getHighestBidOffer() throws InterruptedException {
        while (bidLock) wait();
        bidLock = true;
        if (bid.size() > 0) {
            if (bid.get(bid.size() - 1).getNumOffered() == 0) {
                bid.remove(bid.get(bid.size() - 1));
                bidLock = false;
                notify();
                return null;
            }
            bidLock = false;
            notify();
            return bid.get(bid.size() - 1);
        }
        bidLock = false;
        notify();
        return null;
    }

    /**
     * gets the offer of the lowest ask
     * @return the offer of the lowest ask
     * @throws InterruptedException from the wait() function
     */
    public synchronized Offer getLowestAskOffer() throws InterruptedException {
        while (askLock) wait();
        askLock = true;
        if (ask.size() > 0) {
            if (ask.get(0).getNumOffered() == 0) {
                ask.remove(ask.get(0));
                askLock = false;
                notify();
                return null;
            }
            askLock = false;
            notify();
            return ask.get(0);
        }
        askLock = false;
        notify();
        return null;
    }

    /**
     * creates a string showing up to the 20 highest bid offers, used by TradingCycle
     * @return the created string
     * @throws InterruptedException from the wait() function
     */
    public synchronized String bidString() throws InterruptedException {
        while (bidLock) wait();
        bidLock = true;
        StringBuilder str = new StringBuilder();
        if (bid.size() > 20) {
            for (int i = (bid.size() - 1); i >= (bid.size() - 21); i--) {
                Offer offer = bid.get(i);
                //each offer is represented like: "[q: 10 p: 58.94]" for example
                str.append("[q: ").append(offer.getNumOffered()).append(" p: ").append(offer.getPrice()).append("] ");
            }
        } else {
            for (int i = (bid.size() - 1); i >= 0; i--) {
                Offer offer = bid.get(i);
                str.append("[q: ").append(offer.getNumOffered()).append(" p: ").append(offer.getPrice()).append("] ");
            }
        }
        bidLock = false;
        notify();
        return str.toString();
    }

    /**
     * creates a string showing up to the 20 lowest ask offers, used by TradingCycle
     * @return
     * @throws InterruptedException
     */
    public synchronized String askString() throws InterruptedException {
        while (askLock) wait();
        askLock = true;
        StringBuilder str = new StringBuilder();
        if (ask.size() > 20) {
            for (int i = 0; i < 20; i++) {
                Offer offer = ask.get(i);
                //each offer is represented like: "[q: 10 p: 58.94]" for example
                str.append("[q: ").append(offer.getNumOffered()).append(" p: ").append(offer.getPrice()).append("] ");
            }
        } else {
            for (Offer offer : ask) {
                str.append("[q: ").append(offer.getNumOffered()).append(" p: ").append(offer.getPrice()).append("] ");
            }
        }
        askLock = false;
        notify();
        return str.toString();
    }

    /**
     * adds a new bid offer to the order book
     * @param offer the new bid
     * @throws InterruptedException from wait() function
     */
    public synchronized void addBid(Offer offer) throws InterruptedException {
        while (bidLock) wait();
        bidLock = true;
        //if ask is empty but bid isn't, no orders are added, otherwise bid could go super high and no asks could be added
        if ((ask.size() == 0) && (bid.size() > 0)) {
            bidLock = false;
            notify();
        } else {
            if (offer.getPrice() > 0) {
                bid.add(offer);
            }
            bidLock = false;
            notify();
        }
    }

    /**
     * adds a new ask offer to the order book
     * @param offer the new ask
     * @throws InterruptedException from wait() function
     */
    public synchronized void addAsk(Offer offer) throws InterruptedException {
        while (askLock) wait();
        askLock = true;
        //if bid is empty but ask isn't, no orders are added, otherwise ask could go super low and no bids could be added
        if ((bid.size() == 0) && (ask.size() > 0)) {
            askLock = false;
            notify();
        } else {
            if (offer.getPrice() > 0) {
                ask.add(offer);
            }
            askLock = false;
            notify();
        }
    }

    /**
     * removes a bid offer from the order book
     * @param offer the offer to be removed
     * @throws InterruptedException from wait() function
     */
    public synchronized void removeBid(Offer offer) throws InterruptedException {
        while (bidLock) wait();
        bidLock = true;
        if (bid.contains(offer)) {
            bid.remove(offer);
            offer.getOfferMaker().removedBid(offer);
        }
        bidLock = false;
        notify();
    }

    /**
     * removes an ask offer from the order book
     * @param offer the offer to be removed
     * @throws InterruptedException from wait() function
     */
    public synchronized void removeAsk(Offer offer) throws InterruptedException {
        while (askLock) wait();
        askLock = true;
        if (ask.contains(offer)) {
            ask.remove(offer);
            offer.getOfferMaker().removeAsk(offer);
        }
        askLock = false;
        notify();
    }

    /**
     * called when a new trade is finished, stored the data so it can be used in charts later
     * @param price price of the trade
     * @param amount number of shares traded
     * @param round round the trade occurred
     */
    public static void addTradeData(float price, int amount, int round) {
        tradeData.add(new TradeData(price, amount, round));
    }

    /**
     * creates the starting price for the stock to be offered
     */
    private void createPrice(){
        Random rand = new Random();
        float floor = 20;
        float ceiling = 100;
        price = rand.nextInt((int)(ceiling - floor) + 1) + floor;
        Good.startingPrice = price;
        Good.prevPrice = price;
        Exchange.lastPrice = price;
    }

    /**
     * updates the stock price after a trade, called from the Exchange
     * @param offer the offer that just traded
     * @param traded how many shares traded
     */
    public void setPrice(Offer offer, int traded){
        float newPrice = offer.getPrice();
        Good.prevPrice = price;
        numTrades += 1;
        price = (((float)Math.round(newPrice*100))/100);
        priceData.put(numTrades, price); //keeps track of all price movements
        priceList.add(price); //saves the price to be used in a chart
        if (newPrice > highest) { highest = newPrice; }
        if (newPrice < lowest) { lowest = newPrice; }
        vwap = (float) (((vwap * volume) + (offer.getPrice() * traded)) / (volume + traded)); //calculates new VWAP value
        volume += traded;
    }
}


