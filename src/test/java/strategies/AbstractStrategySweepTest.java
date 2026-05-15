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
import static org.assertj.core.api.Assertions.offset;

/**
 * Verifies the limit-style sweep helpers on {@link AbstractStrategy}.
 *
 * <p>{@code sweepBuy} walks the ask book from the lowest price upward, routing each
 * level through {@link Exchange#execute}, stopping when the agent's quantity is
 * filled, the next ask exceeds the limit price, funds are exhausted, or the
 * exchange rejects. {@code sweepSell} is symmetric on the bid side.
 */
class AbstractStrategySweepTest extends GlobalStateFixture {

    private Good good;
    private float base;
    private TradingCycle tc;

    /** Minimal strategy subclass that exposes the protected sweep helpers to the test. */
    private static class TestStrategy extends AbstractStrategy implements Runnable {
        TestStrategy(Agent agent, TradingCycle tc) {
            super(agent, tc, 0);
        }
        @Override public synchronized void run() { /* no-op */ }
        int sweepBuyTest(Good g, float maxPrice, int qty)  throws InterruptedException { return sweepBuy(g, maxPrice, qty); }
        int sweepSellTest(Good g, float minPrice, int qty) throws InterruptedException { return sweepSell(g, minPrice, qty); }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        Good.setDirectlyAvailable(100); // ensure IPO ask has positive quantity
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();
        Good.getBid().clear();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
        tc = new TradingCycle();
    }

    /**
     * Place a stub bid so the one-sided-book guard in addAsk doesn't reject our
     * second-and-later asks. Returns the agent for tear-down inspection.
     */
    private Agent seedBidSide(float bidPrice, int qty) throws InterruptedException {
        Agent stubBidder = new Agent();
        stubBidder.setFunds(bidPrice * qty + 1f);
        Offer stub = new Offer(bidPrice, stubBidder, good, qty);
        good.addBid(stub);
        stubBidder.setFunds(stubBidder.getFunds() - bidPrice * qty);
        return stubBidder;
    }

    /** Place a stub ask so addBid passes the symmetric guard during sell-side setup. */
    private Agent seedAskSide(float askPrice, int qty) throws InterruptedException {
        Agent stubSeller = new Agent();
        OwnedGood owned = new OwnedGood(stubSeller, good, qty + 1, qty + 1, base, true);
        stubSeller.getGoodsOwned().add(0, owned);
        Offer stub = new Offer(askPrice, stubSeller, good, qty);
        good.addAsk(stub);
        owned.setNumAvailable(1); // stub ask locked the rest
        return stubSeller;
    }

    /** Place an ask owned by the given seller and lock the shares in numAvailable. */
    private void addAskForSeller(Agent seller, OwnedGood owned, float price, int qty) throws InterruptedException {
        Offer ask = new Offer(price, seller, good, qty);
        good.addAsk(ask);
        owned.setNumAvailable(owned.getNumAvailable() - qty);
        seller.getAsksPlaced().add(ask);
    }

