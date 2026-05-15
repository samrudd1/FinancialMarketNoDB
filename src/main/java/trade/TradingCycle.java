package trade;

import strategies.*;
import agent.Agent;
import good.Good;
import good.Offer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import session.Session;
import utilities.RandomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * TradingCycle is in charge of organising all the trading occurring on the market
 * @version 1.0
 * @since 09/03/22
 * @author github.com/samrudd1
 */
@Log
public class TradingCycle {
    @Getter private int numOfRounds; //how many rounds to complete
    @Getter @Setter public static boolean agentComplete; //used in the Initial Offering, says if an agent completed their trade
    @Getter @Setter private static boolean signalLogging; //toggles sentiment signal logs
    @Getter @Setter private static int prevTradeTotal = 0; //tracks total number of trades at the end of the last round
    @Getter @Setter private static int currTradeTotal = 0; //current number of trades
    @Getter @Setter private static boolean testMode = false; //when true, skips makeChart(), the post-run Scanner loop, and System.exit(0)
    @Getter @Setter public static int testThreadPoolSize = 0; //when > 0, overrides pool size (use 1 for deterministic single-threaded tests)
    private final ExecutorService pool = Executors.newFixedThreadPool(
            testThreadPoolSize > 0 ? testThreadPoolSize : Runtime.getRuntime().availableProcessors());

    /**
     * the backbone method of trading, calls all the other methods
     * @param numOfRounds how many rounds to run
     * @throws Exception from wait() function
     */
    public void startTrading(int numOfRounds) throws Exception {
        this.numOfRounds = numOfRounds;
        Session.setNumOfRounds(Session.getNumOfRounds()+1);
        agentComplete = false;
        synchronized (this) {
            initialOffering(); //starts the Initial Public Offering
            System.out.println("Initial offering complete...");
            //clears price history, as initial offering doesn't need to go on charts
            Good.getPriceList().clear();
            Good.getAvgPriceList().clear();
            Good.getTradeData().clear();

            //enables the live stock price chart if it's chosen
            if (Exchange.isLiveActive()) {
                Exchange.getLiveChart().setVisible(true);
                Exchange.getLiveChart().toFront();
                Exchange.getLiveChart().startUpdating();
            }

            Offer inOffer = Good.getAsk().get(0);
            inOffer.setPrice((float) (inOffer.getPrice() * 1.05)); //gets companies offer and raises price by 5% so it can make more money after IPO
            System.out.println("Starting trading between agents...");
            System.out.println();
            currTradeTotal = Good.getNumTrades();
            Thread.sleep(1000);

            for (int i = 0; i < numOfRounds; i++) {
                createTrades(i); //starts trading round between agents
                if (Exchange.getInstance().getGoods().get(0).getLowestAsk() <= 1) { //company is deemed bankrupt under £1
                    break;
                }
            }
        }
        pool.shutdown();
        if (Exchange.isLiveActive()) Exchange.getLiveChart().stopUpdating();
        if (signalLogging) { //after trading print all logs
            System.out.println();
            Exchange.printLog();
            System.out.println();
        }
        //market data output
        System.out.println("Market funds raised: " + Good.getCompany().getFunds());
        System.out.println("Starting price: " + Good.getStartingPrice());
        System.out.println("Lowest price: " + Good.getLowest());
        System.out.println("Highest price: " + Good.getHighest());
        System.out.println("Average price per share: " + Good.getVwap());
        System.out.println("Total shares traded: " + Good.getVolume());

        analyseStrategies(); //creates the output for all strategies performance

        if (!testMode) {
            dashboard.DashboardLauncher.show(); // blocks until the user closes the window
            System.out.println("Finished.");
            System.exit(0);
        }
    }

