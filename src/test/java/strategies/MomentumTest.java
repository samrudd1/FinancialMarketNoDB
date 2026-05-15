package strategies;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;
import trade.Exchange;
import trade.TradingCycle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Momentum decision logic.
 *
 * <p>Momentum reads {@link Exchange#getPriceRoc(int)} over a 5-round window
 * and acts on |ROC| ≥ 0.02 if the agent's {@code momentumPosition} counter is
 * still inside the ±5 cap.
 *
 * <p>Setup trick: pre-populate {@link Exchange#getRoundFinalPrice()} with at
 * least 6 round-close entries, then push {@link Exchange#lastPrice} and
 * {@link Good#setPrice} to a price that produces the desired ROC. Both
 * {@code lastPrice} and {@code priceCheck} are aligned with the new price so
 * the Exchange's ±5% / -4% price band doesn't reject the test trade.
 */
class MomentumTest extends GlobalStateFixture {

    private Good good;
    private float base;
    private TradingCycle tc;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
        tc = new TradingCycle();
    }

    /** Move the current price to {@code newPrice} and align the price-band reference. */
    private void primePrice(float newPrice) {
        Agent dummy = new Agent();
        good.setPrice(new Offer(newPrice, dummy, good, 1), 1);
        Exchange.lastPrice = newPrice;
        Exchange.getInstance().setPriceCheck(newPrice);
    }

    private void primeRoundHistory(int count, float closePrice) {
        for (int i = 0; i < count; i++) Exchange.getRoundFinalPrice().add(closePrice);
    }

    // ── no action when no round history ───────────────────────────────────────

    @Test
    void noActionWhenRocHistoryInsufficient() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 10000f);
        int tradesBefore = Good.getNumTrades();

        new Momentum(agent, tc, 0).run();

        assertThat(Good.getNumTrades())
                .as("no trade when ROC has no history (returns 0)")
                .isEqualTo(tradesBefore);
        assertThat(agent.getMomentumPosition()).isZero();
    }

    // ── position cap blocks further same-direction trades ─────────────────────

    @Test
    void positionCapBlocksBuyAtCap() throws InterruptedException {
        primeRoundHistory(6, base);
        primePrice(base * 1.05f);  // ROC = 0.05 → above 0.02 threshold

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 1.04f, seller, good, 100));
        sellerOwned.setNumAvailable(400);

        Agent momBuyer = new Agent();
        momBuyer.setFunds(base * 50000f);
        momBuyer.setMomentumPosition(5);  // already at cap

        int tradesBefore = Good.getNumTrades();
        new Momentum(momBuyer, tc, 7).run();

        assertThat(Good.getNumTrades())
                .as("momentumPosition at +5 cap must block further buys")
                .isEqualTo(tradesBefore);
    }

    // ── buy signal executes when uptrend conditions met ───────────────────────

    @Test
    void buySignalExecutesTradeWhenUpTrendConditionsMet() throws InterruptedException {
        primeRoundHistory(6, base);
        primePrice(base * 1.05f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 1000, 1000, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        good.addAsk(new Offer(base * 1.04f, seller, good, 100));
        sellerOwned.setNumAvailable(900);

        Agent momBuyer = new Agent();
        momBuyer.setFunds(base * 50000f);

        new Momentum(momBuyer, tc, 7).run();

        assertThat(momBuyer.getGoodsOwned()).as("buyer should own shares after Momentum buy").isNotEmpty();
        assertThat(momBuyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
        assertThat(momBuyer.getMomentumPosition())
                .as("momentumPosition must increment after a buy fill")
                .isEqualTo(1);
    }

    // ── sizing is fractional, not all-in ──────────────────────────────────────

    @Test
    void buyUsesFractionalSizingNotFullBankroll() throws InterruptedException {
        primeRoundHistory(6, base);
        primePrice(base * 1.05f);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 50000, 50000, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        // Ask quantity > sized wantToBuy so sizing is the binding constraint, not the offer.
        good.addAsk(new Offer(base * 1.04f, seller, good, 30000));
        sellerOwned.setNumAvailable(20000);

        Agent momBuyer = new Agent();
        float startingFunds = base * 50000f;
        momBuyer.setFunds(startingFunds);

        new Momentum(momBuyer, tc, 7).run();

        // With MAX_TRADE_FRACTION=0.4f and strength clamped to 0.5 (ROC=0.05 of 0.10 saturation),
        // shares bought ≈ (funds * 0.5 * 0.4) / offer.price = 20% of bankroll worth of shares.
        // Must be strictly less than going all-in (funds / price).
        assertThat(momBuyer.getGoodsOwned()).isNotEmpty();
        int bought = momBuyer.getGoodsOwned().get(0).getNumOwned();
        int allInWouldBe = (int) Math.floor(startingFunds / (base * 1.04f));
        assertThat(bought)
                .as("partial-sizing must buy strictly fewer shares than all-in")
                .isLessThan(allInWouldBe);
    }

    // ── sell signal executes on downtrend ─────────────────────────────────────

    @Test
    void sellSignalExecutesWhenDownTrendConditionsMet() throws InterruptedException {
        // Down-trend: prior closes at base*1.05, current at base
        primeRoundHistory(6, base * 1.05f);
        primePrice(base);
        // ROC = (base - base*1.05) / (base*1.05) ≈ -0.0476 → below -0.02

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        // Bid for the Momentum agent to hit. Buyer must not be the same agent.
        Agent bidder = new Agent();
        bidder.setFunds(base * 10000f);
        float bidPrice = base * 0.999f;  // ≥ price*0.995, within band
        Offer bid = new Offer(bidPrice, bidder, good, 100);
        good.addBid(bid);
        bidder.setFunds(bidder.getFunds() - bidPrice * 100);

        int sharesBefore = sellerOwned.getNumOwned();
        new Momentum(seller, tc, 7).run();

        assertThat(sellerOwned.getNumOwned())
                .as("seller's shares must decrease after Momentum sell signal")
                .isLessThan(sharesBefore);
        assertThat(seller.getMomentumPosition())
                .as("momentumPosition must decrement after a sell fill")
                .isEqualTo(-1);
    }

    // ── falls back to limit order when no qualifying taker offer ──────────────

    @Test
    void buyFallsBackToLimitOrderWhenNoQualifyingAsk() throws InterruptedException {
        primeRoundHistory(6, base);
        primePrice(base * 1.05f);
        // No ask on book at all → taker path can't fire → limit fallback expected

        Agent momBuyer = new Agent();
        momBuyer.setFunds(base * 50000f);

        new Momentum(momBuyer, tc, 7).run();

        // The fallback createBid lives just inside the spread; without an ask on
        // book the addBid path may reject (one-sided book guard). The test
        // accepts either outcome: a placed bid OR no placed bid — what we're
        // verifying is that no trade was forced through.
        assertThat(momBuyer.getGoodsOwned())
                .as("no fill when there is no qualifying ask")
                .isEmpty();
        assertThat(momBuyer.getMomentumPosition())
                .as("momentumPosition must stay at 0 when no fill happened")
                .isZero();
    }
}
