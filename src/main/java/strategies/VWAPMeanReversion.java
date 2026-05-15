package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
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

            // Volatility-aware trigger and sizing. sigmaFrac is the rolling stddev of trade
            // prices expressed as a fraction of VWAP — i.e. one "z" worth of deviation.
            //   trigger:    |z| >= 1   (only act once price is at least one stddev from VWAP)
            //   saturation: |z| >= 2   (full-size trade at two stddevs out)
            // In quiet markets a small absolute deviation is meaningful; in noisy markets the
            // same absolute deviation is just noise and shouldn't move the strategy. Falls back
            // to the legacy 0.5% trigger / 5% saturation when there isn't enough trade history
            // for a non-zero stddev (e.g. the first few rounds of a fresh simulation).
            float stddev = Good.getRollingPriceStddev(VWAP_WINDOW);
            float sigmaFrac = (stddev > 0f && vwap > 0f) ? stddev / vwap : 0f;
            boolean haveSigma = sigmaFrac > 0f;
            float triggerThreshold = haveSigma ? sigmaFrac : DEV_THRESHOLD;
            float saturation       = haveSigma ? 2f * sigmaFrac : DEV_SATURATION;

            Good good = Exchange.getInstance().getGoods().get(0);

            // Price below VWAP → buy the dip
            if (dev <= -triggerThreshold
                    && agent.getVwapMRPosition() < POSITION_CAP
                    && agent.getFunds() > price) {
                float strength = Math.min(-dev / saturation, 1.0f);
                int wantToBuy = sizeBuy(agent.getFunds(), price, strength, MAX_TRADE_FRACTION);
                if (wantToBuy > 0) {
                    // Cap the sweep at the rolling VWAP — the mean-reversion thesis is that
                    // price will revert *to* fair value, so paying more than that defeats the
                    // strategy. The Exchange ±5%/-4% price band remains the ultimate ceiling.
                    int filledQty = sweepBuy(good, vwap, wantToBuy);
                    if (filledQty > 0) {
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
            } else if (dev >= triggerThreshold
                    && agent.getVwapMRPosition() > -POSITION_CAP
                    && !agent.getGoodsOwned().isEmpty()) {
                OwnedGood owned = agent.getGoodsOwned().get(0);
                float strength = Math.min(dev / saturation, 1.0f);
                int offering = sizeSell(owned.getNumAvailable(), strength, MAX_TRADE_FRACTION);
                if (offering > 0) {
                    // Mirror cap: don't sell *below* VWAP when reverting from above.
                    int filledQty = sweepSell(good, vwap, offering);
                    if (filledQty > 0) {
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

    private static float roundTo2(float v) {
        return ((float) Math.round(v * 100f)) / 100f;
    }
}
