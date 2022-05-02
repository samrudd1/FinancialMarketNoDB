package agent;

import good.Good;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import session.Session;

/**
 * holds the information related to the stock owned by the agent
 * @version 1.0
 * @since 21/12/21
 * @author github.com/samrudd1
 */
@EqualsAndHashCode
@Log
public class OwnedGood {
    @Getter @Setter private Agent owner; //agent that owns the shares
    @Getter @Setter private Good good; //reference to the stock object
    @Getter @Setter private int numOwned; //quantity of shares
    @Getter @Setter private int numAvailable; //how many are available to trade
    @Getter @Setter private float boughtAt; //average price of all owned shares

    /**
     * constructor used by the Exchange and populateOwnerships() method in Session class
     * @param owner owner of the shares
     * @param good reference to the stock object
     * @param numOwned quantity of shares bought
     * @param numAvailable how many shares available to trade
     * @param boughtAt average price of all shares bought
     * @param isNew //has the object been saved to the database yet
     */
    public OwnedGood(Agent owner, Good good, int numOwned, int numAvailable, float boughtAt, boolean isNew){
        this.owner = owner;
        this.good = good;
        this.numOwned = numOwned;
        this.numAvailable = numAvailable;
        this.boughtAt = boughtAt;
        owner.getNamesOwned().add(Good.getName());
        Session.getOwnerships().put(owner.getId() + "-" + good.getId() + "-" + boughtAt,this);
    }
}
