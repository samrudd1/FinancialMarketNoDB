package trade;

import agent.Agent;
import good.Good;
import lombok.extern.java.Log;
import session.Session;

import java.util.Scanner;

/**
 * starts the market and asks the user for values to run the market with
 * @version 1.0
 * @since 21/12/21
 * @author github.com/samrudd1
 */
@Log
public class RunMarket {
    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);

        int startingAgentNo = 0;
        int startingGoodNo = 0;
        int noOfRounds = 0;
        //asks for how many agents
        boolean pass = false;
        while (!pass) {
            log.info("Please enter the number of starting agents.");
            if (input.hasNextInt()) {
                startingAgentNo = input.nextInt();
                pass = true;
            } else {
                char forceWait = Character.toLowerCase(input.next().charAt(0));
            }
        }
        //asks for how many shares
        pass = false;
        while (!pass) {
            log.info("Please enter the number of starting goods.");
            if (input.hasNextInt()) {
                startingGoodNo = input.nextInt();
                pass = true;
            } else {
                char forceWait = Character.toLowerCase(input.next().charAt(0));
            }
        }
        //asks for how many rounds to trade
        pass = false;
        while (!pass) {
            log.info("Please enter the number of trading rounds.");
            if (input.hasNextInt()) {
                noOfRounds = input.nextInt();
                if (noOfRounds > 9) {
                    pass = true;
                } else {
                    System.out.println("Please have at least 10 rounds");
                }
            } else {
                char forceWait = Character.toLowerCase(input.next().charAt(0));
            }
        }
        //asks if they would like a live price chart
        pass = false;
        while (!pass) {
            log.info("Would you like a live stock chart? [y/n]");
            char liveChart = Character.toLowerCase(input.next().charAt(0));
            if (liveChart == 'y') {
                Exchange.setLiveActive(true);
                pass = true;
            }
            if (liveChart == 'n') {
                Exchange.setLiveActive(false);
                pass = true;
            }
        }
        //asks if they would like trading signals logged and displayed
        pass = false;
        while (!pass) {
            log.info("Would you like to see signal logs for RSI and strong sentiments? [y/n]");
            char liveChart = Character.toLowerCase(input.next().charAt(0));
            if (liveChart == 'y') {
                Exchange.setSignalLogging(true); //RSI logging
                TradingCycle.setSignalLogging(true); //Sentiment logging
                pass = true;
            }
            if (liveChart == 'n') {
                Exchange.setSignalLogging(false); //RSI logging
                TradingCycle.setSignalLogging(false); //Sentiment logging
                pass = true;
            }
        }
        //allows the user to choose strategies that trade
        pass = false;
        while (!pass) {
            log.info("Would you like to remove high volatility strategies? (Momentum and VWAP can cause large rapid price swings) [y/n]");
            char liveChart = Character.toLowerCase(input.next().charAt(0));
            if (liveChart == 'y') {
                Agent.setVolatility(false);
                pass = true;
            }
            if (liveChart == 'n') {
                Agent.setVolatility(true);
                pass = true;
            }
        }

        //sets data
        Session.setNumAgents(startingAgentNo);
        Good.setOutstandingShares(startingGoodNo);
        Good.setDirectlyAvailable(startingGoodNo);

        Agent.setSentiment(20);
        Agent.nextID = 1;

        new Good(true); //creates stock
        for(int i = 0; i<startingAgentNo; i++){
            new Agent(); //creates all agents
        }
        new TradingCycle().startTrading(noOfRounds); //starts trading of the market

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            log.info("error trying to sleep");
        }
        System.exit(0);
    }
}
