package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

/**
 * Trend-following on a rolling rate-of-change of round-closing prices.
 * Buys into sustained uptrends, sells into sustained downtrends.
 *
 * <p>Sizing scales with signal strength rather than going all-in, and the
 * agent's {@code momentumPosition} counter caps how many same-direction
 * trades can stack before the agent must reverse — letting it scale into a
 * strong move without infinitely chasing.
 *
 * <p>Order routing tries the taker side first (hits an existing offer near
 * the current price); if no qualifying offer is available it falls back to a
 * passive limit order via {@code createBid}/{@code createAsk}.
 *
 * @version 2.0
 * @since 10/03/22
 * @author github.com/samrudd1
 */
public class Momentum extends AbstractStrategy implements Runnable {
    /** Rolling window (in rounds) over which we measure rate-of-change. */
    private static final int ROC_WINDOW = 5;
    /** Rolling window (in trades) for the fair-value guard. */
    private static final int VWAP_WINDOW = 50;
    /** Minimum |ROC| to act on. Below this the move is noise. */
    private static final float ROC_THRESHOLD = 0.02f;
    /** ROC at which signal strength saturates at 1.0. */
    private static final float ROC_SATURATION = 0.10f;
    /** Maximum consecutive same-direction trades before reversal is required. */
    private static final int POSITION_CAP = 5;
    /** Max fraction of free cash / available shares a single trade may consume. */
    private static final float MAX_TRADE_FRACTION = 0.4f;

    Agent agent;
    TradingCycle tc;
    int roundNum;

    public Momentum(Agent agent, TradingCycle tc, int roundNum) {
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

            float roc = Exchange.getPriceRoc(ROC_WINDOW);
            float vwapGuard = Good.getRollingVwap(VWAP_WINDOW);
            if (vwapGuard <= 0f) vwapGuard = price;
            Good good = Exchange.getInstance().getGoods().get(0);

            if (roc >= ROC_THRESHOLD
                    && agent.getMomentumPosition() < POSITION_CAP
                    && price < vwapGuard * 1.5f
                    && agent.getFunds() > price) {
                float strength = Math.min(roc / ROC_SATURATION, 1.0f);
                int wantToBuy = sizeBuy(agent.getFunds(), price, strength, MAX_TRADE_FRACTION);
                if (wantToBuy > 0) {
                    boolean filled = tryTakeAsk(good, wantToBuy, price * 1.005f);
                    if (filled) {
                        int p = agent.getMomentumPosition();
                        agent.setMomentumPosition(p > 0 ? p + 1 : 1);
                    } else if (agent.getBidsPlaced().isEmpty()) {
                        // Only post a passive limit if we don't already have one resting.
                        // This strategy fires every tick the rolling ROC stays positive —
                        // without this guard, each tick would lock another slice of cash in
                        // a NEW bid at a slightly different (trended-against) price.
                        // Production runs showed dozens of stacked bids all filling near the
                        // trend peak, baking in a guaranteed mark-to-market loss when price
                        // reverted. The position cap doesn't help here because it only
                        // increments on filled takers, not on resting fallback bids.
                        createBid(roundTo2(Math.min(price * 0.999f, good.getLowestAsk() - 0.01f)), good, wantToBuy);
                    }
                }
            } else if (roc <= -ROC_THRESHOLD
                    && agent.getMomentumPosition() > -POSITION_CAP
                    && !agent.getGoodsOwned().isEmpty()
                    && price > vwapGuard * 0.7f) {
                OwnedGood owned = agent.getGoodsOwned().get(0);
                float strength = Math.min(-roc / ROC_SATURATION, 1.0f);
                int offering = sizeSell(owned.getNumAvailable(), strength, MAX_TRADE_FRACTION);
                if (offering > 0) {
                    boolean filled = tryHitBid(good, offering, price * 0.995f);
                    if (filled) {
                        int p = agent.getMomentumPosition();
                        agent.setMomentumPosition(p < 0 ? p - 1 : -1);
                    } else if (agent.getAsksPlaced().isEmpty()) {
                        // Mirror of the bid guard above: don't stack standing asks across ticks.
                        // Without this, every tick where ROC stays negative would lock another
                        // batch of shares in a new ask, leaving the agent with many stacked
                        // asks that all fill near the trough and crystallise the sell-low side
                        // of any reversal.
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

    /** Try to fill {@code wantToBuy} by hitting the lowest ask, but only if its price is ≤ {@code maxPrice}. */
    private boolean tryTakeAsk(Good good, int wantToBuy, float maxPrice) throws InterruptedException {
        Offer offer = good.getLowestAskOffer();
        if (offer == null) return false;
        if (offer.getPrice() > maxPrice) return false;
        if (agent.getId() == offer.getOfferMaker().getId()) return false;
        int amt = Math.min(wantToBuy, offer.getNumOffered());
        if (amt <= 0) return false;
        return Exchange.getInstance().execute(agent, offer.getOfferMaker(), offer, amt, tc, roundNum);
    }

    /** Try to fill {@code offering} by hitting the highest bid, but only if its price is ≥ {@code minPrice}. */
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
