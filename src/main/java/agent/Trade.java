package agent;

/**
 * Per-agent record of an executed trade. Captured by {@link trade.Exchange#execute}
 * after every successful fill so the post-run dashboard can show each agent's
 * trade history without needing a database.
 */
public record Trade(int round, Side side, int amount, float price,
                    int counterpartyId, String counterpartyName) {

    public enum Side { BUY, SELL }

    public float total() { return amount * price; }
}
