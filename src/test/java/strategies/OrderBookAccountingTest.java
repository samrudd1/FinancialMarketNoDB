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
 * Verifies the correctness of the order-book accounting layer:
 *
 *   createBid / createAsk — must not commit funds or shares when the book rejects
 *   the offer (one-sided book guard in addBid / addAsk).
 *
 *   cleanOffers — must (a) cancel stale offers from the book and refund reserved
 *   resources, and (b) remove both stale AND already-dead (filled) offers from the
 *   agent's bidsPlaced / asksPlaced tracking lists so those lists don't grow without
 *   bound.
 *
 * A package-private TestStrategy inner class exposes createBid, createAsk, and
 * cleanOffers without reflection.
 */
class OrderBookAccountingTest extends GlobalStateFixture {

    private Good good;
    private float base;
    private TradingCycle tc;

    /** Minimal strategy subclass that exposes the package-private helpers. */
    private static class TestStrategy extends AbstractStrategy implements Runnable {
        TestStrategy(Agent agent, TradingCycle tc) {
            super(agent, tc, 0);
        }
        @Override public synchronized void run() { /* no-op */ }
        void bid(float price, Good g, int qty)    throws InterruptedException { createBid(price, g, qty); }
        void ask(float price, OwnedGood og, int qty) throws InterruptedException { createAsk(price, og, qty); }
        void clean(Agent a, float price)           throws InterruptedException { cleanOffers(a, price); }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        Good.setDirectlyAvailable(100); // ensure IPO ask has positive quantity
        good = new Good(true);
        base = Good.getPrice();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
        tc = new TradingCycle();
    }

    // ── createBid: silently-dropped bid must not deduct funds ─────────────────

    @Test
    void droppedBidDoesNotDeductFunds() throws InterruptedException {
        // Trigger the one-sided guard: ask side must be empty AND bid side non-empty.
        Good.getAsk().clear();  // empty ask side

        Agent blocker = new Agent();
        blocker.setFunds(base * 100f);
        Offer blockingBid = new Offer(base * 0.5f, blocker, good, 1);
        good.addBid(blockingBid); // bid.size()=1, ask.size()=0 → next addBid will be dropped

        Agent agent = new Agent();
        float initialFunds = base * 200f;
        agent.setFunds(initialFunds);

        new TestStrategy(agent, tc).bid(base * 0.6f, good, 10);

        assertThat(agent.getFunds())
                .as("funds must not be deducted when addBid silently drops the offer")
                .isEqualTo(initialFunds);
        assertThat(agent.getBidsPlaced())
                .as("dropped bid must not appear in bidsPlaced tracking list")
                .isEmpty();
        assertThat(agent.getPlacedBid())
                .as("placedBid flag must remain false for a dropped bid")
                .isFalse();
    }

    // ── createAsk: silently-dropped ask must not lock shares ─────────────────

    @Test
    void droppedAskDoesNotLockShares() throws InterruptedException {
        // Trigger the one-sided guard: bid side must be empty AND ask side non-empty.
        // new Good(true) puts the IPO ask on the book (numOffered=100) → ask.size()=1, bid.size()=0.
        Agent agent = new Agent();
        OwnedGood owned = new OwnedGood(agent, good, 500, 500, base, true);
        agent.getGoodsOwned().add(0, owned);

        new TestStrategy(agent, tc).ask(base * 1.5f, owned, 100);

        assertThat(owned.getNumAvailable())
                .as("numAvailable must not be decremented when addAsk silently drops the offer")
                .isEqualTo(500);
        assertThat(agent.getAsksPlaced())
                .as("dropped ask must not appear in asksPlaced tracking list")
                .isEmpty();
        assertThat(agent.getPlacedAsk())
                .as("placedAsk flag must remain false for a dropped ask")
                .isFalse();
    }

    // ── cleanOffers: stale bid — removed from book, funds refunded, list pruned ─

