package trade;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import good.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the ±5%/-4% rolling-20-trade price band in Exchange.execute.
 * When avg20 has fewer than 20 entries, priceCheck == lastPrice.
 */
class ExchangeExecutePriceBandTest extends GlobalStateFixture {

    private Good good;
    private Agent buyer;
    private Agent seller;
    private OwnedGood sellerOwned;
    private TradingCycle tc;
    private float base;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();                  // createPrice() also sets Exchange.lastPrice
        Good.getAsk().clear();                   // remove zero-qty IPO ask

        tc = new TradingCycle();
        seller = new Agent();
        sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        buyer = new Agent();
        buyer.setFunds(base * 1000f);            // ample funds
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Offer placeSellAsk(float price, int qty) throws InterruptedException {
        Offer ask = new Offer(price, seller, good, qty);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - qty);
        seller.setPlacedAsk(true);
        return ask;
    }

    // ── in-band ───────────────────────────────────────────────────────────────

    @Test
    void inBandOfferExecutesAndUpdatesPrice() throws InterruptedException {
        float inBandPrice = base;   // exactly at lastPrice → always in band
        Offer ask = placeSellAsk(inBandPrice, 10);

        Exchange.getInstance().execute(buyer, seller, ask, 10, tc, 1);

        assertThat(Exchange.lastPrice)
                .as("lastPrice should update when offer is in band")
                .isCloseTo(inBandPrice, within(0.01f));
        assertThat(Good.getNumTrades()).isGreaterThan(0);
    }

    // ── above band (>5%) ─────────────────────────────────────────────────────

    @Test
    void offerMoreThanFivePercentAbovePriceCheckIsRejected() throws InterruptedException {
        float tooHigh = base * 1.06f;            // 6% above → outside band
        Offer ask = placeSellAsk(tooHigh, 10);
        float lastPriceBefore = Exchange.lastPrice;

        Exchange.getInstance().execute(buyer, seller, ask, 10, tc, 1);

        assertThat(Exchange.lastPrice)
                .as("lastPrice must not change when offer is above the band")
                .isEqualTo(lastPriceBefore);
        assertThat(sellerOwned.getNumOwned())
                .as("seller shares must not change when offer is rejected by band")
                .isEqualTo(500);
    }

    // ── below band (<4%) ─────────────────────────────────────────────────────

    @Test
    void offerMoreThanFourPercentBelowPriceCheckIsRejected() throws InterruptedException {
        float tooLow = base * 0.95f;             // 5% below → outside band
        // Must be on the book — clear ask side then add a bid so we can add ask at low price
        Offer ask = placeSellAsk(tooLow, 10);
        float lastPriceBefore = Exchange.lastPrice;

        Exchange.getInstance().execute(buyer, seller, ask, 10, tc, 1);

        assertThat(Exchange.lastPrice)
                .as("lastPrice must not change when offer is below the band")
                .isEqualTo(lastPriceBefore);
        assertThat(sellerOwned.getNumOwned())
                .as("seller shares must not change when offer is below the band")
                .isEqualTo(500);
    }

    // ── offer not on book ────────────────────────────────────────────────────

    @Test
    void offerNotOnBookSkipsTradeExecution() throws InterruptedException {
        Offer phantom = new Offer(base, seller, good, 10);  // never added to book
        float lastPriceBefore = Exchange.lastPrice;

        Exchange.getInstance().execute(buyer, seller, phantom, 10, tc, 1);

        assertThat(Exchange.lastPrice).isEqualTo(lastPriceBefore);
        assertThat(sellerOwned.getNumOwned()).isEqualTo(500);
    }

    // ── same buyer and seller ─────────────────────────────────────────────────

    @Test
    void buyerEqualsSellerReturnsFalse() throws InterruptedException {
        Offer ask = placeSellAsk(base, 10);
        // tc.notifyAll() is called in the buyer==seller branch, so we must hold tc's monitor.
        final boolean[] result = {true};
        synchronized (tc) {
            result[0] = Exchange.getInstance().execute(seller, seller, ask, 10, tc, 0);
        }
        assertThat(result[0]).isFalse();
    }

    // ── priceCheck uses lastAvg once avg20 is full ────────────────────────────

    @Test
    void priceCheckShiftsToAvg20WhenFull() throws InterruptedException {
        // Fill avg20 with low prices so lastAvg is far below base
        for (int i = 0; i < 20; i++) {
            Exchange.getInstance().getAvg20().add(base * 0.50f);
        }
        // avg20 is now exactly 20 entries; trigger lastAvg update by executing a trade
        // The execute code only updates lastAvg when avg20 has >= 20 entries at the start
        // of the NEXT trade. Pre-filling avg20 via the list directly sets the stage.
        // A bid at base is now 100% above lastAvg (0.5*base), so outside the +5% band.
        Offer ask = placeSellAsk(base, 10);
        float lastPriceBefore = Exchange.lastPrice;

        Exchange.getInstance().execute(buyer, seller, ask, 10, tc, 1);

        // The +5% band check uses lastAvg ≈ 0.5*base, so base > 0.5*base*1.05 → rejected
        assertThat(Exchange.lastPrice)
                .as("offer above the avg20-based band should be rejected")
                .isEqualTo(lastPriceBefore);
    }
}
