package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * trades based off momentum in price movement
 * @version 1.0
 * @since 10/03/22
 * @author github.com/samrudd1
 */
public class Momentum extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public Momentum(Agent agent, TradingCycle tc, int roundNum) {
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
        float endPrice = Exchange.getRoundPrice();

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);
        float priceDiff = price - endPrice;
        priceDiff = priceDiff / endPrice;

        if (priceDiff != 0) { //checks for movement in price
            if ((priceDiff >= 0.03) && (priceDiff <= 0.1) && (!agent.getPrevPriceUp())) {
                if (agent.getFunds() > price) {
                    if (price < (Good.getVwap() * 1.5)) { //stops it taking risky positions far above historical prices
                        Offer offer = Exchange.getInstance().getGoods().get(0).getLowestAskOffer();
                        if (offer != null) {
                            int wantToBuy = (int) Math.floor(agent.getFunds() / offer.getPrice());
                            if (offer.getNumOffered() < wantToBuy) {
                                wantToBuy = offer.getNumOffered();
                            }
                            if (offer.getPrice() < (price * 1.005)) {
                                if ((wantToBuy > 0) && (agent.getId() != offer.getOfferMaker().getId())) {
                                    agent.setPrevPriceUp(true); //stops it buying again next, stops it chasing a movement
                                    //buys shares to follow the up-trend
                                    boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, wantToBuy, tc, roundNum);
                                    if (!success) {
                                        System.out.println("trade execution failed");
                                    }
                                }
                            }
                        }
                    }
                }

            } else if ((priceDiff <= -0.03) && (priceDiff >= -0.1) && (agent.getPrevPriceUp())) {
                if (agent.getGoodsOwned().size() > 0) {
                    if (price > (Good.getVwap() * 0.7)) { //stops it selling far below historical prices, as likely to return
                        Offer offer = Exchange.getInstance().getGoods().get(0).getHighestBidOffer();
                        if (offer != null) {
                            int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable());
                            if (offer.getNumOffered() < offering) {
                                offering = offer.getNumOffered();
                            }
                            if (offering > 0) {
                                agent.setPrevPriceUp(false); //stops it selling consecutively, so it doesn't chase a movement
                                //sells shares expecting price to drop further
                                boolean success = Exchange.getInstance().execute(offer.getOfferMaker(), agent, offer, offering, tc, roundNum);
                                if (!success) {
                                    System.out.println("trade execution failed");
                                }
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