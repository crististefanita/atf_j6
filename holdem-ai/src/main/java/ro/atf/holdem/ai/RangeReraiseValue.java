package ro.atf.holdem.ai;

/** Chip-value approximation used to compare preflop re-raise quotes in the arena. */
public final class RangeReraiseValue {
    private RangeReraiseValue() {}

    public static double chips(int pot, int effective, RaiseEvSizing.Offer offer,
                               ConditionalRangeEquity.Estimate estimate, double equityDiscount,
                               boolean useRaiseRange) {
        int amount = offer.amount();
        double called = 0;
        if (estimate.call().probability() > 0)
            called = Math.max(0, estimate.call().equity() - equityDiscount)
                    * ((long) pot + amount + offer.opponentPrice()) - amount;

        double raised = -amount;
        if (useRaiseRange && estimate.raise().probability() > 0) {
            long extra = (long) effective - amount;
            double showdown = Math.max(0, estimate.raise().equity() - equityDiscount)
                    * (pot + (long) amount + offer.opponentPrice() + 2 * extra) - effective;
            raised = Math.max(raised, showdown);
        }
        return estimate.foldProbability() * pot + estimate.call().probability() * called
                + estimate.raise().probability() * raised;
    }
}
