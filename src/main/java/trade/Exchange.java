package trade;

import agent.Agent;
import agent.BookEvent;
import agent.OwnedGood;
import agent.Trade;
import good.Good;
import good.Offer;
import lombok.Getter;
import lombok.Setter;
import utilities.LineChartLive;

import java.util.ArrayList;

/**
 * the central Exchange of the whole market, the place where all trades get executed
 * @version 1.0
 * @since 13/01/22
 * @author github.com/samrudd1
 */
public class Exchange {
    private static final Exchange exchange = new Exchange(); //keeps its own instance of the class
    @Getter private final ArrayList<Good> goods = new ArrayList<>(); //holds a reference to the stock object
    private Exchange() {} //private constructor to prevent more than one instance existing
    public static Exchange getInstance() { return exchange; } //returns the instance, used by other classes to access the exchange
    private static boolean exchangeLock = false; //concurrent lock, processes trades one at a time to prevent data mismanagement
    public void addGood(Good good) {
        goods.add(good);
    } //adds a stock object to the list

    /**
     * Returns the live chart, constructing it on the first call. Lazy so headless
     * tests that never enable the live chart never instantiate a JFrame.
     */
    public static LineChartLive getLiveChart() {
        if (liveChart == null) {
            liveChart = new LineChartLive();
        }
        return liveChart;
    }
    @Getter private final ArrayList<Float> avg20 = new ArrayList<>() ; //holds the last 20 trades so an average can be created
    private float lastAvg = 0; //the value of the last average price
    @Getter @Setter private float priceCheck; //used as more of an average for strategies to check prices with
    private static LineChartLive liveChart; //lazy-initialised on first getLiveChart() call so headless tests don't open a window
    @Getter @Setter private static boolean liveActive = false; //boolean to toggle the live chart
    @Getter @Setter private static boolean liveForced = false; //forces a chart update at the end of every round
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static float lastPrice; //last traded price
    //all below are counters used to tally the trades of each strategy
    @Getter private static float defaultCount = 0;
    @Getter private static float sentCount = 0;
    @Getter private static float momCount = 0;
    @Getter private static float vwapCount = 0;
    @Getter private static float vwapMRCount = 0;
    @Getter private static float rsiCount = 0;
    @Getter private static float rsi10Count = 0;
    @Getter private static float bothCount = 0;
    @Getter private static float offerCount = 0;

    @Getter private static int round = 0; //current round
    private static boolean newRound = false; //if new round has started
    @Getter private static final ArrayList<Float> roundFinalPrice = new ArrayList<>(); //list of the closing prices of each round
    @Getter private static float roundPrice = 0; //the last closing price
    @Getter private static final ArrayList<Float> rsiList = new ArrayList<>(); //list containing Relative Strength Index values
    @Getter private static float rsi = 0; //current RSI value
    @Getter private static final ArrayList<Float> rsiPList = new ArrayList<>(); //list containing RSI 10 values
    @Getter private static float rsiP = 0; //current RSI 10 value
    @Getter private static final ArrayList<String> signalLog = new ArrayList<>(); //list of signal logs
    @Getter @Setter private static boolean signalLogging; //toggles if logs are kept
    @Getter private static final ArrayList<Integer> sentimentList = new ArrayList<>(); //per-round sentiment value, fed to dashboard
    @Getter private static final ArrayList<Float> vwapList = new ArrayList<>(); //per-round VWAP snapshot, fed to dashboard

    /**
     * Rate of change of price over the last {@code window} rounds.
     * Returns {@code (lastPrice - roundFinalPrice[size-1-window]) / roundFinalPrice[size-1-window]},
     * or 0 when there isn't enough closing-price history yet.
     * Used by Momentum to detect sustained trends instead of single-round noise.
     */
    public static float getPriceRoc(int window) {
        int size = roundFinalPrice.size();
        if (size <= window || window <= 0) return 0f;
        float past = roundFinalPrice.get(size - 1 - window);
        if (past <= 0f) return 0f;
        return (lastPrice - past) / past;
    }

    /** Append the current sentiment value for the round just completed. Called from {@code TradingCycle.mutate}. */
    public static void addSentimentTick(int s) { sentimentList.add(s); }

    /** Append the current VWAP value for the round just completed. */
    public static void addVwapTick(float v) { vwapList.add(v); }