    /**
     * completes the Initial Public Offering for the company, allowing agents to buy
     * @throws InterruptedException from wait() function
     */
    private void initialOffering() throws InterruptedException {
        Offer IPO = Good.getAsk().get(0); //gets the offer
        agentComplete = true;
        log.info("There are " + IPO.getNumOffered() + " direct goods for sale at " + IPO.getPrice());
        int startingOffer = Good.getOutstandingShares();
        for (Agent agent : Session.getAgents().values()) { //cycles through all agents
            while (!agentComplete) wait(); //concurrency lock
            agentComplete = false;
            if ((IPO.getPrice() < agent.getTargetPrice()) && (IPO.getNumOffered() > 0)) { //checks if agent wants to buy shares
                int wantToBuy = (startingOffer / Session.getNumAgents());
                if (wantToBuy > 0) {
                    if (IPO.getNumOffered() >= wantToBuy) {
                        agent.CheckInitial(wantToBuy, this); //allows agent to process trade
                    } else if (IPO.getNumOffered() < wantToBuy) {
                        wantToBuy = IPO.getNumOffered();
                        agent.CheckInitial(wantToBuy, this); // allows agent to process trade
                    }
                    agentComplete = true;
                } else {
                    agentComplete = true;
                }
            } else {
                agentComplete = true;
            }
            notifyAll(); //allows next agent to trade
        }
    }

    /**
     * runs each trading round, calls strategies for agents and outputs market data
     * @param roundNum the current round number
     * @throws InterruptedException from wait() function
     */
    void createTrades(int roundNum) throws InterruptedException {
        List<Future<?>> futures = new ArrayList<>();
        for (Agent agent : Session.getAgents().values()) {
            if (agent.getId() != 1) {
                if (agent.getStrategy() == 2) {
                    if (roundNum > 10) {
                        futures.add(pool.submit(new SentimentTrend(agent, this, roundNum)));
                    }
                } else if (agent.getStrategy() == 3) {
                    futures.add(pool.submit(new OfferOnly(agent, this, roundNum)));
                } else if (agent.getStrategy() == 4) {
                    if (roundNum > 16) {
                        futures.add(pool.submit(new RSI(agent, this, roundNum)));
                    }
                } else if (agent.getStrategy() == 5) {
                    if (roundNum > 160) {
                        futures.add(pool.submit(new RSI10(agent, this, roundNum)));
                    }
                } else if (agent.getStrategy() == 6) {
                    if (roundNum > 16) {
                        final Agent a6 = agent;
                        final int rn6 = roundNum;
                        futures.add(pool.submit(() -> {
                            new RSI(a6, this, rn6).run();
                            if (rn6 > 160) new RSI10(a6, this, rn6).run();
                        }));
                    }
                } else if (agent.getStrategy() == 7) {
                    if (roundNum < 2000) {
                        futures.add(pool.submit(new VWAP(agent, this, roundNum)));
                    }
                } else if (agent.getStrategy() == 8) {
                    if (roundNum > 100) {
                        futures.add(pool.submit(new Momentum(agent, this, roundNum)));
                    }
                } else if (agent.getStrategy() == 9) {
                    if (roundNum < 2000) {
                        futures.add(pool.submit(new VWAPMeanReversion(agent, this, roundNum)));
                    }
                } else {
                    futures.add(pool.submit(new DefaultStrategy(agent, this, roundNum)));
                }
            }
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { /* strategy threw; log and continue */ }
        }
        // Centralised per-round snapshot for the dashboard. Done after all strategy
        // threads complete so funds/shares are settled, and before mutate() so any
        // newly-spawned agent's first snapshot is the round it actually joined.
        float snapPrice = Good.getPrice();
        for (Agent agent : Session.getAgents().values()) {
            if (agent.getId() == 1) continue; // company excluded
            agent.takeSnapshot(roundNum, snapPrice);
        }
        mutate(roundNum); // can adjust sentiment and add a new agent
        if (Exchange.isLiveActive()) Exchange.getLiveChart().scheduleRefresh();
        //outputs market data
        System.out.println();
        System.out.println("Round " + (roundNum + 1) + " of " + numOfRounds);
        System.out.println("Bid: " + Exchange.getInstance().getGoods().get(0).bidString());
        System.out.println("Ask: " + Exchange.getInstance().getGoods().get(0).askString());
        if (!Good.getBid().isEmpty()) {
            System.out.println("Highest bid: " + Exchange.getInstance().getGoods().get(0).getHighestBid());
        }
        if (!Good.getAsk().isEmpty()) {
            System.out.println("Lowest ask: " + Exchange.getInstance().getGoods().get(0).getLowestAsk());
        }
        System.out.println("Trades so far: " + (Good.getNumTrades()));
        System.out.println("Total shares traded: " + (int) Good.getVolume());
        if (Exchange.getRsi() > 0) {
            System.out.println("RSI  : " + Exchange.getRsi());
        }
        if (Exchange.getRsiP() > 0) {
            System.out.println("RSI 10: " + Exchange.getRsiP());
        }
        System.out.println("Overall sentiment: " + Agent.getSentiment());
        System.out.println();

        prevTradeTotal = currTradeTotal;
        currTradeTotal = Good.getNumTrades();
        if ((currTradeTotal - prevTradeTotal) == 0) { //if no trades occurred then priceCheck is adjusted to potentially allow trades to occur again
            float midDiff = (Exchange.getInstance().getGoods().get(0).getLowestAsk() - Exchange.getInstance().getGoods().get(0).getHighestBid()) / 2;
            if (midDiff < 10) {
                Exchange.getInstance().setPriceCheck((Exchange.getInstance().getGoods().get(0).getHighestBid() + midDiff));
            }
            //adjusts small amount of agents target price to encourage trading
            for (Agent agent : Session.getAgents().values()) {
                float random = (float) RandomProvider.get().nextDouble();
                if (random > 0.99) {
                    agent.setTargetPrice(Exchange.getInstance().getPriceCheck());
                    agent.changeTargetPrice();
                }
            }
        }

        //balances the sentiment system
        //if (roundNum == 250) { Agent.setSentAdjust(Agent.getSentAdjust() + 1); }
        if ((roundNum > (Agent.getRoundChange() + 1000)) && (Exchange.getInstance().getPriceCheck() < (Good.getStartingPrice() * 0.5))) {
            Agent.setSentAdjust(Agent.getSentAdjust() + 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) && (Exchange.getInstance().getPriceCheck() < (Good.getStartingPrice() * 0.1))) {
            Agent.setSentAdjust(Agent.getSentAdjust() + 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) && (Exchange.getInstance().getPriceCheck() < (Good.getStartingPrice() * 0.02))) {
            Agent.setSentAdjust(Agent.getSentAdjust() + 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) & (Exchange.getInstance().getPriceCheck() > (Good.getStartingPrice() * 2))) {
            Agent.setSentAdjust(Agent.getSentAdjust() - 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) & (Exchange.getInstance().getPriceCheck() > (Good.getStartingPrice() * 10))) {
            Agent.setSentAdjust(Agent.getSentAdjust() - 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) & (Exchange.getInstance().getPriceCheck() > (Good.getStartingPrice() * 50))) {
            Agent.setSentAdjust(Agent.getSentAdjust() - 1);
            Agent.setRoundChange(roundNum);
        }
    }

