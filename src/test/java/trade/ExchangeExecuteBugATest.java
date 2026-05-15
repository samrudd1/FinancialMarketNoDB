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
 * Bug A fix: seller's numAvailable is decremented by execute ONLY when the seller
 * hits a bid directly (offer is a bid). When the seller placed an ask, numAvailable
 * was already locked by createAsk() and must not be decremented again.
 */
class ExchangeExecuteBugATest extends GlobalStateFixture {

    private Good good;
    private Agent buyer;
    private Agent seller;
    private OwnedGood sellerOwned;
    private TradingCycle tc;
    private float base;
    private static final int INITIAL_SHARES = 100;
    private static final int TRADE_QTY = 20;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        base = Good.getPrice();
        Good.getAsk().clear();

        tc = new TradingCycle();
        seller = new Agent();
        sellerOwned = new OwnedGood(seller, good, INITIAL_SHARES, INITIAL_SHARES, base, true);
        seller.getGoodsOwned().add(0, sellerOwned);

        buyer = new Agent();
        buyer.setFunds(base * 1000f);
    }

    // ── Scenario 1: seller placed an ask (offerIsAsk = true) ─────────────────
    // createAsk() already locked numAvailable; execute must NOT decrement it again.

    @Test
    void askTrade_numOwnedDecremented() throws InterruptedException {
        Offer ask = new Offer(base, seller, good, TRADE_QTY);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(INITIAL_SHARES - TRADE_QTY); // simulate createAsk pre-lock

        Exchange.getInstance().execute(buyer, seller, ask, TRADE_QTY, tc, 1);

        assertThat(sellerOwned.getNumOwned())
                .as("numOwned must always be decremented when shares are sold via ask")
                .isEqualTo(INITIAL_SHARES - TRADE_QTY);
    }

    @Test
    void askTrade_numAvailableNotDecrementedAgain() throws InterruptedException {
        Offer ask = new Offer(base, seller, good, TRADE_QTY);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(INITIAL_SHARES - TRADE_QTY); // createAsk pre-lock
        int availBeforeExecute = sellerOwned.getNumAvailable();

        Exchange.getInstance().execute(buyer, seller, ask, TRADE_QTY, tc, 1);

        assertThat(sellerOwned.getNumAvailable())
                .as("numAvailable must NOT be decremented again when ask was pre-locked by createAsk")
                .isEqualTo(availBeforeExecute);
    }

    // ── Scenario 2: seller hits buyer's bid directly (offerIsAsk = false) ────
    // numAvailable was never locked; execute must decrement both numOwned and numAvailable.

    @Test
    void bidTrade_numOwnedDecremented() throws InterruptedException {
        // Both sides empty so a bid can be placed
        Offer bid = new Offer(base, buyer, good, TRADE_QTY);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - base * TRADE_QTY); // simulate createBid reservation

        Exchange.getInstance().execute(buyer, seller, bid, TRADE_QTY, tc, 1);

        assertThat(sellerOwned.getNumOwned())
                .as("numOwned must be decremented when seller hits buyer's bid")
                .isEqualTo(INITIAL_SHARES - TRADE_QTY);
    }

    @Test
    void bidTrade_numAvailableDecrementedByExecute() throws InterruptedException {
        Offer bid = new Offer(base, buyer, good, TRADE_QTY);
        good.addBid(bid);
        buyer.setFunds(buyer.getFunds() - base * TRADE_QTY);

        Exchange.getInstance().execute(buyer, seller, bid, TRADE_QTY, tc, 1);

        assertThat(sellerOwned.getNumAvailable())
                .as("numAvailable must be decremented by execute when seller hit the bid directly")
                .isEqualTo(INITIAL_SHARES - TRADE_QTY);
    }

    @Test
    void sellerWithNoOwnedGoodDoesNotThrow() throws InterruptedException {
        // execute should handle the case where seller has no OwnedGood gracefully
        Agent bareSeller = new Agent();  // no goodsOwned
        bareSeller.setFunds(base * 100f);
        Offer ask = new Offer(base, bareSeller, good, TRADE_QTY);
        good.addAsk(ask);

        boolean result = Exchange.getInstance().execute(buyer, bareSeller, ask, TRADE_QTY, tc, 1);

        assertThat(result).isTrue(); // trade still executes
    }
}
