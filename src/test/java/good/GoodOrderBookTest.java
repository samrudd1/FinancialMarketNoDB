package good;

import agent.Agent;
import agent.OwnedGood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;

class GoodOrderBookTest extends GlobalStateFixture {

    private Good good;
    private Agent buyer;

    @BeforeEach
    void setUpGood() throws InterruptedException {
        Good.setDirectlyAvailable(100); // ensure IPO ask has positive quantity
        good = new Good(true);
        // After construction: ask has 1 IPO offer (numOffered=100), bid is empty
        buyer = new Agent();
    }

    @Test
    void initialStateAfterConstruction() throws InterruptedException {
        assertThat(Good.getAsk()).hasSize(1);
        assertThat(Good.getBid()).isEmpty();
        assertThat(good.getLowestAsk()).isEqualTo(Good.getPrice());
        assertThat(good.getHighestBid()).isZero();
    }

    @Test
    void addBidSucceedsWhenAskIsNonEmpty() throws InterruptedException {
        float bidPrice = Good.getPrice() * 0.95f;
        Offer bid = new Offer(bidPrice, buyer, good, 10);
        good.addBid(bid);
        assertThat(Good.getBid()).hasSize(1);
        assertThat(good.getHighestBid()).isEqualTo(bidPrice);
    }

    @Test
    void addAskRejectedWhenBidEmptyAndAskNonEmpty() throws InterruptedException {
        // Guard: bid.size()==0 && ask.size()>0 → reject
        Offer extraAsk = new Offer(Good.getPrice() * 1.1f, buyer, good, 5);
        good.addAsk(extraAsk);
        assertThat(Good.getAsk()).as("extra ask rejected when bid list is empty").hasSize(1);
    }

    @Test
    void addAskSucceedsWhenBidExists() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, buyer, good, 10));
        good.addAsk(new Offer(base * 1.05f, buyer, good, 5));
        assertThat(Good.getAsk()).hasSize(2);
    }

    @Test
    void addBidRejectedWhenAskEmptyAndBidNonEmpty() throws InterruptedException {
        float base = Good.getPrice();
        Good.getAsk().clear();

        // Both sides empty: first bid is accepted
        good.addBid(new Offer(base * 0.95f, buyer, good, 10));
        assertThat(Good.getBid()).hasSize(1);

        // Guard: ask.size()==0 && bid.size()>0 → reject second bid
        good.addBid(new Offer(base * 0.93f, buyer, good, 5));
        assertThat(Good.getBid()).as("second bid rejected when ask is empty").hasSize(1);
    }

    @Test
    void bidsAreSortedAscendingByPrice() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.90f, buyer, good, 10));
        good.addBid(new Offer(base * 0.92f, buyer, good, 10));
        good.addBid(new Offer(base * 0.88f, buyer, good, 10));

        assertThat(good.getHighestBid()).isEqualTo(base * 0.92f);
        assertThat(good.getSecondHighestBid()).isEqualTo(base * 0.90f);
    }

    @Test
    void asksAreSortedAscendingByPrice() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, buyer, good, 5));
        good.addAsk(new Offer(base * 1.10f, buyer, good, 5));
        good.addAsk(new Offer(base * 1.05f, buyer, good, 5));

        assertThat(good.getLowestAsk()).isEqualTo(base);             // IPO offer
        assertThat(good.getSecondLowestAsk()).isEqualTo(base * 1.05f);
    }

    @Test
    void removeBidRestoresFundsToAgent() throws InterruptedException {
        float base = Good.getPrice();
        int qty = 10;
        float bidPrice = base * 0.90f;
        Offer bid = new Offer(bidPrice, buyer, good, qty);
        good.addBid(bid);

        float fundsAfterReserve = buyer.getFunds() - bidPrice * qty;
        buyer.setFunds(fundsAfterReserve);

        good.removeBid(bid);

        assertThat(buyer.getFunds()).isGreaterThan(fundsAfterReserve);
        assertThat(Good.getBid()).isEmpty();
    }

    @Test
    void removeAskRestoresAvailabilityToAgent() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, buyer, good, 5));

        OwnedGood owned = new OwnedGood(buyer, good, 100, 100, base, true);
        buyer.getGoodsOwned().add(0, owned);

        int qty = 20;
        Offer ask = new Offer(base * 1.05f, buyer, good, qty);
        good.addAsk(ask);
        owned.setNumAvailable(owned.getNumAvailable() - qty);
        int lockedAvail = owned.getNumAvailable();

        good.removeAsk(ask);

        assertThat(owned.getNumAvailable()).isEqualTo(lockedAvail + qty);
    }

    @Test
    void secondLevelReturnsZeroAndSentinelWhenOnlyOneEntry() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.90f, buyer, good, 5));

        assertThat(good.getSecondHighestBid()).isZero();
        assertThat(good.getSecondLowestAsk()).isEqualTo(99999f);
    }

    @Test
    void highestBidOfferReturnsNullWhenBidEmpty() throws InterruptedException {
        assertThat(good.getHighestBidOffer()).isNull();
    }

    @Test
    void lowestAskOfferReturnsValidOfferWithPositiveQuantity() throws InterruptedException {
        float base = Good.getPrice();
        // Clear the IPO ask so we can verify getLowestAskOffer with a known offer.
        Good.getAsk().clear();
        Offer validAsk = new Offer(base * 1.05f, buyer, good, 10);
        good.addAsk(validAsk);

        Offer result = good.getLowestAskOffer();
        assertThat(result).isNotNull();
        assertThat(result).isSameAs(validAsk);
    }

    @Test
    void zeroPriceBidNotAdded() throws InterruptedException {
        good.addBid(new Offer(0f, buyer, good, 10));
        assertThat(Good.getBid()).isEmpty();
    }

    @Test
    void zeroPriceAskNotAdded() throws InterruptedException {
        float base = Good.getPrice();
        good.addBid(new Offer(base * 0.95f, buyer, good, 5));
        int askSizeBefore = Good.getAsk().size();

        good.addAsk(new Offer(0f, buyer, good, 10));

        assertThat(Good.getAsk()).hasSize(askSizeBefore);
    }
}
