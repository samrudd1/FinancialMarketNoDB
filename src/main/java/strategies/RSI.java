package strategies;

import agent.Agent;
import good.Good;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

import java.util.ArrayList;

/**
 * trades based on the Relative Strength Index value, a commonly used indicator
 * @version 1.0
 * @since 26/04/22
 * @author github.com/samrudd1
 */
public class RSI extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public RSI(Agent agent, TradingCycle tc, int roundNum) {
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
        float rsi = Exchange.getRsi();

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if ((rsi >= 0) && (rsi <= 100)) { //checks RSI value is valid
            Good good = Exchange.getInstance().getGoods().get(0);
            if (rsi < 20) { //checks if RSI is over-sold
                if (agent.getFunds() > price) {
                    double tradeMult = 0.4 + ((20 - rsi) * 0.03); //calculates how much to buy based on the value, the lower the value, the better the buy opportunity
                    int wantToBuy = (int) Math.floor((agent.getFunds() / price) * tradeMult);
                    // Cap the sweep at whichever is tighter: (a) the price at which next-round RSI
                    // would cross back up through 20 (signal self-extinguishes), or (b) the legacy
                    // 3% premium safety ceiling.
                    float rsiCap = rsiBoundaryPrice(20f, true);
                    float maxPrice = Float.isNaN(rsiCap) ? price * 1.03f : Math.min(rsiCap, price * 1.03f);
                    sweepBuy(good, maxPrice, wantToBuy);
                }

            } else if (rsi > 80) { //checks if RSI is over-bought
                if (!agent.getGoodsOwned().isEmpty()) {
                    double tradeMult = 0.4 + ((rsi - 80) * 0.03); //calculates how much to sell based on the value, the higher the value, the better the sell opportunity
                    int offering = (int) Math.floor((agent.getGoodsOwned().get(0).getNumAvailable() * tradeMult));
                    // Mirror of the buy cap: the tighter of (a) the price at which next-round RSI
                    // crosses back down through 80, or (b) the 2% discount safety floor.
                    float rsiCap = rsiBoundaryPrice(80f, false);
                    float minPrice = Float.isNaN(rsiCap) ? price * 0.98f : Math.max(rsiCap, price * 0.98f);
                    sweepSell(good, minPrice, offering);
                }
            }
        }

        agent.addValue(Good.getPrice()); //tracks portfolio value
        agent.setAgentLock(false);
        notify();
        return;
    }

    /**
     * Estimates the round-end price at which next-round RSI would equal {@code targetRsi}.
     *
     * <p>Within a round, {@code Exchange.rsi} is frozen — it's recomputed once at the first
     * trade of each round and held until the next round begins. When many RSI agents fire on
     * the same oversold/overbought signal they all read the same stale value, so without a
     * smarter cap they can collectively run the price up to the 3% safety ceiling each round,
     * trading against a signal their own activity has already neutralized.
     *
     * <p>Solving the RSI formula for the price that puts next-round RSI exactly at the threshold
     * gives a natural self-limiting cascade: each agent caps its sweep at this price, so the
     * earliest movers absorb the move and later agents in the same round find top-of-book
     * already at the cap (sweep returns 0).
     *
     * <p>Returns {@code NaN} if there's not enough history, or if the maths is degenerate
     * (e.g. all-gains over the lookback so target RSI is unreachable from this side). Callers
     * fall back to the 3% safety ceiling in those cases.
     *
     * @param targetRsi the RSI level at which the signal turns off (20 for buys, 80 for sells)
     * @param buySide   true if the agent will buy (push price up); false if selling
     */
    private static float rsiBoundaryPrice(float targetRsi, boolean buySide) {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        int n = rfp.size();
        // Match Exchange's RSI window: 14 diffs ending at index n-1, oldest at n-14.
        // Need rfp[n-15] for the dropping diff when a new round is appended.
        if (n < 15) return Float.NaN;

        float gainOld = 0f;
        float lossOld = 0f;
        for (int i = n - 1; i >= n - 14; i--) {
            float diff = rfp.get(i) / rfp.get(i - 1) - 1f;
            if (diff > 0f) gainOld += diff;
            else if (diff < 0f) lossOld += -diff;
        }
        // The diff at i = n-14 is the oldest one in the current window; it drops out when
        // the next round appends a new entry.
        float dropDiff = rfp.get(n - 14) / rfp.get(n - 15) - 1f;
        float dropGain = dropDiff > 0f ? dropDiff : 0f;
        float dropLoss = dropDiff < 0f ? -dropDiff : 0f;

        // RSI = 100 - 100/(1 + r) where r = gain/loss → r = targetRsi / (100 - targetRsi)
        float targetRatio = targetRsi / (100f - targetRsi);
        float newDiff;
        if (buySide) {
            // A buy makes new_diff > 0 → it only adds to avgGain. Solve gain/loss = targetRatio.
            float newLoss = lossOld - dropLoss;
            if (newLoss <= 0f) return Float.NaN; // no losses in window → next-round RSI = 100, target unreachable upward
            newDiff = targetRatio * newLoss - gainOld + dropGain;
            if (newDiff <= 0f) return Float.NaN; // already at or past target; caller should not sweep further
        } else {
            // A sell makes new_diff < 0 → it only adds to avgLoss.
            float newGain = gainOld - dropGain;
            if (newGain <= 0f) return Float.NaN; // no gains in window → next-round RSI = 0, target unreachable downward
            newDiff = -(newGain / targetRatio - (lossOld - dropLoss));
            if (newDiff >= 0f) return Float.NaN;
        }
        return rfp.get(n - 1) * (1f + newDiff);
    }
}