    /**
     * the method used to execute all trades on the market
     * @param buyer the buyer in the trade
     * @param seller the seller in the trade
     * @param offer the offer from the order book being traded
     * @param amountBought the number of shares being bought
     * @param tc reference to TradingCycle
     * @param roundNum the current round number
     * @return boolean if the trade was complete
     * @throws InterruptedException from the wait() function
     */
    public synchronized boolean execute(Agent buyer, Agent seller, Offer offer, int amountBought, TradingCycle tc, int roundNum) throws InterruptedException {
        boolean complete = true;
        if (buyer == seller) { //not worth trading ifd buyer and seller are the same
            TradingCycle.setAgentComplete(true);
            tc.notifyAll();
            return false;
        } else {
            while (exchangeLock) wait(); //concurrency lock keeps the method thread safe
            exchangeLock = true;

            if (lastAvg > 0) {
                priceCheck = lastAvg;
            } else {
                priceCheck = lastPrice;
            }
            if (Good.getBid().contains(offer) || Good.getAsk().contains(offer)) { // check offer exists
                if ((offer.getPrice() < (priceCheck * 1.05)) && (offer.getPrice() > (priceCheck * 0.96))) { //prevents large price swings
                    if (offer.getNumOffered() > 0) {

                        try { //trade execution
                            boolean offerIsAsk = Good.getAsk().contains(offer); // capture before offer is removed from book
                            if (offer.getNumOffered() < amountBought) {
                                amountBought = offer.getNumOffered();
                            }
                            if ((buyer.getGoodsOwned().isEmpty())) {
                                //creates OwnedGood object for buyer as they do not currently own stock
                                buyer.getGoodsOwned().add(0, new OwnedGood(buyer, offer.getGood(), amountBought, amountBought, (((float) Math.round(offer.getPrice() * 100)) / 100), true));
                            } else {
                                //adjusts buyer's existing OwnedGood object with new information
                                OwnedGood tempOwned = buyer.getGoodsOwned().get(0);
                                float newBoughtAt = (float) Math.round(((tempOwned.getBoughtAt() * tempOwned.getNumOwned()) + (amountBought * offer.getPrice())) * 100) / ((tempOwned.getNumOwned() + amountBought) * 100);
                                int newNumOwned = (tempOwned.getNumOwned() + amountBought);
                                int newAvailable = (tempOwned.getNumAvailable() + amountBought);
                                OwnedGood newOne = new OwnedGood(buyer, offer.getGood(), newNumOwned, newAvailable, newBoughtAt, false);
                                buyer.getGoodsOwned().set(0, newOne);
                            }
                            //decrement seller's share count
                            if (!seller.getGoodsOwned().isEmpty()) {
                                OwnedGood sellerOwned = seller.getGoodsOwned().get(0);
                                sellerOwned.setNumOwned(sellerOwned.getNumOwned() - amountBought);
                                if (!offerIsAsk) {
                                    // seller hit a bid directly — numAvailable was not pre-locked in createAsk()
                                    sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - amountBought);
                                }
                                // if offerIsAsk, numAvailable was already locked when the ask was placed in createAsk()
                            }
                            offer.getGood().setPrice(offer, amountBought); //updates stock price
                            boolean fullFill = (amountBought == offer.getNumOffered());
                            // role on each side: the offer maker is the MAKER, the counterparty is the TAKER.
                            // offerIsAsk → ask was on the book → seller is MAKER, buyer is TAKER.
                            // !offerIsAsk → bid was on the book → buyer is MAKER, seller is TAKER.
                            Trade.Role buyerRole  = offerIsAsk ? Trade.Role.TAKER : Trade.Role.MAKER;
                            Trade.Role sellerRole = offerIsAsk ? Trade.Role.MAKER : Trade.Role.TAKER;
                            Trade.Fill fillKind   = fullFill ? Trade.Fill.FULL : Trade.Fill.PARTIAL;
                            if (fullFill) {
                                // Full fill: remove atomically via *Full variants that zero numOffered
                                // inside the book lock, so no zero-quantity offer is ever visible.
                                if (offerIsAsk) {
                                    offer.getGood().removeAskFull(offer);
                                } else {
                                    offer.getGood().removeBidFull(offer);
                                }
                            } else {
                                offer.setNumOffered(offer.getNumOffered() - amountBought); //subtracts the number bought from the offer
                            }
                            //only charge buyer when hitting an ask — bid makers already reserved funds in createBid()
                            if (offerIsAsk) {
                                buyer.setFunds(buyer.getFunds() - (offer.getPrice() * amountBought));
                            }
                            seller.setFunds(seller.getFunds() + (offer.getPrice() * amountBought)); //changes the seller's funds
                            // record the fill on both sides for the dashboard's per-agent trade table
                            float p = offer.getPrice();
                            buyer.recordTrade(new Trade(roundNum, Trade.Side.BUY,  amountBought, p, seller.getId(), seller.getName(), buyerRole,  fillKind));
                            seller.recordTrade(new Trade(roundNum, Trade.Side.SELL, amountBought, p, buyer.getId(),  buyer.getName(),  sellerRole, fillKind));
                            // Per-maker book-event ledger entry for the fill. fundsDelta:
                            //   ask maker (seller side) → +p*amountBought (buyer paid them)
                            //   bid maker (buyer side)  → 0 (cash was pre-locked at createBid time)
                            Agent maker = offer.getOfferMaker();
                            BookEvent.Side mside = offerIsAsk ? BookEvent.Side.ASK : BookEvent.Side.BID;
                            BookEvent.Kind mkind = fullFill ? BookEvent.Kind.FILLED_FULL : BookEvent.Kind.FILLED_PARTIAL;
                            float makerDelta = offerIsAsk ? (p * amountBought) : 0f;
                            maker.getBookEvents().add(new BookEvent(roundNum, mkind, mside, p, amountBought, makerDelta, maker.getFunds()));
                        } catch (Exception e) {
                            complete = false; //trade failed
                        }
                    } else {
                        //removes offer if quantity was 0
                        if (Good.getBid().contains(offer)) {
                            offer.getGood().removeBid(offer);
                        } else {
                            offer.getGood().removeAsk(offer);
                        }
                    }
                    if (roundNum > round) { // if new round
                        if (!roundFinalPrice.isEmpty()) {
                            roundPrice = roundFinalPrice.get(roundFinalPrice.size() - 1);
                        }
                        roundFinalPrice.add(lastPrice);
                        newRound = true;
                    }
                    round = roundNum;
                    lastPrice = offer.getPrice();
                }
                //lets new thread execute trade while the current one saves data
                TradingCycle.setAgentComplete(true);
                exchangeLock = false;
                notifyAll();

                Good.addTradeData(offer.getPrice(), amountBought, roundNum); //adds trade information to list

                //calculates the average value of last 20 trades, used in the live chart
                if (avg20.size() < 20) {
                    avg20.add(offer.getPrice());
                } else {
                    avg20.remove(0);
                    avg20.add(offer.getPrice());
                    float avgResult = 0;
                    float total = 0;
                    for (float num : avg20) {
                        avgResult += num;
                        total++;
                    }
                    avgResult = avgResult / total;
                    lastAvg = avgResult;
                    Good.getAvgPriceList().add(avgResult);
                }

                //RSI
                if ((roundFinalPrice.size() > 15) && (newRound)) {
                    float avgGain = 0;
                    float avgLoss = 0;
                    float diff;
                    //RSI equation
                    for (int i = (roundFinalPrice.size() - 1); i >= (roundFinalPrice.size() - 14); i--) {
                        diff = (roundFinalPrice.get(i) / roundFinalPrice.get(i - 1)) - 1;
                        if (diff > 0) {
                            avgGain += diff;
                        } else if (diff < 0) {
                            avgLoss += (diff * -1);
                        }
                    }
                    rsi = 100 - (100 / (1 + (avgGain / avgLoss)));
                    rsiList.add(rsi);

                    if (signalLogging) { //creates signal logs when above or below thresholds
                        if (rsi > 80) {
                            signalLog.add(ANSI_RED + "Round " + roundNum + ": RSI Over-Bought: " + rsi + ", Sentiment: " + Agent.getSentiment() + ANSI_RESET);
                        } else if (rsi < 20) {
                            signalLog.add(ANSI_GREEN + "Round " + roundNum + ": RSI Over-Sold: " + rsi + ", Sentiment: " + Agent.getSentiment() + ANSI_RESET);
                        }
                    }

                    //RSI 10
                    if (roundFinalPrice.size() > 150) {
                        avgGain = 0;
                        avgLoss = 0;
                        //RSI 10 equation
                        for (int i = (roundFinalPrice.size() - 1); i >= (roundFinalPrice.size() - 140); i-=10) {
                            diff = (roundFinalPrice.get(i) / roundFinalPrice.get(i - 10)) - 1;
                            if (diff > 0) {
                                avgGain += diff;
                            } else if (diff < 0) {
                                avgLoss += (diff * -1);
                            }
                        }
                        rsiP = 100 - (100 / (1 + (avgGain / avgLoss)));
                        rsiPList.add(rsiP);

                        if (signalLogging) { //creates signal logs when above or below thresholds
                            if (rsiP > 70) {
                                signalLog.add(ANSI_RED + "Round " + roundNum + ": RSI 10 Over-Bought: " + rsiP + ", Sentiment: " + Agent.getSentiment() + ANSI_RESET);
                            } else if (rsiP < 30) {
                                signalLog.add(ANSI_GREEN + "Round " + roundNum + ": RSI 10 Over-Sold: " + rsiP + ", Sentiment: " + Agent.getSentiment() + ANSI_RESET);
                            }
                        }
                    }
                    newRound = false;
                }

                //the commented out code below is used to output each trade and changes colour based on the price movement
                //this is not commonly used anymore as the market became so fast that it was impossible to track
                /*
                if (complete) {
                    if (!buyer.getName().equalsIgnoreCase(seller.getName())) {
                        if (offer.getPrice() > Good.getPrevPrice()) {
                            System.out.println(ANSI_GREEN + "trade executed between " + buyer.getName() + " and "
                                    + seller.getName() + " for " + amountBought + " share/s at a price of "
                                    + ((float) Math.round(offer.getPrice() * 100) / 100) + " each. There are "
                                    + offer.getNumOffered() + " left on the offer." + ANSI_RESET);
                        } else if (offer.getPrice() < Good.getPrevPrice()) {
                            System.out.println(ANSI_RED + "trade executed between " + buyer.getName() + " and "
                                    + seller.getName() + " for " + amountBought + " share/s at a price of "
                                    + ((float) Math.round(offer.getPrice() * 100) / 100) + " each. There are "
                                    + offer.getNumOffered() + " left on the offer." + ANSI_RESET);
                        } else {
                            System.out.println("trade executed between " + buyer.getName() + " and "
                                    + seller.getName() + " for " + amountBought + " share/s at a price of "
                                    + ((float) Math.round(offer.getPrice() * 100) / 100) + " each. There are "
                                    + offer.getNumOffered() + " left on the offer." + ANSI_RESET);
                        }
                    }
                }
                */

                //adds to tallies for trades for each strategy
                tradeTally(buyer.getStrategy());
                tradeTally(seller.getStrategy());

            } else {
                TradingCycle.setAgentComplete(true);
                exchangeLock = false;
                notifyAll();
            }
        }
        return complete;
    }

    public static void addLog(String log) { signalLog.add(log); } //add signal log of sentiment from the mutate() function in TradingCycle

    /**
     * prints all signal logs created
     */
    public static void printLog() {
        for (int i = 0; i < signalLog.size() - 1; i++) {
            System.out.println(signalLog.get(i));
        }
    }

    /**
     * adds up all trades made by each strategy
     * @param chance the agent's strategy number
     */
    private void tradeTally(int chance) {
        if (chance == 2) {
            sentCount += 1;
        } else if (chance == 3) {
            offerCount += 1;
        } else if (chance == 4) {
            rsiCount += 1;
        } else if (chance == 5) {
            rsi10Count += 1;
        } else if (chance == 6) {
            bothCount += 1;
        } else if (chance == 7) {
            vwapCount += 1;
        } else if (chance == 8) {
            momCount += 1;
        } else if (chance == 9) {
            vwapMRCount += 1;
        } else {
            defaultCount += 1;
        }
    }

    /**
     * Resets all mutable static and singleton-instance state on this class.
     * For test fixtures only.
     */
    public static void resetForTest() {
        Exchange e = exchange;
        e.goods.clear();
        e.avg20.clear();
        e.lastAvg = 0;
        e.priceCheck = 0;
        liveChart = null;
        liveActive = false;
        liveForced = false;
        lastPrice = 0;
        defaultCount = 0;
        sentCount = 0;
        momCount = 0;
        vwapCount = 0;
        vwapMRCount = 0;
        rsiCount = 0;
        rsi10Count = 0;
        bothCount = 0;
        offerCount = 0;
        round = 0;
        newRound = false;
        roundFinalPrice.clear();
        roundPrice = 0;
        rsiList.clear();
        rsi = 0;
        rsiPList.clear();
        rsiP = 0;
        signalLog.clear();
        signalLogging = false;
        sentimentList.clear();
        vwapList.clear();
        exchangeLock = false;
    }
}
