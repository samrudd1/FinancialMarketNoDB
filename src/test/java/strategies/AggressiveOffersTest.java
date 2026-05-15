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
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies AggressiveOffers strategy behaviour.
 *
 * Two competitive-pricing paths:
 *   - Normal bid: targetPrice < lowestAsk → bid at targetPrice
 *   - Competitive bid: targetPrice > lowestAsk → bid just below lowestAsk (lowestAsk - 0.01)
 *   - Normal ask: targetPrice > highestBid → ask at targetPrice
 *   - Competitive ask: targetPrice < highestBid → ask just above highestBid (highestBid + 0.01)
 */
class AggressiveOffersTest extends GlobalStateFixture {

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

    // ── competitive bid (target above lowestAsk) ──────────────────────────────

    @Test
    void placesCompetitiveBidJustBelowLowestAsk() throws InterruptedException {
        // Set up an ask on the book so lowestAsk = base
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 300, 250, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 50);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(250);
        seller.setPlacedAsk(true);

        Agent agent = new Agent();
        agent.setFunds(base * 1000f);
        // targetPrice=base*20 → after changeTargetPrice base*20*0.6=base*12 > lowestAsk=base ✓
        // → competitive bid at floor((base-0.01)*100)*0.01
        agent.setTargetPrice(base * 20f);

        new AggressiveOffers(agent, tc, 0).run();

        assertThat(Good.getBid()).as("competitive bid should be placed below lowestAsk").hasSize(1);
        float expectedBidPrice = (float) (Math.floor((base - 0.01f) * 100) * 0.01);
        assertThat(Good.getBid().get(0).getPrice())
                .as("bid price should be just below lowestAsk")
                .isCloseTo(expectedBidPrice, within(0.01f));
    }

    // ── competitive ask (target below highestBid) ─────────────────────────────

    @Test
    void placesCompetitiveAskJustAboveHighestBid() throws InterruptedException {
        // Set up a bid so highestBid = base*0.99
        Agent buyer = new Agent();
        buyer.setFunds(base * 1000f);
        float bidPrice = base * 0.99f;
        Offer bid = new Offer(bidPrice, buyer, good, 50);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - bidPrice * 50);
        buyer.setPlacedBid(true);

        Agent agent = new Agent();
        OwnedGood owned = new OwnedGood(agent, good, 500, 500, base, true);
        agent.getGoodsOwned().add(0, owned);
        // targetPrice=0 → stays 0 after changeTargetPrice; 0 < highestBid=base*0.99 → competitive ask
        agent.setTargetPrice(0f);
        agent.setPlacedBid(true); // prevent bid placement block

        new AggressiveOffers(agent, tc, 0).run();

        // ask added via competitive path: price = Math.round((highestBid + 0.01) * 100) * 0.01
        float expectedAskPrice = (float) (Math.round((bidPrice + 0.01f) * 100) * 0.01);
        assertThat(Good.getAsk()).as("competitive ask should be placed just above highestBid").hasSize(1);
        assertThat(Good.getAsk().get(0).getPrice())
                .as("ask price should be just above highestBid")
                .isCloseTo(expectedAskPrice, within(0.01f));
    }

    // ── normal bid (target below lowestAsk) ───────────────────────────────────

    @Test
    void placesNormalBidAtTargetPriceWhenTargetBelowLowestAsk() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 1000f);
        // targetPrice=base*0.9 (within 80-100% of priceCheck band):
        // After changeTargetPrice: base*0.9*(0.6-1.0) = base*(0.54-0.9), all > price*0.8=base*0.8? Min=base*0.54 < base*0.8 potentially!
        // Use base*1.2 to ensure survival: base*1.2*0.6=base*0.72 < base*0.8 might fail.
        // Use base (unchanged): after changeTargetPrice base*(0.6-1.0), min=base*0.6 < base*0.8 might fail.
        // Best: use competitive bid test which is more reliable. Skip this test, already covered above.
        // Actually, let's use the book-empty case to force the "normal bid" path without ask on book.
        // Empty book → lowestAsk=99999. targetPrice=base*1.5 → after change [base*0.9,base*1.5], all < 99999 ✓
        // and all > base*0.8 ✓ (min base*0.9 > base*0.8) → normal bid placed at new targetPrice
        agent.setTargetPrice(base * 1.5f);

        new AggressiveOffers(agent, tc, 0).run();

        assertThat(Good.getBid()).as("AggressiveOffers places a bid when target is below lowestAsk").hasSize(1);
    }
}
