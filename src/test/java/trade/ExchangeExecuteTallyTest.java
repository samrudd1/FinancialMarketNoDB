package trade;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Exchange.tradeTally increments the correct per-strategy counter
 * for both the buyer and the seller on every executed trade.
 */
class ExchangeExecuteTallyTest extends GlobalStateFixture {

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
        sellerOwned = new OwnedGood(seller, good, 200, 200, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        buyer = new Agent();
        buyer.setFunds(base * 1000f);
    }

    private void executeTrade(int buyerStrategy, int sellerStrategy) throws InterruptedException {
        buyer.setStrategy(buyerStrategy);
        seller.setStrategy(sellerStrategy);

        Offer ask = new Offer(base, seller, good, 10);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 10);

        Exchange.getInstance().execute(buyer, seller, ask, 10, tc, 1);
    }

    @Test
    void defaultStrategyTallyIncrementedForBoth() throws InterruptedException {
        float before = Exchange.getDefaultCount();
        executeTrade(0, 0);
        // Both buyer (0) and seller (0) are default → count += 2
        assertThat(Exchange.getDefaultCount()).isEqualTo(before + 2f);
    }

    @Test
    void momentumTallyIncrementedForBuyer() throws InterruptedException {
        float before = Exchange.getMomCount();
        executeTrade(8, 0);
        assertThat(Exchange.getMomCount()).isEqualTo(before + 1f);
    }

    @Test
    void sentimentTallyIncrementedForSeller() throws InterruptedException {
        float before = Exchange.getSentCount();
        executeTrade(0, 2);
        assertThat(Exchange.getSentCount()).isEqualTo(before + 1f);
    }

    @Test
    void vwapTallyIncrementedOnce() throws InterruptedException {
        float before = Exchange.getVwapCount();
        executeTrade(7, 0);
        assertThat(Exchange.getVwapCount()).isEqualTo(before + 1f);
    }

    @Test
    void rsiTallyIncrementedForBoth() throws InterruptedException {
        float before = Exchange.getRsiCount();
        executeTrade(4, 4);
        assertThat(Exchange.getRsiCount()).isEqualTo(before + 2f);
    }

    @Test
    void rsi10TallyIncrementedOnce() throws InterruptedException {
        float before = Exchange.getRsi10Count();
        executeTrade(5, 0);
        assertThat(Exchange.getRsi10Count()).isEqualTo(before + 1f);
    }

    @Test
    void offerOnlyTallyIncrementedOnce() throws InterruptedException {
        float before = Exchange.getOfferCount();
        executeTrade(0, 3);
        assertThat(Exchange.getOfferCount()).isEqualTo(before + 1f);
    }

    @Test
    void bothRsiTallyIncrementedOnce() throws InterruptedException {
        float before = Exchange.getBothCount();
        executeTrade(6, 0);
        assertThat(Exchange.getBothCount()).isEqualTo(before + 1f);
    }

    @Test
    void vwapMRTallyIncrementedOnce() throws InterruptedException {
        float before = Exchange.getVwapMRCount();
        executeTrade(9, 0);
        assertThat(Exchange.getVwapMRCount()).isEqualTo(before + 1f);
    }

    @Test
    void noTallyChangedWhenOfferNotOnBook() throws InterruptedException {
        buyer.setStrategy(0);
        seller.setStrategy(0);
        Offer phantom = new Offer(base, seller, good, 10);  // not added to book

        float defaultBefore = Exchange.getDefaultCount();
        Exchange.getInstance().execute(buyer, seller, phantom, 10, tc, 1);

        assertThat(Exchange.getDefaultCount())
                .as("tally must not change when offer is not on the order book")
                .isEqualTo(defaultBefore);
    }

    @Test
    void talliesAccumulateAcrossMultipleTrades() throws InterruptedException {
        buyer.setStrategy(0);
        seller.setStrategy(0);
        float before = Exchange.getDefaultCount();

        for (int i = 0; i < 3; i++) {
            Offer ask = new Offer(base, seller, good, 5);
            good.addAsk(ask);
            sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 5);
            Exchange.getInstance().execute(buyer, seller, ask, 5, tc, i + 1);
        }

        assertThat(Exchange.getDefaultCount()).isEqualTo(before + 6f); // 3 trades × 2
    }
}
