package trade;

import strategies.*;
import agent.Agent;
import good.Good;
import good.Offer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import session.Session;
import utilities.CandleStickChart;
import utilities.LineChartMake;

import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

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
                Thread.sleep(1); //allows threads to catch up and finish before next round
            }
        }
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
        makeChart(); //creates all charts

        //this section allows the user to view the performance of each agent
        Scanner input = new Scanner(System.in);
        boolean pass = false;
        LineChartMake av = new LineChartMake(Exchange.getRsiPList(), "RSI 10", "Round", "RSI Value (0-100)"); // placeholder so it can be set to the chosen agent
        Agent output = new Agent();
        while (!pass) {
            log.info("enter q to quit, or you can view a chart related to an agent's performance, enter an id between 2 and " + (Agent.getNextID() - 2));
            if (input.hasNextInt()) {
                int value = input.nextInt();
                if ((value > 1) && (value < Agent.getNextID() - 1)) { //checks input is valid
                    av.setVisible(false);
                    for (Agent agent : Session.getAgents().values()) { //finds agent
                        if (agent.getId() == value) {
                            output = agent;
                        }
                    }
                    av = new LineChartMake(output.getFundData(), output.getName(), "Rounds", "Portfolio Value (£)");
                    av.setVisible(true); //creates and outputs chart

                    //calculates performance of the agent
                    float finalVal = output.getFunds();
                    if (output.getGoodsOwned().size() > 0) {
                        finalVal += (output.getGoodsOwned().get(0).getNumOwned() * Exchange.lastPrice);
                    }
                    float startingVal = output.getStartingFunds();
                    float percent =  finalVal / startingVal;
                    percent *= 100;
                    percent -= 100;
                    //outputs performance
                    System.out.println(output.getName() + " starting value: " + startingVal);
                    System.out.println(output.getName() + " final value: " + finalVal);
                    System.out.println(output.getName() + " percentage change: " + percent + "%");
                }
            } else {
                //checks if the user wants to quit
                char qCheck = Character.toLowerCase(input.next().charAt(0));
                if (qCheck == 'q') {
                    pass = true;
                }
            }
        }

        System.out.println("Finished.");
        System.exit(0);
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
    private void createTrades(int roundNum) throws InterruptedException {
        for (Agent agent : Session.getAgents().values()) { //cycles through all agents
            if (agent.getId() != 1) { //ignores company
                //calls all the strategies for the agents
                //if statement was faster than switch statement
                if (agent.getStrategy() == 1) {
                    if (roundNum > 100) {
                        //creates instance of strategy class and applies it to Runnable
                        //allows all agents to trade on independent thread
                        Momentum ms = new Momentum(agent, this, roundNum);
                        Runnable mt = new Thread(ms);
                        mt.run();
                    }
                } else if (agent.getStrategy() == 2) {
                    if (roundNum > 10) {
                        SentimentTrend st = new SentimentTrend(agent, this, roundNum);
                        Runnable tt = new Thread(st);
                        tt.run();
                    }
                } else if (agent.getStrategy() == 3) {
                    if (roundNum < 2000) {
                        VWAP vw = new VWAP(agent, this, roundNum);
                        Runnable wv = new Thread(vw);
                        wv.run();
                    }
                } else if (agent.getStrategy() == 4) {
                    if (roundNum > 16) {
                        RSI ve = new RSI(agent, this, roundNum);
                        Runnable ev = new Thread(ve);
                        ev.run();
                    }
                } else if (agent.getStrategy() == 5) {
                    if (roundNum > 160) {
                        RSI10 be = new RSI10(agent, this, roundNum);
                        Runnable eb = new Thread(be);
                        eb.run();
                    }
                } else if (agent.getStrategy() == 6) {
                    OfferOnly ba = new OfferOnly(agent, this, roundNum);
                    Runnable ab = new Thread(ba);
                    ab.run();
                } else if (agent.getStrategy() == 7) {
                    //combination strategy
                    if (roundNum > 16) {
                        RSI rs = new RSI(agent, this, roundNum);
                        Runnable rt = new Thread(rs);
                        rt.run();
                        if (roundNum > 160) {
                            RSI10 rp = new RSI10(agent, this, roundNum);
                            Runnable pr = new Thread(rp);
                            pr.run();
                        }
                    }
                } else if (agent.getStrategy() == 8) {
                    AggressiveOffers ag = new AggressiveOffers(agent, this, roundNum);
                    Runnable ga = new Thread(ag);
                    ga.run();
                } else if (agent.getStrategy() == 9) {
                    //combination strategy
                    if (roundNum > 100) {
                        Momentum sw = new Momentum(agent, this, roundNum);
                        Runnable ws = new Thread(sw);
                        ws.run();
                    }
                    if (roundNum < 2000) {
                        VWAP vp = new VWAP(agent, this, roundNum);
                        Runnable pv = new Thread(vp);
                        pv.run();
                    }
                } else {
                    DefaultStrategy co = new DefaultStrategy(agent, this, roundNum);
                    Runnable ts = new Thread(co);
                    ts.run();
                }
            }
        }
        mutate(roundNum); // can adjust sentiment and add a new agent
        //outputs market data
        System.out.println();
        System.out.println("Round " + (roundNum + 1) + " of " + numOfRounds);
        System.out.println("Bid: " + Exchange.getInstance().getGoods().get(0).bidString());
        System.out.println("Ask: " + Exchange.getInstance().getGoods().get(0).askString());
        if (Good.getBid().size() > 0) {
            System.out.println("Highest bid: " + Exchange.getInstance().getGoods().get(0).getHighestBid());
        }
        if (Good.getAsk().size() > 0) {
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
                float random = (float) Math.random();
                if (random > 0.99) {
                    agent.setTargetPrice(Exchange.getInstance().getPriceCheck());
                    agent.changeTargetPrice();
                }
            }
        }

        //balances the sentiment system
        if (roundNum == 250) { Agent.setSentAdjust(Agent.getSentAdjust() + 1); }
        if ((roundNum > (Agent.getRoundChange() + 1000)) && (Exchange.getInstance().getPriceCheck() < (Good.getStartingPrice() * 0.5))) {
            Agent.setSentAdjust(Agent.getSentAdjust() + 1);
            Agent.setRoundChange(roundNum);
        }
        if ((roundNum > (Agent.getRoundChange() + 1000)) & (Exchange.getInstance().getPriceCheck() > (Good.getStartingPrice() * 3))) {
            Agent.setSentAdjust(Agent.getSentAdjust() - 1);
            Agent.setRoundChange(roundNum);
        }
    }

    /**
     * calls the analysis() method for each strategy to output their performance
     */
    private void analyseStrategies() {
        analysis("default", 0, Exchange.getDefaultCount());
        analysis("offer only", 6, Exchange.getOfferCount());
        analysis("aggressive offers", 8, Exchange.getAggressiveCount());
        analysis("sentiment", 2, Exchange.getSentCount());
        analysis("RSI", 4, Exchange.getRsiCount());
        analysis("RSI 10", 5, Exchange.getRsi10Count());
        analysis("both RSI", 7, Exchange.getBothCount());

        if (Agent.isVolatility()) { //these methods are only used when the user doesn't remove them when asked in RunMarket
            analysis("momentum", 1, Exchange.getMomCount());
            analysis("VWAP", 3, Exchange.getVwapCount());
            analysis("momentum and VWAP", 9, Exchange.getMomVwapCount());
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
                    if (agent.getGoodsOwned().size() > 0) {
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
                    if (agent.getGoodsOwned().size() > 0) {
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
                    if (agent.getGoodsOwned().size() > 0) {
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
     * makes and outputs all charts
     */
    private void makeChart() {
        //finds the average of every 10 trades to cut down on chart loading time
        ArrayList<Float> avg10 = new ArrayList<>();
        float addNum = 0;
        float lowest = 9999;
        float highest = 0;
        for (int i = 0; i < Good.getAvgPriceList().size(); i++) {
            if (i % 10 == 9) {
                avg10.add((addNum / 10));
                addNum = 0;
            } else {
                addNum += Good.getAvgPriceList().get(i);
            }
        }
        for (Float aFloat : avg10) {
            if (aFloat < lowest) {
                lowest = aFloat;
            }
            if (aFloat > highest) {
                highest = aFloat;
            }
        }
        //RSI chart
        LineChartMake rs = new LineChartMake(Exchange.getRsiList(), "RSI", "Round", "RSI Value", 0, 100);
        rs.setVisible(true);
        //RSI 10 chart
        LineChartMake rp = new LineChartMake(Exchange.getRsiPList(), "RSI 10", "Round", "RSI 10 Value", 0, 100);
        rp.setVisible(true);
        //Candle Stick chart
        CandleStickChart cs = new CandleStickChart("Candle Stick Chart", Good.getTradeData());
        cs.setVisible(true);
        //stock price line chart
        LineChartMake lc = new LineChartMake(Good.getAvgPriceList(), "Stock Price", "Trades", "Price(£)", lowest, highest);
        lc.setVisible(true);
    }

    /**
     * can change agent sentiment and add new agents to the market at random, ran each round
     * @param roundNum current round number
     */
    private void mutate(int roundNum) {
        Random rand = new Random();
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
        if (chance < 6) {
            Agent.setSentiment(26);
            if (signalLogging) {
                //creates sentiment signal log
                Exchange.addLog("\u001B[32m" + "Round " + roundNum + ": Strong Positive Sentiment." + "\u001B[0m");
            }
        }
        if ((chance > 400) && (chance < 450)) {
            Agent.setSentiment(18);
        }
        if ((chance > 450) && (chance < 500)) {
            Agent.setSentiment(19);
        }
        if ((chance > 500) && (chance < 550)) {
            Agent.setSentiment(20);
        }
        if ((chance > 550) && (chance < 650)) {
            Agent.setSentiment(21);
        }
        if (chance < 10) {
            //adds new agent to the market
            log.info("Adding new Agent to market.");
            new Agent();
            Session.setNumAgents(Session.getNumAgents() + 1);
        }
    }
}
