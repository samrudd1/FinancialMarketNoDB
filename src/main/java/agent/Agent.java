package agent;

import good.Good;
import good.Offer;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import session.Session;
import trade.Exchange;
import trade.TradingCycle;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

/**
 * This class represents the AI agents that trade on the market
 * @version 1.0
 * @since 21/12/21
 * @author github.com/samrudd1
 */
@ToString
public class Agent {

    @Getter @Setter public static int nextID; //static int used to provide unique identifier for all agents
    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());
    @Getter @Setter private static boolean volatility = true; //changes the strategies running on the market
    @Getter @Setter private static int sentAdjust = 0; //balances the sentiment system
    @Getter @Setter private static int roundChange = 0; //when sentiment was last adjusts
    @Getter @Setter private static int sentiment; //represents the overall social sentiment towards the company, 20 is neutral

    @Getter @Setter private int id; //agent's id
    @Getter @Setter private String name; //agent's name
    private float funds; //current funds
    @Getter private final float startingFunds; //starting money amount
    @Getter private final int startingRound; //round agent started on
    @Getter @Setter int strategy; //what strategy plan the agent is on
    @Getter @Setter private float targetPrice; //what the agent believes is the fair value of the company
    @Setter private boolean prevPriceUp = false; //used by the VWAP and momentum class so they don't trade in the same direction consecutively
    private boolean agentLock; //concurrency lock
    private boolean placedBid; //if the agent has placed a bid offer on the order book
    private boolean placedAsk; //if the agent has placed an ask offer on the order book
    private final int MIN_STARTING_FUNDS = 1000; //minimum possible starting funds
    private final int MAX_STARTING_FUNDS = 8001000; //maximum possible starting funds
    private final ArrayList<OwnedGood> goodsOwned = new ArrayList<>(); //list to hold for owned shares
    @Getter private final ArrayList<String> namesOwned = new ArrayList<>(); //name of shares owned
    @Getter private final ArrayList<Float> fundData = new ArrayList<>(); //tracks the value of the agent's portfolio each round
    @Getter private final ArrayList<Offer> bidsPlaced = new ArrayList<>(); //list holding all current bid offers on the book
    @Getter private final ArrayList<Offer> asksPlaced = new ArrayList<>(); //list holding all current ask offers on the book

    /**
     * Constructor used for making all new agents
     */
    public Agent(){
        setAgentLock(false);
        Random rand = new Random();
        strategy = rand.nextInt(21); //chooses which strategy the agent is on
        id = nextID;
        nextID++;

        //all the if statements below assign the agents to their strategy plan
        if (strategy == 10) {
            strategy = 0;
        }
        if (strategy == 11) {
            strategy = 0;
        }
        if (strategy == 12) {
            strategy = 6;
        }
        if (strategy == 13) {
            strategy = 6;
        }
        if (strategy == 14) {
            strategy = 6;
        }
        if (strategy == 15) {
            strategy = 2;
        }
        if (strategy == 16) {
            strategy = 2;
        }
        if (strategy == 17) {
            strategy = 7;
        }

        //uses different mix on strategy based on the user's preference
        if(!volatility) {
            if (strategy == 1) {
                strategy = 0;
            }
            if (strategy == 3) {
                strategy = 8;
            }
            if (strategy == 9) {
                strategy = 8;
            }
            if (strategy == 18) {
                strategy = 7;
            }
            if (strategy == 19) {
                strategy = 6;
            }
            if (strategy == 20) {
                strategy = 2;
            }
        } else {
            if (strategy == 18) {
                strategy = 8;
            }
            if (strategy == 19) {
                strategy = 8;
            }
            if (strategy == 20) {
                strategy = 8;
            }
        }

        if (strategy == 1) {
            name = "Momentum " + id;
        } else if (strategy == 2) {
            name = "Sentiment Trend " + id;
        } else if (strategy == 3) {
            name = "VWAP " + id;
        } else if (strategy == 4) {
            name = "RSI " + id;
        } else if (strategy == 5) {
            name = "RSI10 " + id;
        } else if (strategy == 6) {
            name = "Offer Only " + id;
        } else if (strategy == 7) {
            name = "Both RSI " + id;
        } else if (strategy == 8) {
            name = "Aggressive Offers " + id;
        } else if (strategy == 9) {
            name = "VWAP and Momentum " + id;
        } else {
            name = "Default " + id;
        }
        funds = assignFunds();
        startingFunds = funds;
        createTargetPrice(); //creates target price around the starting offer price
        fundData.add(funds);
        this.startingRound = Session.getNumOfRounds();
        Session.getAgents().put(id,this); //adds the agent to the instance of Session
        setPrevPriceUp(false);
    }

    /**
     * This is the constructor used for the agent object to represent the company
     * @param name sets the name of the company
     * @param company to identify the constructor is for the company object
     */
    public Agent(String name, boolean company){
        setAgentLock(false);
        id = nextID;
        nextID++;
        this.name = name;
        funds = 0;
        startingFunds = funds;
        targetPrice = Good.getStartingPrice();
        fundData.add(funds);
        this.startingRound = Session.getNumOfRounds();
        Session.getAgents().put(id,this);
        setPrevPriceUp(false);
    }

    /**
     * Constructor used by the populateAgents() method in Session class
     * @param id id of the agent
     * @param name name of the agent
     * @param funds starting funds for the agent
     */
    public Agent(int id, String name, float funds){
        setAgentLock(false);
        setPlacedBid(false);
        setPlacedAsk(false);
        Random rand = new Random();
        strategy = rand.nextInt(7);
        this.id = id; //Make sure nextId is handled okay with concurrency
        this.name = name;
        this.funds = funds;
        startingFunds = funds;
        createTargetPrice();
        fundData.add(funds);
        this.startingRound = Session.getNumOfRounds();
        Session.getAgents().put(id,this);
        setPrevPriceUp(false);
    }

    /**
     * Sets the agents initial target price for the stock, target price can be created in a 10% range
     */
    public void createTargetPrice() {
        Random rand = new Random();
        int chance = rand.nextInt(10);
        if ((strategy == 1) || (strategy == 3) || (strategy == 9)) {
            //vwap and momentum don't want to buy at start
            targetPrice = (float) (((float) Math.round((chance + 80) * Good.getPrice())) * 0.01);
        } else {
            targetPrice = (float) (((float) Math.round((chance + 95) * Good.getPrice())) * 0.01);
        }
        placedAsk = false;
        placedBid = false;
    }

    /**
     * changes the agents target price, this is affected by the sentiment value,
     * a high sentiment value leads to a higher probability of a higher target price
     */
    public void changeTargetPrice() {
        Random rand = new Random();
        int chance = rand.nextInt(sentiment) + sentAdjust;
        targetPrice = (float) (((float) Math.round((chance + 91) * targetPrice)) * 0.01);
    }

    /**
     * this creates a random number between 0 and 200 and cubes it and adds 100
     * this then is assigned as the starting funds for the agent
     * @return The starting funds for the agent.
     */
    private float assignFunds(){
        Random rand = new Random();
        int fundsInt = rand.nextInt(201);
        return (float) (MIN_STARTING_FUNDS + (fundsInt * fundsInt * fundsInt));
    }

    //setters and getters
    public boolean getAgentLock() { return agentLock; }
    public void setAgentLock(boolean val) { agentLock = val; }
    public ArrayList<OwnedGood> getGoodsOwned() { return goodsOwned; }
    public boolean getPlacedAsk() { return placedAsk; }
    public boolean getPlacedBid() { return placedBid; }
    public void setPlacedAsk(boolean val) { this.placedAsk = val; }
    public void setPlacedBid(boolean val) { this.placedBid = val; }
    public void setFunds(float newFunds){ this.funds = newFunds; }
    public float getFunds() { return (((float)Math.round(this.funds * 100)) / 100); }
    public boolean getPrevPriceUp() { return prevPriceUp; }

    /**
     * bid has been removed from the order book, so the funds are given back to the agent
     * @param offer the offer that was removed
     */
    public void removedBid(Offer offer) {
        funds = (funds + (offer.getNumOffered() * offer.getPrice()));
        placedBid = false;
        changeTargetPrice();
    }

    /**
     * ask has been removed from the order book, the shares are returned to the agent
     * @param offer the ask offer that was removed
     */
    public void removeAsk(Offer offer) {
        for (OwnedGood good: goodsOwned) {
            if (offer.getGood().getId() == good.getGood().getId()) {
                good.setNumAvailable(good.getNumAvailable() + offer.getNumOffered());
            }
        }
        placedAsk = false;
        changeTargetPrice();
    }

    /**
     * used in the Initial Public Offering
     * allows the agent to decide if they want to buy shares from the offer
     * @param wantToBuy how many whares they want to buy
     * @param tc reference to the TradingCycle instance
     */
    public void CheckInitial(int wantToBuy, TradingCycle tc) {
        Offer offer = Good.getAsk().get(0);
        if (getFunds() < (wantToBuy * offer.getPrice())) {
            wantToBuy = (int) Math.floor(getFunds() / offer.getPrice());
        }
        if (wantToBuy > 0) {
            InitiateBuy ib1 = new InitiateBuy(this, offer, wantToBuy, tc);
            Runnable t1 = new Thread(ib1);
            t1.run();
        }
    }

    /**
     * Used to initiate the trade to buy shares from the company in the Initial Offering
     * @param buyer the agent that is buying
     * @param offer the offer from the company
     * @param amountBought the number of shares being bought
     * @param tc reference to the TradingCycle instance that can be notified when the trade is done
     */
    private record InitiateBuy(@Getter Agent buyer, @Getter Offer offer, @Getter int amountBought, TradingCycle tc) implements Runnable {

        @Override
        public synchronized void run() {
            try {
                Exchange.getInstance().execute(buyer, offer.getOfferMaker(), offer, amountBought, tc, 0);
            } catch (InterruptedException e) {
                LOGGER.info("trade failed");
            }
        }
    }

    /**
     * calculates the current portfolio value and adds it to the fundData list
     * @param price the current stock price
     */
    public void addValue(float price) {
        float value = funds;
        if (goodsOwned.size() > 0) {
            value += (goodsOwned.get(0).getNumOwned() * price);
        }
        fundData.add(value);
    }
}