    @Test
    void staleBidIsRemovedFromTrackingList() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 500f);
        TestStrategy ts = new TestStrategy(agent, tc);

        // Place a bid at half the market price so it will be stale at market price
        ts.bid(base * 0.5f, good, 10);  // succeeds: ask on book, bid < lowestAsk
        assertThat(agent.getBidsPlaced()).hasSize(1);

        // clean at current base price: base*0.5 < base*0.7 → stale
        ts.clean(agent, base);

        assertThat(agent.getBidsPlaced())
                .as("bidsPlaced must be empty after stale bid is cleaned")
                .isEmpty();
        assertThat(agent.getBidsPlaced())
                .as("bidsPlaced must be empty after stale bid is cleaned")
                .isEmpty();
    }

    @Test
    void staleBidRefundsFunds() throws InterruptedException {
        Agent agent = new Agent();
        agent.setFunds(base * 500f);
        float fundsBeforeBid = agent.getFunds();
        TestStrategy ts = new TestStrategy(agent, tc);

        int qty = 10;
        float bidPrice = base * 0.5f;
        ts.bid(bidPrice, good, qty);

        float fundsAfterBid = agent.getFunds();
        assertThat(fundsAfterBid)
                .as("funds must be reserved when bid is placed")
                .isLessThan(fundsBeforeBid);

        ts.clean(agent, base); // base*0.5 < base*0.7 → stale → refunded

        assertThat(agent.getFunds())
                .as("reserved funds must be returned when a stale bid is cleaned")
                .isCloseTo(fundsBeforeBid, within(0.01f));
    }

    // ── cleanOffers: already-filled bid — dead entry pruned from tracking list ─

    @Test
    void filledBidIsRemovedFromTrackingListOnNextClean() throws InterruptedException {
        // Set up: seller with shares places an ask; buyer places a matching bid via createBid.
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 300, 250, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 50);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);
        TestStrategy ts = new TestStrategy(buyer, tc);

        // Place a bid below lowestAsk; use a price within the Exchange price band [0.96, 1.05]×priceCheck
        // so Exchange.execute does not reject it at the band check.
        Offer bidOffer = new Offer(base * 0.98f, buyer, good, 20);
        good.addBid(bidOffer);
        buyer.setFunds(buyer.getFunds() - (base * 0.95f * 20));
        buyer.getBidsPlaced().add(bidOffer);
        buyer.setPlacedBid(true);

        // Seller hits the bid → bid is filled and removed from the order book
        Exchange.getInstance().execute(buyer, seller, bidOffer, 20, tc, 1);

        assertThat(Good.getBid()).doesNotContain(bidOffer); // confirmed filled

        // bidsPlaced still holds the dead offer before cleanOffers is called
        assertThat(buyer.getBidsPlaced()).contains(bidOffer);

        // After cleanOffers (with any price — the offer is dead regardless of price)
        ts.clean(buyer, base);

        assertThat(buyer.getBidsPlaced())
                .as("filled (dead) bid must be pruned from bidsPlaced by cleanOffers")
                .doesNotContain(bidOffer);
    }

    // ── cleanOffers: stale ask — removed from book, shares returned, list pruned ─

    @Test
    void staleAskIsRemovedFromTrackingList() throws InterruptedException {
        Agent seller = new Agent();
        OwnedGood owned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, owned);
        Agent dummy = new Agent();
        dummy.setFunds(base * 100f);
        Offer dummyBid = new Offer(base * 0.9f, dummy, good, 1);
        good.addBid(dummyBid); // put a bid on so addAsk is accepted

        TestStrategy ts = new TestStrategy(seller, tc);
        // Place an ask at 2× market price so it will be stale when cleaned at market
        ts.ask(base * 2.0f, owned, 50); // base*2 > highestBid (accepted)
        assertThat(seller.getAsksPlaced()).hasSize(1);

        // clean at base: base*2.0 > base*1.4 → stale
        ts.clean(seller, base);

        assertThat(seller.getAsksPlaced())
                .as("asksPlaced must be empty after stale ask is cleaned")
                .isEmpty();
    }

    @Test
    void staleAskReturnsShares() throws InterruptedException {
        Agent seller = new Agent();
        OwnedGood owned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, owned);
        Agent dummy = new Agent();
        dummy.setFunds(base * 100f);
        Offer dummyBid = new Offer(base * 0.9f, dummy, good, 1);
        good.addBid(dummyBid);

        TestStrategy ts = new TestStrategy(seller, tc);
        int lockedQty = 50;
        ts.ask(base * 2.0f, owned, lockedQty); // locks 50 shares

        int availableAfterAsk = owned.getNumAvailable(); // should be 500 - 50 = 450
        assertThat(availableAfterAsk).isEqualTo(450);

        ts.clean(seller, base); // base*2 > base*1.4 → stale → shares returned

        assertThat(owned.getNumAvailable())
                .as("locked shares must be returned when a stale ask is cleaned")
                .isEqualTo(500);
    }

    // ── cleanOffers: already-filled ask — dead entry pruned from tracking list ─

    @Test
    void filledAskIsRemovedFromTrackingListOnNextClean() throws InterruptedException {
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 300, 300, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        Offer ask = new Offer(base, seller, good, 50);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(250); // 50 locked in ask
        seller.getAsksPlaced().add(ask);
        seller.setPlacedAsk(true);

        Agent buyer = new Agent();
        buyer.setFunds(base * 5000f);

        // Buyer hits the ask → ask is fully filled and removed from the book
        Exchange.getInstance().execute(buyer, seller, ask, 50, tc, 1);
        assertThat(Good.getAsk()).doesNotContain(ask);

        // asksPlaced still holds the dead entry before cleanOffers
        assertThat(seller.getAsksPlaced()).contains(ask);

        TestStrategy ts = new TestStrategy(seller, tc);
        ts.clean(seller, base);

        assertThat(seller.getAsksPlaced())
                .as("filled (dead) ask must be pruned from asksPlaced by cleanOffers")
                .doesNotContain(ask);
    }

    // ── bidsPlaced stays bounded after multiple fill-and-replace cycles ────────

    @Test
    void bidsPlacedDoesNotGrowAfterMultipleFilledBids() throws InterruptedException {
        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 1000, 900, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        Agent buyer = new Agent();
        buyer.setFunds(base * 50000f);
        TestStrategy ts = new TestStrategy(buyer, tc);

        for (int cycle = 0; cycle < 3; cycle++) {
            // Place a fresh ask so createBid's lowestAsk check passes
            Offer ask = new Offer(base, seller, good, 50);
            good.addAsk(ask);
            sellerOwned.setNumAvailable(sellerOwned.getNumAvailable() - 50);
            seller.setPlacedAsk(true);

            // Buyer places bid within Exchange price band [0.96, 1.05]×priceCheck but below ask
            Offer bidOffer = new Offer(base * 0.98f, buyer, good, 10);
            good.addBid(bidOffer);
            buyer.setFunds(buyer.getFunds() - (base * 0.98f * 10));
            buyer.getBidsPlaced().add(bidOffer);
            buyer.setPlacedBid(true);

            // Seller hits the bid → bid filled and removed from book
            Exchange.getInstance().execute(buyer, seller, bidOffer, 10, tc, cycle + 1);

            // Clean dead entries before next cycle
            ts.clean(buyer, base);

            assertThat(buyer.getBidsPlaced())
                    .as("bidsPlaced must not accumulate dead entries after cycle %d", cycle + 1)
                    .doesNotContain(bidOffer);

            // Reset flags so buyer can place another bid next cycle
            buyer.setPlacedBid(false);
            seller.setPlacedAsk(false);
        }

        assertThat(buyer.getBidsPlaced().size())
                .as("bidsPlaced must not grow beyond the number of currently-open bids")
                .isLessThanOrEqualTo(1);
    }
}
