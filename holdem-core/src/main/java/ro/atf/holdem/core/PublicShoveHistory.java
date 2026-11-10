package ro.atf.holdem.core;

import java.util.ArrayList;
import java.util.List;

/** Seat statistics from accepted public actions in completed hands of one tournament. */
public final class PublicShoveHistory {
    private final int[] opportunities;
    private final int[] raises;
    private final int[] pendingOpportunities;
    private final int[] pendingRaises;

    public PublicShoveHistory(int seats) {
        opportunities = new int[seats];
        raises = new int[seats];
        pendingOpportunities = new int[seats];
        pendingRaises = new int[seats];
    }

    public void recordAccepted(HoldemObservation before, ActionType action, int amount) {
        if (before.street() != Street.PREFLOP
                || !PublicOpponentTendency.inProfileStackRange(before.heroStack(), before.bigBlind())
                || before.legalActions().stream().noneMatch(option -> option.type() == ActionType.ALL_IN
                && option.amount() > before.toCall())) return;
        int seat = before.physicalHeroSeat();
        pendingOpportunities[seat]++;
        if (action == ActionType.ALL_IN && amount > before.toCall()) pendingRaises[seat]++;
    }

    public void settleHand() {
        for (int seat = 0; seat < opportunities.length; seat++) {
            opportunities[seat] += pendingOpportunities[seat];
            raises[seat] += pendingRaises[seat];
            pendingOpportunities[seat] = 0;
            pendingRaises[seat] = 0;
        }
    }

    public List<PublicOpponentTendency> snapshot() {
        var result = new ArrayList<PublicOpponentTendency>(opportunities.length);
        for (int seat = 0; seat < opportunities.length; seat++) {
            result.add(new PublicOpponentTendency(seat, opportunities[seat], raises[seat]));
        }
        return List.copyOf(result);
    }
}
