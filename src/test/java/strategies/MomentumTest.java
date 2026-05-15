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
 * Momentum buys when priceDiff in [0.03, 0.1] and !prevPriceUp,
 * sells when priceDiff in [-0.1, -0.03] and prevPriceUp.
 *
 * priceDiff = (Good.getPrice() - Exchange.getRoundPrice()) / Exchange.getRoundPrice()
 *
 * Setup trick: pre-populate roundFinalPrice with one entry at base, then execute one
 * trade at base*1.04. Exchange.execute with a new round uses the last roundFinalPrice
 * entry as roundPrice, giving priceDiff = 0.04 (in buy range).
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

    // ── no action when roundPrice is zero ─────────────────────────────────────

    @Test
    void noActionWhenRoundPriceIsZero() throws InterruptedException {
        // After reset, roundPrice=0 → priceDiff = base/0 = Infinity → not in [0.03,0.1] → no trade
        Agent agent = new Agent();
        agent.setFunds(base * 10000f);
        agent.setPrevPriceUp(false);

        int tradesBefore = Good.getNumTrades();
        new Momentum(agent, tc, 0).run();

        assertThat(Good.getNumTrades()).as("no trade when roundPrice is 0").isEqualTo(tradesBefore);
    }

    // ── prevPriceUp flag blocks buy even with uptrend ─────────────────────────

    @Test
    void prevPriceUpFlagBlocksBuySignal() throws InterruptedException {
        // Set up uptrend: roundPrice=base, price=base*1.04
        Exchange.getRoundFinalPrice().add(base);

        Agent setupSeller = new Agent();
        OwnedGood setupOwned = new OwnedGood(setupSeller, good, 500, 490, base, true);
        setupSeller.getGoodsOwned().add(0, setupOwned);
        Offer setupAsk = new Offer(base * 1.04f, setupSeller, good, 10);
        good.addAsk(setupAsk);
        Agent setupBuyer = new Agent();
        setupBuyer.setFunds(base * 10000f);
        Exchange.getInstance().execute(setupBuyer, setupSeller, setupAsk, 10, tc, 1);
        // roundPrice=base, Good.price=base*1.04, priceDiff=0.04 (buy signal)

        Agent momAgent = new Agent();
        momAgent.setFunds(base * 10000f);
        momAgent.setPrevPriceUp(true); // flag blocks buy

        int tradesBefore = Good.getNumTrades();
        new Momentum(momAgent, tc, 1).run();

        assertThat(Good.getNumTrades()).as("prevPriceUp=true must block buy even with positive priceDiff")
                .isEqualTo(tradesBefore);
    }

    // ── buy signal executes when uptrend conditions met ───────────────────────

    @Test
    void buySignalExecutesTradeWhenUpTrendConditionsMet() throws InterruptedException {
        // Prime roundFinalPrice so roundPrice = base when round advances to 1
        Exchange.getRoundFinalPrice().add(base);

        Agent setupSeller = new Agent();
        OwnedGood setupOwned = new OwnedGood(setupSeller, good, 1000, 990, base, true);
        setupSeller.getGoodsOwned().add(0, setupOwned);
        Agent setupBuyer = new Agent();
        setupBuyer.setFunds(base * 100000f);

        // Execute setup trade to establish roundPrice=base and Good.price=base*1.04
        Offer setupAsk = new Offer(base * 1.04f, setupSeller, good, 10);
        good.addAsk(setupAsk);
        Exchange.getInstance().execute(setupBuyer, setupSeller, setupAsk, 10, tc, 1);
        // After: roundPrice=base, Good.price=base*1.04, priceDiff=0.04 in [0.03,0.1] ✓

        // Add an ask for Momentum to hit (price=base*1.04, within 0.5% of current price)
        Offer momAsk = new Offer(base * 1.04f, setupSeller, good, 100);
        good.addAsk(momAsk);
        setupOwned.setNumAvailable(setupOwned.getNumAvailable() - 100);

        Agent momBuyer = new Agent();
        momBuyer.setFunds(base * 50000f);
        momBuyer.setPrevPriceUp(false);

        new Momentum(momBuyer, tc, 1).run();

        assertThat(momBuyer.getGoodsOwned()).as("buyer should own shares after Momentum buy").isNotEmpty();
        assertThat(momBuyer.getGoodsOwned().get(0).getNumOwned()).isGreaterThan(0);
    }
}
