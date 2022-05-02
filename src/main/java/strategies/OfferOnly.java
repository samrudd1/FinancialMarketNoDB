package strategies;

import agent.Agent;
import good.Good;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * effectively just the offer part of the default strategy
 * used to make the order book denser
 * @version 1.0
 * @since 26/04/22
 * @author github.com/samrudd1
 */
public class OfferOnly extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public OfferOnly(Agent agent, TradingCycle tc, int roundNum) {
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
        }

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);
        cleanOffers(agent, price);

        if (agent.getGoodsOwned().size() > 0) {
            if (!agent.getPlacedAsk()) {
                if ((agent.getTargetPrice() > highestBid) && (agent.getTargetPrice() < (price * 1.2))) {
                    int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable() * 0.5);
                    if (offering > 1000) {
                        offering = 1000;
                    }
                    if (offering > 0) {
                        try {
                            //places ask at target price above the highest bid
                            createAsk(agent.getTargetPrice(), agent.getGoodsOwned().get(0), offering);
                        } catch (InterruptedException e) {
                            System.out.println("creating ask threw an error");
                        }
                    }
                }
            }
        }

        if (agent.getFunds() > price) {
            if (!agent.getPlacedBid()) {
                if ((agent.getTargetPrice() < lowestAsk) && (agent.getTargetPrice() > (price * 0.8))) {
                    int purchaseLimit = (int) Math.floor((agent.getFunds() / price) * 0.5);
                    if (purchaseLimit > 1000) {
                        purchaseLimit = 1000;
                    }
                    if (purchaseLimit > 0) {
                        try {
                            //places a bid at target price below the lowest ask
                            createBid(agent.getTargetPrice(), Exchange.getInstance().getGoods().get(0), purchaseLimit);
                        } catch (InterruptedException e) {
                            System.out.println("Creating bid threw an error");
                        }
                    }
                }
            }
        }

        //if the agent's target price is far from the current price, then it is moved closer so it can trade again
        if ((agent.getTargetPrice() > (price * 2)) || (agent.getTargetPrice() < (price * 0.5))) {
            agent.setTargetPrice(price);
            agent.changeTargetPrice();
        }

        agent.addValue(Good.getPrice()); //tracks portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }
}