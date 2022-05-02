package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * trades when the price moves above or below the Volume Weighted Average Price
 * @version 1.0
 * @since 29/04/22
 * @author github.com/samrudd1
 */
public class VWAP extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public VWAP(Agent agent, TradingCycle tc, int roundNum) {
        super(agent, tc, roundNum);
        this.agent = agent;
        this.tc = tc;
        this.roundNum = roundNum;
    }

    /**
     * runs strategy on independent thread
     */
    @SneakyThrows
    @Override
    public synchronized void run() {
        float price = Good.getPrice();
        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if ((price > Good.getVwap()) && (!agent.getPrevPriceUp())) { //if price has moved above VWAP and the agent hasn't just bought
            if (agent.getFunds() > price) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getLowestAskOffer();
                if (offer != null) {
                    int wantToBuy;
                    wantToBuy = (int) Math.floor(agent.getFunds() / offer.getPrice());
                    if (offer.getNumOffered() < wantToBuy) {
                        wantToBuy = offer.getNumOffered();
                    }
                    if (offer.getPrice() < (price * 1.01)) {
                        if ((wantToBuy > 0) && (agent.getId() != offer.getOfferMaker().getId())) {
                            agent.setPrevPriceUp(true); //stops the agent buying consecutively, chasing the trend
                            //buys from the lowest ask if it is close to the last traded price
                            boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, wantToBuy, tc, roundNum);
                            if (!success) {
                                System.out.println("trade execution failed");
                            }
                        }
                    }
                }
            }

        } else  if ((price < Good.getVwap()) && (agent.getPrevPriceUp())) { //if price has moved below VWAP and the agent hasn't just sold
            if (agent.getGoodsOwned().size() > 0) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getHighestBidOffer();
                if (offer != null) {
                    int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable());
                    if (offer.getNumOffered() < offering) {
                        offering = offer.getNumOffered();
                    }
                    if (offer.getPrice() > (price * 0.99)) {
                        if (offering > 0) {
                            agent.setPrevPriceUp(false); //stops the agent selling consecutively, chasing the trend
                            //sells to the highest bid if it is close to the last traded price
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