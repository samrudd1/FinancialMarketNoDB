package strategies;

import agent.Agent;
import good.Good;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;
import utilities.RandomProvider;

/**
 * first strategy created, can place offers and trade with other agent's offers
 * @version 1.0
 * @since 17/01/22
 * @author github.com/samrudd1
 */
public class DefaultStrategy extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public DefaultStrategy(Agent agent, TradingCycle tc, int roundNum) {
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
        float random = (float) RandomProvider.get().nextDouble();
        float lowestAsk = Exchange.getInstance().getGoods().get(0).getLowestAsk();
        float highestBid = Exchange.getInstance().getGoods().get(0).getHighestBid();
        float price = Exchange.getInstance().getPriceCheck();

        if (random > 0.9) {
            agent.changeTargetPrice();
        }

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);
        cleanOffers(agent, price); //clears offers that are far away from the current price

        if (!agent.getGoodsOwned().isEmpty()) { //if has shares
            int offering = (int) Math.round((float) agent.getGoodsOwned().get(0).getNumAvailable() * 0.1); //finds the number of shares to sell
            if ((agent.getTargetPrice() > highestBid) && (agent.getTargetPrice() < (price * 1.1))) {
                if (!agent.getPlacedAsk()) {
                    if (offering > 0) {
                        try {
                            //places an ask offer at their target price above the highest bid
                            createAsk(agent.getTargetPrice(), agent.getGoodsOwned().get(0), offering);
                        } catch (InterruptedException e) {
                            System.out.println("creating ask threw an error");
                        }
                    }
                }
            }
        }

        if (agent.getFunds() > price) {
            int purchaseLimit = (int) Math.floor(((agent.getFunds() / price) * 0.1)); //finds the number of shares to buy
            if ((agent.getTargetPrice() < lowestAsk) && (agent.getTargetPrice() > (price * 0.9))) {
                if (!agent.getPlacedBid()) {
                    if (purchaseLimit > 0) {
                        try {
                            //places a bid offer at their target price under the lowest ask
                            createBid(agent.getTargetPrice(), Exchange.getInstance().getGoods().get(0), purchaseLimit);
                        } catch (InterruptedException e) {
                            System.out.println("Creating bid threw an error");
                        }
                    }
                }
            }
        }

        Good good = Exchange.getInstance().getGoods().get(0);

        if (!agent.getGoodsOwned().isEmpty()) {
            if ((agent.getTargetPrice() < highestBid) && (highestBid != 0)) {
                int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable() * 0.25); //finds how many shares to sell
                // Sweep down the bid book stopping at the agent's target — every bid above target
                // is acceptable to this agent regardless of how it compares to current market.
                sweepSell(good, agent.getTargetPrice(), offering);
            }
        }

        if (agent.getFunds() > price) {
            if ((lowestAsk != 99999) && (lowestAsk < agent.getTargetPrice())) {
                int wantToBuy = (int) Math.floor(((agent.getFunds() / price) * 0.25)); //finds how many shares to buy
                // Sweep up the ask book stopping at the agent's target — every ask below target
                // is a buy this agent is happy to take.
                sweepBuy(good, agent.getTargetPrice(), wantToBuy);
            }
        }

        //if the agent's target price is far from the current price, then it is moved closer so it can trade again
        if ((agent.getTargetPrice() > (price * 1.33)) || (agent.getTargetPrice() < (price * 0.75))) {
            agent.setTargetPrice(price);
            agent.changeTargetPrice();
        }

        agent.addValue(Good.getPrice()); //keeps track of portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }
}