package session;

import agent.Agent;
import agent.OwnedGood;
import good.Good;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * this is used to establish a connection with the database and act as a central data point in the program
 * @version 1.0
 */
public class Session {
    @Getter @Setter private static int numAgents; //number of agents currently on the market
    @Getter private static final Map<Integer, Agent> agents = new HashMap<>(); //map of all agents
    @Getter private static final ArrayList<Good> goods = new ArrayList<>(); //contains the stock object
    @Getter private static final Map<String,OwnedGood> ownerships = new HashMap<>(); //keeps track of stock ownerships
    @Getter @Setter private static int numOfRounds = 0;

    /**
     * private constructor to prevent instantiation.
     */
    private Session(){}
}