    /**
     * calls the analysis() method for each strategy to output their performance
     */
    private void analyseStrategies() {
        analysis("default", 0, Exchange.getDefaultCount());
        analysis("offer only", 3, Exchange.getOfferCount());
        analysis("sentiment", 2, Exchange.getSentCount());
        analysis("RSI", 4, Exchange.getRsiCount());
        analysis("RSI 10", 5, Exchange.getRsi10Count());
        analysis("both RSI", 6, Exchange.getBothCount());

        if (Agent.isVolatility()) { //these methods are only used when the user doesn't remove them when asked in RunMarket
            analysis("momentum", 8, Exchange.getMomCount());
            analysis("VWAP", 7, Exchange.getVwapCount());
            analysis("VWAP mean reversion", 9, Exchange.getVwapMRCount());
        }
        System.out.println();
    }
    private void analysis(String name, int id, float count) {
        //has a low, medium and high version of each variable so that strategies can be analysed with different starting capital
        //variables include total agents, final value, starting value and percentage change
        float lowNum = 0;
        float totalNum = 0;
        float highNum = 0;
        float lowValue = 0;
        float avgValue = 0;
        float highValue = 0;
        float lowPerc;
        float percent;
        float highPerc;
        float totalPerc;
        float lowStart = 0;
        float start = 0;
        float highStart = 0;
        for (Agent agent : Session.getAgents().values()) {
            if (agent.getStrategy() == id) {
                if ((agent.getStartingFunds() > 250000) && (agent.getStartingFunds() < 2000000)) { //middle group
                    avgValue *= totalNum;
                    float value = agent.getFunds();
                    if (!agent.getGoodsOwned().isEmpty()) {
                        value += (agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice);
                    }
                    start *= totalNum;
                    start += agent.getStartingFunds();
                    avgValue += value;
                    totalNum += 1;
                    start = start / totalNum;
                    avgValue = avgValue / totalNum;
                } else if (agent.getStartingFunds() <= 250000) { //low group
                    lowValue *= lowNum;
                    float value = agent.getFunds();
                    if (!agent.getGoodsOwned().isEmpty()) {
                        value += (agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice);
                    }
                    lowStart *= lowNum;
                    lowStart += agent.getStartingFunds();
                    lowValue += value;
                    lowNum += 1;
                    lowStart = lowStart / lowNum;
                    lowValue = lowValue / lowNum;
                } else if (agent.getStartingFunds() >= 2000000) { //high group
                    highValue *= highNum;
                    float value = agent.getFunds();
                    if (!agent.getGoodsOwned().isEmpty()) {
                        value += (agent.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice);
                    }
                    highStart *= highNum;
                    highStart += agent.getStartingFunds();
                    highValue += value;
                    highNum += 1;
                    highStart = highStart / highNum;
                    highValue = highValue / highNum;
                }
            }
        }
        percent = avgValue / start;
        percent *= 100;
        percent -= 100;

        lowPerc = lowValue / lowStart;
        lowPerc *= 100;
        lowPerc -= 100;

        highPerc = highValue / highStart;
        highPerc *= 100;
        highPerc -= 100;

        totalPerc = (lowValue + avgValue + highValue) / (lowStart + start + highStart);
        totalPerc *= 100;
        totalPerc -= 100;

        //outputs results
        System.out.println();
        System.out.println("Total number agents using " + name + " strategy: " + (totalNum + lowNum + highNum));
        System.out.println("Total percentage change for " + name + " strategy with all agents: " + totalPerc + "%");
        System.out.println("Average percentage change for " + name + " strategy with lower starting value   : " + lowPerc + "%");
        System.out.println("Average percentage change for " + name + " strategy with average starting value : " + percent + "%");
        System.out.println("Average percentage change for " + name + " strategy with high starting value    : " + highPerc + "%");
        System.out.println("Total trades by agents with " + name + " strategy: " + count);
    }


