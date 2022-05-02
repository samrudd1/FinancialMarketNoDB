package strategies;

import agent.Agent;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * high frequency algorithm that was experimented with
 * consistently returned profits of 1-3%, but caused the bid-ask spread to become quite large
 * also was meant to trade all the time, but would go inactive for periods despite the while loop
 * @version 1.0
 * @since 10/03/22
 * @author github.com/samrudd1
 */
public class HighFrequency extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    static int roundNum;
    int finalRound;

    public HighFrequency(Agent agent, TradingCycle tc, int roundNum, int finalRound) {
        super(agent, tc, roundNum);
        this.agent = agent;
        this.tc = tc;
        HighFrequency.roundNum = roundNum;
        this.finalRound = finalRound;
    }

    @SneakyThrows
    @Override
    public synchronized void run() {
        Good good = Exchange.getInstance().getGoods().get(0);
        float highestBid;
        float secondHighestBid;
        float lowestAsk;
        float secondLowestAsk;
        while (tc.getNumOfRounds() < (finalRound - 1)) { //tries to be active constantly

            if (agent.getFunds() > Exchange.lastPrice) {
                lowestAsk = good.getLowestAsk();
                secondLowestAsk = good.getSecondLowestAsk();
                if ((lowestAsk != 99999) && (secondLowestAsk != 99999)) { //checks the offers exist
                    if ((lowestAsk - secondLowestAsk) > 0.02) { //checks if they are more than 2p apart
                        Offer offer = good.getLowestAskOffer();
                        if ((agent.getFunds() * 0.5) > (offer.getPrice() * offer.getNumOffered())) {//only wants to use max half total funds
                            if (!(offer.getOfferMaker().getName().equalsIgnoreCase(agent.getName()))) {//checks agent is not the seller
                                if (offer.getNumOffered() > 2) {
                                    //buys all of lowest ask offer
                                    boolean success = Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, offer.getNumOffered(), tc, roundNum);
                                    if (!success) {
                                        System.out.println("trade execution failed");
                                    } else {
                                        //places new offer just below the second lowest offer
                                        int numShares = offer.getNumOffered();
                                        try {
                                            createAsk((float) (secondLowestAsk - 0.01), agent.getGoodsOwned().get(0), numShares);
                                        } catch (InterruptedException e) {
                                            System.out.println("creating ask threw an error");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (agent.getGoodsOwned().size() > 0) {
                highestBid = good.getHighestBid();
                secondHighestBid = good.getSecondHighestBid();
                if ((highestBid != 0) && (secondHighestBid != 0)) { //checks both offers exist
                    if ((highestBid - secondHighestBid) > 0.02) { //checks gap between the offers is at least 2p
                        Offer offer = good.getHighestBidOffer();
                        if (agent.getGoodsOwned().get(0).getNumOwned() > (offer.getPrice() * offer.getNumOffered())) {
                            if (!(offer.getOfferMaker().getName().equalsIgnoreCase(agent.getName()))) { //makes sure agent is not the buyer
                                //sells all shares to the bid to remove from the order book
                                boolean success = Exchange.getInstance().execute(offer.getOfferMaker(), agent, offer, offer.getNumOffered(), tc, roundNum);
                                if (!success) {
                                    System.out.println("trade execution failed");
                                } else {
                                    //places new bid just above the second highest bid
                                    int wantToBuy = (int)Math.floor(agent.getFunds() / Exchange.lastPrice);
                                    if (wantToBuy > 1000) {
                                        wantToBuy = 500;
                                    } else {
                                        wantToBuy = offer.getNumOffered();
                                        wantToBuy = (int)Math.floor(wantToBuy * 0.5);
                                    }
                                    try {
                                        createBid((float)(secondHighestBid + 0.01), good, (wantToBuy * 2));
                                    } catch (InterruptedException e) {
                                        System.out.println("creating bid threw an error");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        agent.addValue(Good.getPrice()); //tracks portfolio value
        return;
    }
}