    /** Place a bid owned by the given buyer and lock the funds. */
    private void addBidForBuyer(Agent buyer, float price, int qty) throws InterruptedException {
        Offer bid = new Offer(price, buyer, good, qty);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - price * qty);
        buyer.getBidsPlaced().add(bid);
    }

    // ── 1. Sweeps multiple ask levels in one call ────────────────────────────

    @Test
    void buySweepFillsAcrossMultipleAskLevels() throws InterruptedException {
        seedBidSide(base * 0.5f, 1);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        addAskForSeller(seller, sellerOwned, base * 1.00f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.02f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.04f, 10);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10_000f);

        int filled = new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.05f, 25);

        assertThat(filled).as("sweep should fill the requested 25 shares across 3 levels").isEqualTo(25);
        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isEqualTo(25);
    }

    // ── 2. Stops at the agent's price limit ──────────────────────────────────

    @Test
    void buySweepStopsAtPriceLimit() throws InterruptedException {
        seedBidSide(base * 0.5f, 1);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        addAskForSeller(seller, sellerOwned, base * 1.00f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.02f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.04f, 10); // above maxPrice

        Agent buyer = new Agent();
        buyer.setFunds(base * 10_000f);

        int filled = new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.03f, 100);

        assertThat(filled).as("sweep should only touch the two levels at or below the limit").isEqualTo(20);
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isEqualTo(20);
    }

    // ── 3. Stops when funds are exhausted mid-sweep ──────────────────────────

    @Test
    void buySweepStopsWhenFundsExhausted() throws InterruptedException {
        seedBidSide(base * 0.5f, 1);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        addAskForSeller(seller, sellerOwned, base * 1.00f, 50);
        addAskForSeller(seller, sellerOwned, base * 1.02f, 50);

        Agent buyer = new Agent();
        // Fund just over 12 shares at base*1.00 — sweep should stop well short of 100.
        buyer.setFunds(base * 12.5f);

        int filled = new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.05f, 100);

        assertThat(filled).as("sweep cannot fill more than the agent can afford").isLessThanOrEqualTo(12);
        assertThat(buyer.getFunds()).as("funds must not go negative").isGreaterThanOrEqualTo(0f);
    }

    // ── 4. Stops when the next level falls outside the exchange price band ───

    @Test
    void buySweepStopsWhenExchangeRejects() throws InterruptedException {
        seedBidSide(base * 0.5f, 1);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        addAskForSeller(seller, sellerOwned, base * 1.00f, 10);
        // 1.10 is outside the +5% Exchange band — execute will reject it.
        addAskForSeller(seller, sellerOwned, base * 1.10f, 10);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10_000f);

        // maxPrice is generous (sweep itself wouldn't stop us), but Exchange.execute will
        // refuse the second level because it's outside priceCheck * 1.05.
        int filled = new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.20f, 100);

        assertThat(filled).as("sweep must stop at the first exchange rejection").isEqualTo(10);
    }

    // ── 5. Sweeping past your own resting offer stops the sweep ──────────────

    @Test
    void buySweepStopsWhenTopOfBookBelongsToSelf() throws InterruptedException {
        seedBidSide(base * 0.5f, 1);

        Agent buyer = new Agent();
        OwnedGood buyerOwned = new OwnedGood(buyer, good, 50, 50, base, true);
        buyer.getGoodsOwned().add(0, buyerOwned);
        buyer.setFunds(base * 10_000f);
        // buyer's own ask sits at the top of the book.
        addAskForSeller(buyer, buyerOwned, base * 1.00f, 10);

        int filled = new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.05f, 10);

        assertThat(filled).as("cannot fill against your own resting offer").isEqualTo(0);
    }

    // ── 6. Sell-side: sweeps multiple bid levels ─────────────────────────────

    @Test
    void sellSweepFillsAcrossMultipleBidLevels() throws InterruptedException {
        seedAskSide(base * 1.5f, 1);

        Agent bidder = new Agent();
        bidder.setFunds(base * 100_000f);
        // Bids: 1.00, 0.99, 0.98 of base — sweep should hit highest first.
        addBidForBuyer(bidder, base * 1.00f, 10);
        addBidForBuyer(bidder, base * 0.99f, 10);
        addBidForBuyer(bidder, base * 0.98f, 10);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 100, 100, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        int filled = new TestStrategy(seller, tc).sweepSellTest(good, base * 0.97f, 25);

        assertThat(filled).as("sell sweep should fill the requested 25 across bids").isEqualTo(25);
        assertThat(sellerOwned.getNumOwned()).isEqualTo(75);
    }

    // ── 7. Sell-side: stops at the agent's minimum price ─────────────────────

    @Test
    void sellSweepStopsAtPriceLimit() throws InterruptedException {
        seedAskSide(base * 1.5f, 1);

        Agent bidder = new Agent();
        bidder.setFunds(base * 100_000f);
        addBidForBuyer(bidder, base * 1.00f, 10);
        addBidForBuyer(bidder, base * 0.99f, 10);
        addBidForBuyer(bidder, base * 0.97f, 10); // below minPrice

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 100, 100, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        int filled = new TestStrategy(seller, tc).sweepSellTest(good, base * 0.98f, 100);

        assertThat(filled).as("sell sweep stops when the next bid drops below minPrice").isEqualTo(20);
    }

    // ── 8. Cash conservation across a multi-level buy sweep ──────────────────

    @Test
    void cashIsConservedAcrossBuySweep() throws InterruptedException {
        Agent stubBidder = seedBidSide(base * 0.5f, 1);

        Agent seller = new Agent();
        OwnedGood sellerOwned = new OwnedGood(seller, good, 500, 500, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);
        addAskForSeller(seller, sellerOwned, base * 1.00f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.02f, 10);
        addAskForSeller(seller, sellerOwned, base * 1.04f, 10);

        Agent buyer = new Agent();
        buyer.setFunds(base * 10_000f);

        float totalBefore = buyer.getFunds() + seller.getFunds() + stubBidder.getFunds()
                + (base * 0.5f * 1); // stub bid reservation already deducted; add it back so we compare like for like

        new TestStrategy(buyer, tc).sweepBuyTest(good, base * 1.05f, 25);

        float openBidReservations = 0f;
        for (Offer o : Good.getBid()) {
            openBidReservations += o.getPrice() * o.getNumOffered();
        }
        float totalAfter = buyer.getFunds() + seller.getFunds() + stubBidder.getFunds() + openBidReservations;

        assertThat(totalAfter).as("cash must be conserved across the sweep")
                .isCloseTo(totalBefore, offset(1.0f));
    }
}
