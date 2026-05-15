package agent;

import good.Good;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import session.Session;
import support.GlobalStateFixture;

import static org.assertj.core.api.Assertions.assertThat;

class OwnedGoodTest extends GlobalStateFixture {

    private Good good;
    private Agent agent;

    @BeforeEach
    void setUp() throws InterruptedException {
        good = new Good(true);
        agent = new Agent();
    }

    @Test
    void constructorRegistersInSessionOwnerships() {
        OwnedGood owned = new OwnedGood(agent, good, 100, 80, 50f, true);

        assertThat(Session.getOwnerships())
                .as("OwnedGood should register itself in Session.ownerships")
                .containsValue(owned);
    }

    @Test
    void constructorAddsGoodNameToOwnerNamesOwned() {
        int namesBefore = agent.getNamesOwned().size();
        new OwnedGood(agent, good, 50, 50, 30f, true);
        assertThat(agent.getNamesOwned()).hasSize(namesBefore + 1);
        assertThat(agent.getNamesOwned()).contains(Good.getName());
    }

    @Test
    void numOwnedAndNumAvailableAreIndependent() {
        OwnedGood owned = new OwnedGood(agent, good, 100, 70, 50f, true);

        assertThat(owned.getNumOwned()).isEqualTo(100);
        assertThat(owned.getNumAvailable()).isEqualTo(70);

        owned.setNumAvailable(50);
        assertThat(owned.getNumOwned()).isEqualTo(100);
        assertThat(owned.getNumAvailable()).isEqualTo(50);
    }

    @Test
    void numOwnedCanBeDecrementedIndependently() {
        OwnedGood owned = new OwnedGood(agent, good, 100, 100, 50f, true);

        owned.setNumOwned(80);

        assertThat(owned.getNumOwned()).isEqualTo(80);
        assertThat(owned.getNumAvailable()).isEqualTo(100);
    }

    @Test
    void boughtAtStoredCorrectly() {
        float price = 47.25f;
        OwnedGood owned = new OwnedGood(agent, good, 10, 10, price, true);
        assertThat(owned.getBoughtAt()).isEqualTo(price);
    }

    @Test
    void ownerAndGoodReferencesArePreserved() {
        OwnedGood owned = new OwnedGood(agent, good, 10, 10, 50f, true);
        assertThat(owned.getOwner()).isSameAs(agent);
        assertThat(owned.getGood()).isSameAs(good);
    }

    @Test
    void multipleOwnershipsForSameAgentAllRegistered() {
        int before = Session.getOwnerships().size();
        new OwnedGood(agent, good, 10, 10, 50f, true);
        new OwnedGood(agent, good, 20, 20, 55f, true);
        // Key includes boughtAt so two entries with different boughtAt coexist
        assertThat(Session.getOwnerships().size()).isGreaterThanOrEqualTo(before + 2);
    }
}
