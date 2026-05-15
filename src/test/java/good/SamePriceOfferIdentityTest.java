package good;

import agent.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;
import trade.Exchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the "same-price offer collision" bug in the vendored
 * {@code SortedList}.
 *
 * <p>The order book uses {@code NaturalSortedList<Offer>}, whose
 * {@code contains}/{@code remove} compare purely via {@code Offer.compareTo}.
 * Before the fix, {@code Offer.compareTo} only compared price, so two distinct
 * offers placed at the exact same price were indistinguishable to the tree:
 * {@code bid.contains(B)} could match A (the other agent's same-price bid),
 * and {@code bid.remove(B)} would silently delete A from the book.
 *
 * <p>That broke {@code Exchange.execute}'s {@code removeBidFull} path: when one
 * of the two same-price bids was filled, the WRONG node was removed from the
 * tree. The other agent's bid then looked "dead" to {@code cleanOffers}, was
 * dropped from {@code bidsPlaced} with no refund, and the locked cash was lost
 * for good — producing the slow portfolio drain visible in the dashboard
 * (PLACED bids with no matching CANCELLED/FILLED entry in the ledger).
 *
 * <p>The fix gives every {@code Offer} a unique id and breaks ties on it in
 * {@code compareTo}, so the tree can address each offer individually.
 */
class SamePriceOfferIdentityTest extends GlobalStateFixture {

    private Good good;
    private float base;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();
        Exchange.getInstance().setPriceCheck(base);
        Exchange.lastPrice = base;
    }

    @Test
    void samePriceBidsAreDistinctInTheBook() throws InterruptedException {
        Agent a = new Agent();
        Agent b = new Agent();

        Offer bidA = new Offer(base, a, good, 5);
        Offer bidB = new Offer(base, b, good, 7);

        // Need an ask to satisfy the one-sided book guard.
        good.addAsk(new Offer(base * 1.1f, new Agent(), good, 1));

        good.addBid(bidA);
        good.addBid(bidB);

        assertThat(Good.getBid()).hasSize(2);
        assertThat(Good.getBid().contains(bidA)).as("contains must distinguish identical-price offers").isTrue();
        assertThat(Good.getBid().contains(bidB)).as("contains must distinguish identical-price offers").isTrue();
    }

    @Test
    void removingOneSamePriceBidDoesNotRemoveTheOther() throws InterruptedException {
        Agent a = new Agent();
        Agent b = new Agent();

        Offer bidA = new Offer(base, a, good, 5);
        Offer bidB = new Offer(base, b, good, 7);

        good.addAsk(new Offer(base * 1.1f, new Agent(), good, 1));
        good.addBid(bidA);
        good.addBid(bidB);

        // Remove B specifically — A must remain on the book.
        Good.getBid().remove(bidB);

        assertThat(Good.getBid().contains(bidA)).as("A's bid must survive removal of B's same-price bid").isTrue();
        assertThat(Good.getBid().contains(bidB)).as("B's bid must be gone").isFalse();
        assertThat(Good.getBid()).hasSize(1);
    }

    @Test
    void removeBidFullOnFillRemovesTheCorrectOfferAndRefundsCorrectly() throws InterruptedException {
        // Reproduces the production leak path: two agents both have a bid at the
        // same price; one is filled. Before the fix, the wrong node was removed
        // from the tree and the other agent's funds stayed locked forever.
        float startA = base * 100f;
        float startB = base * 100f;

        Agent a = new Agent();
        a.setFunds(startA);
        Agent b = new Agent();
        b.setFunds(startB);

        Offer bidA = new Offer(base, a, good, 5);
        Offer bidB = new Offer(base, b, good, 5);

        // Untouchable ask above the price band keeps the bid side from being touched
        // by anything other than our explicit removeBidFull call.
        good.addAsk(new Offer(base * 1.1f, new Agent(), good, 1));
        good.addBid(bidA);
        good.addBid(bidB);

        // Simulate the fill mutation Exchange.execute performs on a fully-filled bid.
        good.removeBidFull(bidB);

        assertThat(Good.getBid().contains(bidA))
                .as("A's same-price bid must remain in the book after B's bid is removed")
                .isTrue();
        assertThat(Good.getBid().contains(bidB))
                .as("B's bid (the one we removed) must be gone")
                .isFalse();
        assertThat(Good.getBid()).hasSize(1);
        // numOffered on bidB is zeroed by removeBidFull — that's a fill, not a cancel.
        assertThat(bidB.getNumOffered()).isEqualTo(0);
        // numOffered on bidA must be untouched.
        assertThat(bidA.getNumOffered()).isEqualTo(5);
    }

    @Test
    void samePriceAsksAreDistinctInTheBook() throws InterruptedException {
        Agent a = new Agent();
        Agent b = new Agent();

        Offer askA = new Offer(base, a, good, 5);
        Offer askB = new Offer(base, b, good, 7);

        good.addBid(new Offer(base * 0.9f, new Agent(), good, 1));
        good.addAsk(askA);
        good.addAsk(askB);

        assertThat(Good.getAsk().contains(askA)).isTrue();
        assertThat(Good.getAsk().contains(askB)).isTrue();
        assertThat(Good.getAsk()).hasSize(2);

        Good.getAsk().remove(askB);
        assertThat(Good.getAsk().contains(askA)).isTrue();
        assertThat(Good.getAsk().contains(askB)).isFalse();
    }
}
