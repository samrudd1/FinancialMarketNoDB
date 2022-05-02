package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * trades based on the Relative Strength Index value, a commonly used indicator
 * @version 1.0
 * @since 26/04/22
 * @author github.com/samrudd1
 */
public class RSI extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public RSI(Agent agent, TradingCycle tc, int roundNum) {
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
        float rsi = Exchange.getRsi();

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if ((rsi >= 0) && (rsi <= 100)) { //checks RSI value is valid
            if (rsi < 20) { //checks if RSI is over-sold
                if (agent.getFunds() > price) {
                    double tradeMult = 0.4 + ((20 - rsi) * 0.03); //calculates how much to buy based on the value, the lower the value, the better the buy opportunity
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

            } else if (rsi > 80) { //checks if RSI is over-bought
                if (agent.getGoodsOwned().size() > 0) {
                    double tradeMult = 0.4 + ((rsi - 80) * 0.03); //calculates how much to sell based on the value, the higher the value, the better the sell opportunity
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