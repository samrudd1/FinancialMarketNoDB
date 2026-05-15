package strategies;

import agent.Agent;
import good.Good;
import lombok.SneakyThrows;
import trade.Exchange;
import trade.TradingCycle;

import java.util.ArrayList;

/**
 * similar to the RSI class, but uses the RSI 10 value
 * the RSI 10 value uses increments of 10 between rounds used in the equation rather than 1
 * this allows the value to represent strength over longer periods of time
 * this includes the last 140 rounds
 * @version 1.0
 * @since 13/04/22
 * @author github.com/samrudd1
 */
public class RSI10 extends AbstractStrategy implements Runnable {
    Agent agent;
    TradingCycle tc;
    int roundNum;

    public RSI10(Agent agent, TradingCycle tc, int roundNum) {
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
        float rsi = Exchange.getRsiP(); //RSI 10 value

        while(agent.getAgentLock()) wait();
        agent.setAgentLock(true);

        if ((rsi >= 0) && (rsi <= 100)) { //checks RSI 10 value is valid
            Good good = Exchange.getInstance().getGoods().get(0);
            if (rsi < 30) { //checks if RSI is over-sold
                if (agent.getFunds() > price) {
                    double tradeMult = 0.4 + ((30 - rsi) * 0.02); //calculates how much to buy based on the value, the lower the value, the better the buy opportunity
                    int wantToBuy = (int) Math.floor((agent.getFunds() / price) * tradeMult);
                    // Cap the sweep at the tighter of (a) the price at which next-round RSI10
                    // crosses back up through 30 (signal self-extinguishes), or (b) the 3% safety
                    // ceiling. See RSI.rsiBoundaryPrice for the rationale.
                    float rsiCap = rsiBoundaryPrice(30f, true);
                    float maxPrice = Float.isNaN(rsiCap) ? price * 1.03f : Math.min(rsiCap, price * 1.03f);
                    sweepBuy(good, maxPrice, wantToBuy);
                }

            } else if (rsi > 70) { //checks if RSI is over-bought
                if (!agent.getGoodsOwned().isEmpty()) {
                    double tradeMult = 0.4 + ((rsi - 70) * 0.02); //calculates how much to sell based on the value, the higher the value, the better the sell opportunity
                    int offering = (int) Math.floor((agent.getGoodsOwned().get(0).getNumAvailable() * tradeMult));
                    // Mirror of the buy cap: tighter of (a) the price at which next-round RSI10
                    // crosses back down through 70, or (b) the 2% discount safety floor.
                    float rsiCap = rsiBoundaryPrice(70f, false);
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
     * RSI10 analogue of {@link RSI#rsiBoundaryPrice(float, boolean)}. Same self-extinguishing-
     * signal rationale, but using the RSI10 window: 14 diffs at stride 10, so the new diff
     * compares against the price 10 rounds ago and the dropping diff sits at index {@code n-131}.
     * Returns {@code NaN} when there's not enough history or the target is unreachable.
     */
    private static float rsiBoundaryPrice(float targetRsi, boolean buySide) {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        int n = rfp.size();
        // Match Exchange's RSI10 window: 14 diffs with stride 10, oldest diff at index n-131
        // using rfp[n-141] as its base. So we need rfp[n-141] to exist.
        if (n < 141) return Float.NaN;

        float gainOld = 0f;
        float lossOld = 0f;
        for (int i = n - 1; i >= n - 131; i -= 10) {
            float diff = rfp.get(i) / rfp.get(i - 10) - 1f;
            if (diff > 0f) gainOld += diff;
            else if (diff < 0f) lossOld += -diff;
        }
        float dropDiff = rfp.get(n - 131) / rfp.get(n - 141) - 1f;
        float dropGain = dropDiff > 0f ? dropDiff : 0f;
        float dropLoss = dropDiff < 0f ? -dropDiff : 0f;

        float targetRatio = targetRsi / (100f - targetRsi);
        float newDiff;
        if (buySide) {
            float newLoss = lossOld - dropLoss;
            if (newLoss <= 0f) return Float.NaN;
            newDiff = targetRatio * newLoss - gainOld + dropGain;
            if (newDiff <= 0f) return Float.NaN;
        } else {
            float newGain = gainOld - dropGain;
            if (newGain <= 0f) return Float.NaN;
            newDiff = -(newGain / targetRatio - (lossOld - dropLoss));
            if (newDiff >= 0f) return Float.NaN;
        }
        // The new diff in next round's window compares the appended price against rfp[n-10]
        // (10 rounds back), so the boundary price is rfp[n-10] * (1 + newDiff).
        return rfp.get(n - 10) * (1f + newDiff);
    }
}