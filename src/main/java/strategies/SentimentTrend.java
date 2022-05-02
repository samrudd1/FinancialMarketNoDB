package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * trades based on the sentiment value that affects the target price of other strategies
 * this allows the strategy to trade before an expected move in price
 * @version 1.0
 * @since 10/03/22
 * @author github.com/samrudd1
 */
public class SentimentTrend extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public SentimentTrend(Agent agent, TradingCycle tc, int roundNum) {
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
        float price = Exchange.getInstance().getPriceCheck();
        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if (Agent.getSentiment() > 21) { //if good positive sentiment
            if (agent.getFunds() > price) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getLowestAskOffer();
                if (offer != null) {
                    int wantToBuy;
                    wantToBuy = (int) Math.floor((agent.getFunds() / offer.getPrice()) * 0.5); //finds how much to buy
                    if (offer.getNumOffered() < wantToBuy) {
                        wantToBuy = offer.getNumOffered();
                    }
                    if (offer.getPrice() < (price * 1.02)) {
                        if ((wantToBuy > 0) && (agent.getId() != offer.getOfferMaker().getId())) {
                            //buys from lowest ask if it is close to the last traded price
                            boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, wantToBuy, tc, roundNum);
                            if (!success) {
                                System.out.println("trade execution failed");
                            }
                        }
                    }
                }
            }

        } else  if (Agent.getSentiment() < 18){ //if negative sentiment
            if (agent.getGoodsOwned().size() > 0) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getHighestBidOffer();
                if (offer != null) {
                    int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable());
                    if (offering > 5) {
                        offering = (int) Math.floor((agent.getGoodsOwned().get(0).getNumAvailable() * 0.5)); //finds how much to sell
                    }
                    if (offer.getNumOffered() < offering) {
                        offering = offer.getNumOffered();
                    }
                    if (offer.getPrice() > (price * 0.98)) {
                        if (offering > 0) {
                            //sells to highest bid if it is close to last traded price
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