    /**
     * can change agent sentiment and add new agents to the market at random, ran each round
     * @param roundNum current round number
     */
    private void mutate(int roundNum) {
        Random rand = RandomProvider.get();
        int chance = rand.nextInt(1000); //creates random number up to 1000
        if (chance > 900) {
            Agent.setSentiment(17);
        }
        if (chance > 950) {
            Agent.setSentiment(16);
        }
        if (chance > 995) {
            Agent.setSentiment(15);
            if (signalLogging) {
                //creates sentiment signal log
                Exchange.addLog("\u001B[31m" + "Round " + roundNum + ": Strong Negative Sentiment." + "\u001B[0m");
            }
        }
        if (chance < 100) {
            Agent.setSentiment(23);
        }
        if (chance < 50) {
            Agent.setSentiment(24);
        }
        if (chance < 5) {
            Agent.setSentiment(26);
            if (signalLogging) {
                //creates sentiment signal log
                Exchange.addLog("\u001B[32m" + "Round " + roundNum + ": Strong Positive Sentiment." + "\u001B[0m");
            }
        }
        if ((chance > 350) && (chance < 400)) {
            Agent.setSentiment(18);
        }
        if ((chance > 400) && (chance < 450)) {
            Agent.setSentiment(19);
        }
        if ((chance > 450) && (chance < 550)) {
            Agent.setSentiment(20);
        }
        if ((chance > 550) && (chance < 600)) {
            Agent.setSentiment(21);
        }
        if ((chance > 600) && (chance < 650)) {
            Agent.setSentiment(22);
        }
        if ((chance % 100) == 0) {
            //adds new agent to the market
            log.info("Adding new Agent to market.");
            new Agent();
            Session.setNumAgents(Session.getNumAgents() + 1);
        }
        // Capture per-round sentiment + VWAP for the post-run dashboard.
        // Invoked exactly once per round (mutate is called once per createTrades).
        Exchange.addSentimentTick(Agent.getSentiment());
        Exchange.addVwapTick(Good.getVwap());
    }

    /**
     * Resets all mutable static state on this class. For test fixtures only.
     */
    public static void resetForTest() {
        agentComplete = false;
        signalLogging = false;
        prevTradeTotal = 0;
        currTradeTotal = 0;
        testMode = false;
        testThreadPoolSize = 0;
    }
}
