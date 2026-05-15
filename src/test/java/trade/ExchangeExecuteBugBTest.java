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
 * Bug B fix: buyer is charged by execute ONLY when hitting a seller's ask.
 * When buyer placed a bid, funds were pre-reserved by createBid() and must
 * not be charged again by execute.
 */
class ExchangeExecuteBugBTest extends GlobalStateFixture {

    private Good good;
    private Agent buyer;
    private Agent seller;
    private OwnedGood sellerOwned;
    private TradingCycle tc;
    private float base;
    private static final int TRADE_QTY = 10;

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
    }

    // ── Scenario 1: buyer hits seller's ask (offerIsAsk = true) ──────────────
    // execute must charge the buyer for the shares.

    @Test
    void askTrade_buyerChargedByExecute() throws InterruptedException {
        float initialFunds = base * 500f;
        buyer.setFunds(initialFunds);

        Offer ask = new Offer(base, seller, good, TRADE_QTY);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200 - TRADE_QTY);

        Exchange.getInstance().execute(buyer, seller, ask, TRADE_QTY, tc, 1);

        assertThat(buyer.getFunds())
                .as("buyer must be charged price*qty when hitting an ask")
                .isCloseTo(initialFunds - base * TRADE_QTY, within(0.5f));
    }

    @Test
    void askTrade_sellerReceivesPayment() throws InterruptedException {
        float sellerInitialFunds = seller.getFunds();
        buyer.setFunds(base * 500f);

        Offer ask = new Offer(base, seller, good, TRADE_QTY);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200 - TRADE_QTY);

        Exchange.getInstance().execute(buyer, seller, ask, TRADE_QTY, tc, 1);

        assertThat(seller.getFunds())
                .as("seller must receive price*qty proceeds")
                .isCloseTo(sellerInitialFunds + base * TRADE_QTY, within(0.5f));
    }

    // ── Scenario 2: buyer placed a bid (offerIsAsk = false) ──────────────────
    // execute must NOT charge buyer again — funds were already reserved by createBid().

    @Test
    void bidTrade_buyerNotChargedByExecute() throws InterruptedException {
        float initialFunds = base * 500f;
        float reservedFunds = initialFunds - base * TRADE_QTY; // simulate createBid
        buyer.setFunds(reservedFunds);

        Offer bid = new Offer(base, buyer, good, TRADE_QTY);
        good.addBid(bid);  // both sides were empty

        Exchange.getInstance().execute(buyer, seller, bid, TRADE_QTY, tc, 1);

        // removedBid is called with numOffered=0 (set before removal), so no funds added back.
        // Net effect: buyer.funds == reservedFunds (no further deduction from execute).
        assertThat(buyer.getFunds())
                .as("buyer must NOT be charged again by execute when bid was pre-reserved")
                .isCloseTo(reservedFunds, within(0.5f));
    }

    @Test
    void bidTrade_sellerReceivesPayment() throws InterruptedException {
        float sellerInitialFunds = seller.getFunds();
        float initialBuyerFunds = base * 500f;
        buyer.setFunds(initialBuyerFunds - base * TRADE_QTY); // pre-reserved

        Offer bid = new Offer(base, buyer, good, TRADE_QTY);
        good.addBid(bid);

        Exchange.getInstance().execute(buyer, seller, bid, TRADE_QTY, tc, 1);

        assertThat(seller.getFunds())
                .as("seller still receives payment when hitting a bid")
                .isCloseTo(sellerInitialFunds + base * TRADE_QTY, within(0.5f));
    }

    // ── buyer receives shares in both scenarios ───────────────────────────────

    @Test
    void askTrade_buyerReceivesShares() throws InterruptedException {
        buyer.setFunds(base * 500f);
        Offer ask = new Offer(base, seller, good, TRADE_QTY);
        good.addAsk(ask);
        sellerOwned.setNumAvailable(200 - TRADE_QTY);

        Exchange.getInstance().execute(buyer, seller, ask, TRADE_QTY, tc, 1);

        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isEqualTo(TRADE_QTY);
    }

    @Test
    void bidTrade_buyerReceivesShares() throws InterruptedException {
        buyer.setFunds(base * 500f - base * TRADE_QTY);
        Offer bid = new Offer(base, buyer, good, TRADE_QTY);
        good.addBid(bid);

        Exchange.getInstance().execute(buyer, seller, bid, TRADE_QTY, tc, 1);

        assertThat(buyer.getGoodsOwned()).isNotEmpty();
        assertThat(buyer.getGoodsOwned().get(0).getNumOwned()).isEqualTo(TRADE_QTY);
    }
}
