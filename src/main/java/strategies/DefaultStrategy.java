package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

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
        float random = (float) Math.random();
        float lowestAsk = Exchange.getInstance().getGoods().get(0).getLowestAsk();
        float highestBid = Exchange.getInstance().getGoods().get(0).getHighestBid();
        float price = Exchange.getInstance().getPriceCheck();

        if (random > 0.9) {
            agent.changeTargetPrice();
        }

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);
        cleanOffers(agent, price); //clears offers that are far away from the current price

        if (agent.getGoodsOwned().size() > 0) { //if has shares
            int offering = (int) Math.round((float) agent.getGoodsOwned().get(0).getNumAvailable() * 0.25); //finds the number of shares to sell
            if (offering > 1000) {
                offering = 1000;
            }
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
            int purchaseLimit = (int) Math.floor(((agent.getFunds() / price) * 0.25)); //finds the number of shares to buy
            if (purchaseLimit > 1000) {
                purchaseLimit = 1000;
            }
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

        if (agent.getGoodsOwned().size() > 0) {
            if ((agent.getTargetPrice() < highestBid) && (highestBid != 0)) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getHighestBidOffer(); //gets offer of the highest bid
                if (offer != null) {
                    if (!(offer.getOfferMaker().getName().equalsIgnoreCase(agent.getName()))) { //ensures agent is not the buyer
                        int offering = (int) Math.floor(agent.getGoodsOwned().get(0).getNumAvailable() * 0.25); //finds how many shares to sell
                        if (offer.getNumOffered() < offering) {
                            offering = offer.getNumOffered();
                        }
                        if (offer.getPrice() > (price * 0.98)) {
                            if ((offering > 0) && (agent.getId() != offer.getOfferMaker().getId())) {
                                //sells to the highest bid if the offer is close to the last traded price
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

        if (agent.getFunds() > price) {
            if ((lowestAsk != 99999) && (lowestAsk < agent.getTargetPrice())) {
                Offer offer = Exchange.getInstance().getGoods().get(0).getLowestAskOffer(); //gets offer of the lowest ask
                if (offer != null) {
                    if (!(offer.getOfferMaker().getName().equals(agent.getName()))) { //ensures agent is not the seller
                        int wantToBuy = (int) Math.floor(((agent.getFunds() / offer.getPrice()) * 0.25)); //finds how many shares to buy
                        if (offer.getNumOffered() < wantToBuy) {
                            wantToBuy = offer.getNumOffered();
                        }
                        if (offer.getPrice() < (price * 1.03)) {
                            if ((wantToBuy > 0) && (agent.getId() != offer.getOfferMaker().getId())) {
                                //buys from the lowest ask if the offer is close to the last traded price
                                boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, wantToBuy, tc, roundNum);
                                if (!success) {
                                    System.out.println("trade execution failed");
                                }
                            }
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

        agent.addValue(Good.getPrice()); //keeps track of portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }
}