package strategies;

import agent.Agent;
import good.Good;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * similar to the default class, but uses only offers, but can place them very frequently to allow it to trade a lot
 * @version 1.0
 * @since 29/04/21
 * @author github.com/samrudd1
 */
public class AggressiveOffers extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public AggressiveOffers(Agent agent, TradingCycle tc, int roundNum) {
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
        float random = (float) Math.random();
        float lowestAsk = Exchange.getInstance().getGoods().get(0).getLowestAsk();
        float highestBid = Exchange.getInstance().getGoods().get(0).getHighestBid();
        float price = Exchange.getInstance().getPriceCheck();

        if (random > 0.9) {
            agent.changeTargetPrice();
            //allows it to place new offers at new target price
            agent.setPlacedAsk(false);
            agent.setPlacedBid(false);
        }

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if (agent.getFunds() > price) {
            if (!agent.getPlacedBid()) {
                int purchaseLimit = (int) Math.floor((agent.getFunds() / price) * 0.4); //chooses how many shares they want to buy
                if (purchaseLimit > 1000) {
                    purchaseLimit = 1000;
                }
                if ((agent.getTargetPrice() < lowestAsk) && (agent.getTargetPrice() > (price * 0.8))) {
                    if (purchaseLimit > 0) {
                        try {
                            //creates bid at their target price under the lowest ask
                            createBid(agent.getTargetPrice(), Exchange.getInstance().getGoods().get(0), purchaseLimit);
                        } catch (InterruptedException e) {
                            System.out.println("Creating bid threw an error");
                        }
                    }
                } else if (agent.getTargetPrice() > lowestAsk) {
                    if (purchaseLimit > 0) {
                        try {
                            //if their target price is above the lowest ask, but they want to buy shares, they place a very competitive bid
                            float offerPrice = (float)((Math.floor((lowestAsk - 0.01f) * 100)) * 0.01);
                            createBid(offerPrice, Exchange.getInstance().getGoods().get(0), purchaseLimit);
                        } catch (InterruptedException e) {
                            System.out.println("Creating bid threw an error");
                        }
                    }
                }
            }
        }

        if (agent.getGoodsOwned().size() > 0) {
            if (!agent.getPlacedAsk()) {
                int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable() * 0.4); //chooses how many shares they want to sell
                if (offering > 1000) {
                    offering = 1000;
                }
                if ((agent.getTargetPrice() > highestBid) && (agent.getTargetPrice() < (price * 1.2))) {
                    if (offering > 0) {
                        try {
                            //creates an ask at their target price above the highest bid
                            createAsk(agent.getTargetPrice(), agent.getGoodsOwned().get(0), offering);
                        } catch (InterruptedException e) {
                            System.out.println("creating ask threw an error");
                        }
                    }
                } else if (agent.getTargetPrice() < highestBid) {
                    if (offering > 0) {
                        try {
                            //if their target price is below the lowest ask and they want to sell shares, they place a very competitive ask
                            float offerPrice = (float)((Math.round((highestBid + 0.01f) * 100)) * 0.01);
                            createAsk(offerPrice, agent.getGoodsOwned().get(0), offering);
                        } catch (InterruptedException e) {
                            System.out.println("creating ask threw an error");
                        }
                    }
                }
            }
        }

        //if their target price is way off the current price, then it is reset and changed closer to current price so they can trade again
        if ((agent.getTargetPrice() > (price * 2)) || (agent.getTargetPrice() < (price * 0.5))) {
            cleanOffers(agent, price);
            agent.setTargetPrice(price);
            agent.changeTargetPrice();
        }

        agent.addValue(Good.getPrice()); //tracks portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }
}