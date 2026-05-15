package agent;

/**
 * Per-agent record of an executed trade. Captured by {@link trade.Exchange#execute}
 * after every successful fill so the post-run dashboard can show each agent's
 * trade history without needing a database.
 *
 * <p>{@code role} records this agent's part in the trade: {@code MAKER} if the
 * fill consumed an offer this agent had standing on the book, {@code TAKER} if
 * this agent crossed the spread to hit a counterparty's offer. {@code fill}
 * distinguishes a fill that consumed the entire offer ({@code FULL}) from one
 * that left some quantity standing ({@code PARTIAL}).
 */
public record Trade(int round, Side side, int amount, float price,
                    int counterpartyId, String counterpartyName,
                    Role role, Fill fill) {

    public enum Side { BUY, SELL }
    public enum Role { MAKER, TAKER }
    public enum Fill { FULL, PARTIAL }

    public float total() { return amount * price; }
}
