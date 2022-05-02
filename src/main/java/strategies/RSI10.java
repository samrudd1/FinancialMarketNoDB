package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * similar to the RSI class, but uses the RSI 10 value
 * the RSI 10 value uses increments of 10 between rounds used in the equation rather than 1
 * this allows the value to represent strength over longer periods of time
 * this includes the last 140 rounds
 * @version 1.0
 * @since 13/04/22
 * @author github.com/samrudd1
 */
public class RSI10 extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public RSI10(Agent agent, TradingCycle tc, int roundNum) {
        super(agent, tc, roundNum);
        this.agent = agent;
        this.tc = tc;
        this.roundNum = roundNum;
    }

    /**
     * runs algorithm on independent thread
     */
    @SneakyThrows
    @Override
    public synchronized void run() {
        float price = Good.getPrice();
        float rsi = Exchange.getRsiP(); //RSI 10 value

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if ((rsi >= 0) && (rsi <= 100)) { //checks RSI 10 value is valid
            if (rsi < 30) { //checks if RSI is over-sold
                if (agent.getFunds() > price) {
                    double tradeMult = 0.4 + ((30 - rsi) * 0.02); //calculates how much to buy based on the value, the lower the value, the better the buy opportunity
                    Offer offer = Exchange.getInstance().getGoods().get(0).getLowestAskOffer();
                    if (offer != null) {
                        int wantToBuy = (int) Math.floor((agent.getFunds() / offer.getPrice()) * tradeMult);
                        if (offer.getNumOffered() < wantToBuy) {
                            wantToBuy = offer.getNumOffered();
                        }
                        if ((wantToBuy > 0) && (agent.getId() != offer.getOfferMaker().getId()) && (offer.getPrice() < (price * 1.03))) {
                            //buys shares from the lowest ask
                            boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, wantToBuy, tc, roundNum);
                            if (!success) {
                                System.out.println("trade execution failed");
                            }
                        }
                    }
                }

            } else if (rsi > 70) { //checks if RSI is over-bought
                if (agent.getGoodsOwned().size() > 0) {
                    double tradeMult = 0.4 + ((rsi - 70) * 0.02); //calculates how much to sell based on the value, the higher the value, the better the sell opportunity
                    Offer offer = Exchange.getInstance().getGoods().get(0).getHighestBidOffer();
                    if (offer != null) {
                        int offering = (int) Math.floor((agent.getGoodsOwned().get(0).getNumAvailable() * tradeMult));
                        if (offer.getNumOffered() < offering) {
                            offering = offer.getNumOffered();
                        }
                        if ((offering > 0) && (agent.getId() != offer.getOfferMaker().getId()) && (offer.getPrice() > (price * 0.98))) {
                            //sells shares to the highest bid
                            boolean success = Exchange.getInstance().execute(offer.getOfferMaker(), agent, offer, offering, tc, roundNum);
                            if (!success) {
                                System.out.println("trade execution failed");
                            }
                        }
                    }
                }
            }
        }

        agent.addValue(Good.getPrice()); //tracks portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }
}