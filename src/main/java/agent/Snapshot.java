package agent;

/**
 * Per-round portfolio snapshot taken centrally by
 * {@link trade.TradingCycle#createTrades(int)} after all strategy threads have
 * completed. One snapshot per agent per round, regardless of whether the agent's
 * strategy executed that round — this gives the dashboard a fully-aligned time
 * series even for strategies that fire conditionally (RSI, VWAP+Momentum, etc.).
 *
 * <p>{@code fund} is the true total portfolio value: available cash + cash reserved
 * in open bids + total share value (including shares locked in open asks). This
 * means {@code fund} is stable even while bids or asks are resting on the book.
 */
public record Snapshot(int round, float fund, float cash, float cashInBids,
                       int shares, float sharesInAsksValue) {}
