package agent;

/**
 * Per-agent ledger entry for every order-book event the agent is the maker of:
 * placement, partial fill, full fill, or cancellation. Used by the dashboard's
 * BookEvents table to reconcile each agent's cash trajectory against its
 * order-book activity, independently of the trade history.
 *
 * <p>{@code fundsDelta} is the change in the agent's liquid {@code funds} caused
 * by this event. {@code fundsAfter} is the agent's {@code funds} after the
 * event was applied. Together they let the table show a running cash balance
 * that should add up to {@link Agent#getStartingFunds()} when summed with the
 * cash still locked in resting bids and the value of held shares.
 */
public record BookEvent(int round, Kind kind, Side side, float price, int qty,
                        float fundsDelta, float fundsAfter) {

    public enum Kind {
        /** Offer just added to the book; {@code fundsDelta} = -price*qty for bids, 0 for asks. */
        PLACED,
        /** Offer fully consumed by a counterparty; {@code qty} is what cleared on this fill. */
        FILLED_FULL,
        /** Offer partially consumed; {@code qty} is what cleared on this fill, offer stays on book. */
        FILLED_PARTIAL,
        /** Offer cancelled by {@code cleanOffers}; for bids, {@code fundsDelta} = +qty*price refund. */
        CANCELLED
    }

    public enum Side { BID, ASK }
}
