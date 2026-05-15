package good;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferTest {

    // Agent and Good are stored by reference only — Offer does no validation,
    // so null is safe here and keeps tests self-contained.
    private static Offer offer(float price, int qty) {
        return new Offer(price, null, null, qty);
    }

    // --- Constructor / getters ---

    @Test
    void constructorStoresPrice() {
        assertThat(offer(42.5f, 10).getPrice()).isEqualTo(42.5f);
    }

    @Test
    void constructorStoresQuantity() {
        assertThat(offer(10.0f, 250).getNumOffered()).isEqualTo(250);
    }

    @Test
    void constructorStoresOfferMaker() {
        assertThat(new Offer(10.0f, null, null, 5).getOfferMaker()).isNull();
    }

    @Test
    void constructorStoresGood() {
        assertThat(new Offer(10.0f, null, null, 5).getGood()).isNull();
    }

    // --- Setters ---

    @Test
    void setPriceUpdatesPrice() {
        Offer o = offer(10.0f, 5);
        o.setPrice(99.0f);
        assertThat(o.getPrice()).isEqualTo(99.0f);
    }

    @Test
    void setNumOfferedUpdatesQuantity() {
        Offer o = offer(10.0f, 5);
        o.setNumOffered(500);
        assertThat(o.getNumOffered()).isEqualTo(500);
    }

    // --- compareTo ---
    // Formula: Math.round((price - other.price) * 100)

    @Test
    void compareToLowerPriceIsNegative() {
        assertThat(offer(10.0f, 1).compareTo(offer(20.0f, 1))).isNegative();
    }

    @Test
    void compareToHigherPriceIsPositive() {
        assertThat(offer(20.0f, 1).compareTo(offer(10.0f, 1))).isPositive();
    }

    @Test
    void compareToEqualPriceIsZero() {
        assertThat(offer(15.0f, 1).compareTo(offer(15.0f, 1))).isZero();
    }

    @Test
    void compareToQuantityHasNoEffectOnOrdering() {
        // quantity is not part of the price comparison
        assertThat(offer(50.0f, 1).compareTo(offer(50.0f, 9999))).isZero();
    }

    @Test
    void compareToMagnitudeMatchesRoundedPriceDifferenceTimesHundred() {
        // 30 - 10 = 20, * 100 = 2000
        assertThat(offer(30.0f, 1).compareTo(offer(10.0f, 1))).isEqualTo(2000);
    }

    @Test
    void compareToIsAntisymmetric() {
        Offer a = offer(25.0f, 1);
        Offer b = offer(10.0f, 1);
        assertThat(Integer.signum(a.compareTo(b))).isEqualTo(-Integer.signum(b.compareTo(a)));
    }

    @Test
    void compareToSubPennyDifferenceIsDistinguishable() {
        // 10.01 - 10.00 ≈ 0.01, * 100 ≈ 1 → positive
        assertThat(offer(10.01f, 1).compareTo(offer(10.0f, 1))).isPositive();
    }
}
