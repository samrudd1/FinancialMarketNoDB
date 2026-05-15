package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * Mean-reversion around the rolling VWAP: buys when price has fallen
 * meaningfully below fair value (bargain hunting), sells when it has risen
 * meaningfully above (profit taking).
 *
 * <p>The opposite signal-direction of {@link VWAP}. Both variants exist so
 * the population is more diverse — they will tend to take the other side of
 * each other's trades, which is exactly the dynamic a healthy market needs.
 *
 * @author github.com/samrudd1
 */
public class VWAPMeanReversion extends AbstractStrategy implements Runnable {
    private static final int VWAP_WINDOW = 50;
    private static final float DEV_THRESHOLD = 0.005f;
    private static final float DEV_SATURATION = 0.05f;
    private static final int POSITION_CAP = 5;
    private static final float MAX_TRADE_FRACTION = 0.3f;

    Agent agent;
    TradingCycle tc;
    int roundNum;

    public VWAPMeanReversion(Agent agent, TradingCycle tc, int roundNum) {
        super(agent, tc, roundNum);
        this.agent = agent;
        this.tc = tc;
        this.roundNum = roundNum;
    }

    @SneakyThrows
    @Override
    public synchronized void run() {
        float price = Good.getPrice();
        while (agent.getAgentLock()) wait();
        agent.setAgentLock(true);
        try {
            cleanOffers(agent, price);

            float vwap = Good.getRollingVwap(VWAP_WINDOW);
            if (vwap <= 0f) vwap = price;
            float dev = (price - vwap) / vwap;
            Good good = Exchange.getInstance().getGoods().get(0);

            // Price below VWAP → buy the dip
            if (dev <= -DEV_THRESHOLD
                    && agent.getVwapMRPosition() < POSITION_CAP
                    && agent.getFunds() > price) {
                float strength = Math.min(-dev / DEV_SATURATION, 1.0f);
                int wantToBuy = sizeBuy(agent.getFunds(), price, strength, MAX_TRADE_FRACTION);
                if (wantToBuy > 0) {
                    boolean filled = tryTakeAsk(good, wantToBuy, price * 1.01f);
                    if (filled) {
                        int p = agent.getVwapMRPosition();
                        agent.setVwapMRPosition(p > 0 ? p + 1 : 1);
                    } else if (agent.getBidsPlaced().isEmpty()) {
                        // Only post a passive limit if we don't already have one resting.
                        // This strategy fires every tick the price stays below VWAP — without
                        // this guard, each tick would lock another slice of cash in a NEW bid
                        // at a slightly different price. Even though the mean-reversion thesis
                        // is sound, stacking many bids while waiting for the bargain to fill
                        // ties up most of the agent's cash and leaves it unable to act on a
                        // genuinely better entry if one appears.
                        createBid(roundTo2(Math.min(price * 0.999f, good.getLowestAsk() - 0.01f)), good, wantToBuy);
                    }
                }
            // Price above VWAP → take profit
            } else if (dev >= DEV_THRESHOLD
                    && agent.getVwapMRPosition() > -POSITION_CAP
                    && !agent.getGoodsOwned().isEmpty()) {
                OwnedGood owned = agent.getGoodsOwned().get(0);
                float strength = Math.min(dev / DEV_SATURATION, 1.0f);
                int offering = sizeSell(owned.getNumAvailable(), strength, MAX_TRADE_FRACTION);
                if (offering > 0) {
                    boolean filled = tryHitBid(good, offering, price * 0.99f);
                    if (filled) {
                        int p = agent.getVwapMRPosition();
                        agent.setVwapMRPosition(p < 0 ? p - 1 : -1);
                    } else if (agent.getAsksPlaced().isEmpty()) {
                        // Mirror of the bid guard above: don't stack standing asks across ticks.
                        // Without this, every tick where price stays above VWAP would lock
                        // another batch of shares in a new ask, leaving the agent with many
                        // stacked asks that fill on the way back down and prevent it from
                        // re-quoting at a more attractive price.
                        createAsk(roundTo2(Math.max(price * 1.001f, good.getHighestBid() + 0.01f)), owned, offering);
                    }
                }
            }

            agent.addValue(Good.getPrice());
        } finally {
            agent.setAgentLock(false);
            notify();
        }
    }

    private boolean tryTakeAsk(Good good, int wantToBuy, float maxPrice) throws InterruptedException {
        Offer offer = good.getLowestAskOffer();
        if (offer == null) return false;
        if (offer.getPrice() > maxPrice) return false;
        if (agent.getId() == offer.getOfferMaker().getId()) return false;
        int amt = Math.min(wantToBuy, offer.getNumOffered());
        if (amt <= 0) return false;
        return Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, amt, tc, roundNum);
    }

    private boolean tryHitBid(Good good, int offering, float minPrice) throws InterruptedException {
        Offer offer = good.getHighestBidOffer();
        if (offer == null) return false;
        if (offer.getPrice() < minPrice) return false;
        if (agent.getId() == offer.getOfferMaker().getId()) return false;
        int amt = Math.min(offering, offer.getNumOffered());
        if (amt <= 0) return false;
        return Exchange.getInstance().execute(offer.getOfferMaker(), agent, offer, amt, tc, roundNum);
    }

    private static float roundTo2(float v) {
        return ((float) Math.round(v * 100f)) / 100f;
    }
}
