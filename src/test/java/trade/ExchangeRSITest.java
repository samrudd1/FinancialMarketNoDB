package trade;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RSI(14) and RSI10 calculation inside Exchange.execute.
 *
 * RSI(14) fires when roundFinalPrice.size() > 15 AND a new round begins.
 * RSI10   fires when roundFinalPrice.size() > 150 (inside the RSI block).
 */
class ExchangeRSITest extends GlobalStateFixture {

    private Good good;
    private Agent buyer;
    private Agent seller;
    private OwnedGood sellerOwned;
    private TradingCycle tc;
    private float base;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();

        tc = new TradingCycle();
        seller = new Agent();
        sellerOwned = new OwnedGood(seller, good, 5000, 5000, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        buyer = new Agent();
        buyer.setFunds(base * 100_000f);
    }

    /** Execute one in-band trade at the given roundNum; returns the offer price used. */
    private float executeTrade(int roundNum) throws InterruptedException {
        Offer ask = new Offer(base, seller, good, 5);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
        Exchange.getInstance().execute(buyer, seller, ask, 5, tc, roundNum);
        return base;
    }

    // ── RSI not computed below threshold ─────────────────────────────────────

    @Test
    void rsiNotComputedBelowSixteenRounds() throws InterruptedException {
        // No pre-population: first execute adds 1 entry to roundFinalPrice
        executeTrade(1);

        assertThat(Exchange.getRsiList())
                .as("RSI list must be empty when fewer than 16 round prices exist")
                .isEmpty();
        assertThat(Exchange.getRsi()).isZero();
    }

    @Test
    void rsiNotComputedWhenNoNewRound() throws InterruptedException {
        // Pre-populate with 20 entries
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 20; i++) rfp.add(50f + i);

        // Execute with same roundNum as current round (round was 0 after reset, execute
        // with roundNum=0 → roundNum > round is false → no new round → RSI skipped)
        executeTrade(0);

        assertThat(Exchange.getRsiList())
                .as("RSI must not fire when the round number has not advanced")
                .isEmpty();
    }

    // ── RSI(14) computed and bounded ─────────────────────────────────────────

    @Test
    void rsiComputedAndBoundedAfterSixteenRounds() throws InterruptedException {
        // 16 pre-existing entries; execute with roundNum=1 adds a 17th and triggers new-round
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 16; i++) rfp.add(50f + i);  // strictly increasing → all gains

        executeTrade(1);

        assertThat(Exchange.getRsiList()).hasSize(1);
        assertThat(Exchange.getRsi())
                .as("RSI must be in [0, 100]")
                .isBetween(0f, 100f);
    }

    @Test
    void rsiIsOneHundredWhenAllGains() throws InterruptedException {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        // Strictly increasing, ending at base-1, so the 17th entry (base) is the highest.
        // All 14 diffs in the RSI loop are positive → avgLoss=0 → RSI=100.
        for (int i = 0; i < 16; i++) rfp.add(base - 16f + i);

        executeTrade(1);

        assertThat(Exchange.getRsi())
                .as("RSI should be 100 when all 14 periods are gains")
                .isEqualTo(100f);
    }

    @Test
    void rsiIsZeroWhenAllLosses() throws InterruptedException {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        // Strictly decreasing, ending at base+1, so the 17th entry (base) is the lowest.
        // All 14 diffs in the RSI loop are negative → avgGain=0 → RSI=0.
        for (int i = 0; i < 16; i++) rfp.add(base + 16f - i);

        executeTrade(1);

        assertThat(Exchange.getRsi())
                .as("RSI should be 0 when all 14 periods are losses")
                .isEqualTo(0f);
    }

    @Test
    void rsiAccumulatesAcrossNewRounds() throws InterruptedException {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 16; i++) rfp.add(50f + i);

        executeTrade(1);  // round 0→1: RSI computed, rsiList.size() == 1
        executeTrade(2);  // round 1→2: RSI computed again, rsiList.size() == 2

        assertThat(Exchange.getRsiList()).hasSize(2);
    }

    // ── RSI10 not computed below 150 rounds ───────────────────────────────────

    @Test
    void rsi10NotComputedBelow151Rounds() throws InterruptedException {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        for (int i = 0; i < 16; i++) rfp.add(50f + i);  // enough for RSI14 but not RSI10

        executeTrade(1);

        assertThat(Exchange.getRsiPList())
                .as("RSI10 list must be empty below 151 round prices")
                .isEmpty();
        assertThat(Exchange.getRsiP()).isZero();
    }

    // ── RSI10 computed and bounded ────────────────────────────────────────────

    @Test
    void rsi10ComputedAndBoundedAfter151Rounds() throws InterruptedException {
        ArrayList<Float> rfp = Exchange.getRoundFinalPrice();
        // 151 pre-existing entries (alternating to give both gains and losses)
        for (int i = 0; i < 151; i++) {
            rfp.add(i % 2 == 0 ? 50f : 52f);
        }

        executeTrade(1);  // adds a 152nd entry; RSI14 block triggers; size>150 → RSI10 fires

        assertThat(Exchange.getRsiPList()).hasSize(1);
        assertThat(Exchange.getRsiP())
                .as("RSI10 must be in [0, 100]")
                .isBetween(0f, 100f);
    }
}
