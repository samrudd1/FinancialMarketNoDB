package good;

import agent.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GoodPriceAndVwapTest extends GlobalStateFixture {

    private Good good;
    private Agent agent;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        agent = new Agent();
    }

    @Test
    void startingPriceIsInExpectedRange() {
        assertThat(Good.getPrice())
                .isBetween(20f, 100f);
        assertThat(Good.getStartingPrice()).isEqualTo(Good.getPrice());
    }

    @Test
    void setPriceUpdatesPrice() {
        float tradePrice = Good.getPrice() * 1.01f;
        Offer offer = new Offer(tradePrice, agent, good, 10);

        good.setPrice(offer, 10);

        assertThat(Good.getPrice()).isCloseTo(tradePrice, within(0.01f));
    }

    @Test
    void setPriceRecordsPrevPrice() {
        float before = Good.getPrice();
        Offer offer = new Offer(before * 1.02f, agent, good, 5);

        good.setPrice(offer, 5);

        assertThat(Good.getPrevPrice()).isEqualTo(before);
    }

    @Test
    void setPriceIncrementsNumTrades() {
        int before = Good.getNumTrades();
        good.setPrice(new Offer(Good.getPrice(), agent, good, 1), 1);
        assertThat(Good.getNumTrades()).isEqualTo(before + 1);
    }

    @Test
    void setPriceAppendsToPriceList() {
        float tradePrice = Good.getPrice() * 0.99f;
        good.setPrice(new Offer(tradePrice, agent, good, 5), 5);
        assertThat(Good.getPriceList()).isNotEmpty();
        assertThat(Good.getPriceList().get(Good.getPriceList().size() - 1))
                .isCloseTo(tradePrice, within(0.01f));
    }

    @Test
    void setPriceUpdatesPriceData() {
        int tradeNum = Good.getNumTrades() + 1;
        float tradePrice = Good.getPrice() * 1.03f;
        good.setPrice(new Offer(tradePrice, agent, good, 8), 8);
        assertThat(Good.getPriceData()).containsKey(tradeNum);
    }

    @Test
    void setPriceUpdatesHighest() {
        float aboveHighest = Good.getHighest() + 100f;
        good.setPrice(new Offer(aboveHighest, agent, good, 1), 1);
        assertThat(Good.getHighest()).isEqualTo(aboveHighest);
    }

    @Test
    void setPriceUpdatesLowest() {
        // lowest starts at 110; trade below that resets it
        float belowLowest = 5f;
        good.setPrice(new Offer(belowLowest, agent, good, 1), 1);
        assertThat(Good.getLowest()).isEqualTo(belowLowest);
    }

    @Test
    void vwapCorrectAfterFirstTrade() {
        float tradePrice = 60f;
        int qty = 100;
        good.setPrice(new Offer(tradePrice, agent, good, qty), qty);

        // vwap starts at 0, volume starts at 0 → first trade: vwap = (0*0 + 60*100)/(0+100) = 60
        assertThat(Good.getVwap()).isCloseTo(60f, within(0.01f));
        assertThat(Good.getVolume()).isEqualTo(100.0);
    }

    @Test
    void vwapCorrectAfterMultipleTrades() {
        good.setPrice(new Offer(50f, agent, good, 100), 100);
        good.setPrice(new Offer(60f, agent, good, 50), 50);

        // vwap = (50*100 + 60*50) / (100 + 50) = (5000 + 3000) / 150 ≈ 53.33
        assertThat(Good.getVwap()).isCloseTo(53.33f, within(0.1f));
        assertThat(Good.getVolume()).isEqualTo(150.0);
    }

    @Test
    void highestAndLowestTrackedAcrossSequence() {
        good.setPrice(new Offer(50f, agent, good, 1), 1);
        good.setPrice(new Offer(60f, agent, good, 1), 1);
        good.setPrice(new Offer(40f, agent, good, 1), 1);

        assertThat(Good.getHighest()).isEqualTo(60f);
        assertThat(Good.getLowest()).isEqualTo(40f);
    }

    @Test
    void multiplePricesAppendedToPriceList() {
        int before = Good.getPriceList().size();
        good.setPrice(new Offer(55f, agent, good, 10), 10);
        good.setPrice(new Offer(57f, agent, good, 10), 10);
        good.setPrice(new Offer(53f, agent, good, 10), 10);
        assertThat(Good.getPriceList()).hasSize(before + 3);
    }
}
