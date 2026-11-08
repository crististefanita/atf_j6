package ro.atf.holdem.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import ro.atf.holdem.core.*;

/** Reproducible loose-aggressive style that prefers minimum legal pressure to large sizing. */
public final class LooseAggressivePolicy implements DecisionPolicy {
    public static final String VERSION = "LOOSE-AGGRESSIVE-0.2";

    private final int openFrequencyPercent;
    private final int raiseFrequencyPercent;
    private final int maxCallPotPercent;
    private final int latePositionAdjustmentPercent;

    public LooseAggressivePolicy(int openFrequencyPercent, int raiseFrequencyPercent, int maxCallPotPercent) {
        this(openFrequencyPercent, raiseFrequencyPercent, maxCallPotPercent, 0);
    }

    public LooseAggressivePolicy(int openFrequencyPercent, int raiseFrequencyPercent, int maxCallPotPercent,
                                 int latePositionAdjustmentPercent) {
        this.openFrequencyPercent = percent(openFrequencyPercent, "openFrequencyPercent");
        this.raiseFrequencyPercent = percent(raiseFrequencyPercent, "raiseFrequencyPercent");
        this.maxCallPotPercent = percent(maxCallPotPercent, "maxCallPotPercent");
        if (latePositionAdjustmentPercent < 0 || latePositionAdjustmentPercent > 50)
            throw new IllegalArgumentException("latePositionAdjustmentPercent must be 0..50");
        this.latePositionAdjustmentPercent = latePositionAdjustmentPercent;
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        int roll = roll(observation);
        var raise = observation.legalActions().stream().filter(action -> action.type() == ActionType.RAISE)
                .min(Comparator.comparingInt(LegalAction::minAmount)).orElse(null);
        boolean alreadyAggressed = hasAggressionThisStreet(observation);
        int openFrequency = openingFrequencyFor(observation);
        if (raise != null) {
            int frequency = alreadyAggressed ? raiseFrequencyPercent : openFrequency;
            if (roll < frequency)
                return new Decision(ActionType.RAISE, raise.minAmount(), version(), alreadyAggressed
                        ? "Continue after prior aggression at the configured rereraise frequency"
                        : "Open the unraised pot at the configured position-adjusted frequency");
        }

        var opening = observation.legalActions().stream().filter(action -> action.type() == ActionType.BET)
                .min(Comparator.comparingInt(LegalAction::minAmount)).orElse(null);
        if (opening != null && roll < openFrequency)
            return new Decision(ActionType.BET, opening.minAmount(), version(),
                    "Open the street at the configured position-adjusted frequency");

        for (LegalAction action : observation.legalActions()) {
            if (action.type() == ActionType.CHECK)
                return new Decision(ActionType.CHECK, 0, version(), "Check when the pressure mix declines to bet");
        }
        var call = observation.legalActions().stream().filter(action -> action.type() == ActionType.CALL)
                .findFirst().orElse(null);
        if (call != null && (long) observation.toCall() * 100
                <= (long) Math.max(1, observation.pot()) * maxCallPotPercent)
            return new Decision(ActionType.CALL, call.amount(), version(), "Continue within the configured pot-price cap");
        for (LegalAction action : observation.legalActions()) {
            if (action.type() == ActionType.FOLD)
                return new Decision(ActionType.FOLD, 0, version(), "Fold above the configured pot-price cap");
        }
        LegalAction fallback = observation.legalActions().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No legal action"));
        return new Decision(fallback.type(), fallback.amount(), version(), "Use the only available legal action");
    }

    private String version() {
        return VERSION + "-open" + openFrequencyPercent + "-raise" + raiseFrequencyPercent
                + "-call" + maxCallPotPercent + "-late" + latePositionAdjustmentPercent;
    }

    int openingFrequencyFor(HoldemObservation observation) {
        boolean late = DeterministicBluffPolicy.isLatePosition(observation.positionFromButton(),
                observation.playerCount(), 2);
        int adjustment = late ? latePositionAdjustmentPercent : -latePositionAdjustmentPercent;
        return Bounds.clamp(openFrequencyPercent + adjustment, 0, 100);
    }

    private static boolean hasAggressionThisStreet(HoldemObservation observation) {
        List<String> history = observation.publicActionHistory();
        int start = 0;
        if (observation.street() != Street.PREFLOP) {
            String marker = observation.street() + " [";
            for (int index = 0; index < history.size(); index++)
                if (history.get(index).startsWith(marker)) start = index + 1;
        }
        String prefix = observation.street() + " ";
        return history.subList(start, history.size()).stream().anyMatch(event -> event.startsWith(prefix)
                && (event.matches(prefix + "\\d+ BET \\d+") || event.matches(prefix + "\\d+ RAISE \\d+")));
    }

    private static int roll(HoldemObservation observation) {
        return Math.floorMod(Objects.hash(observation.handNumber(), observation.heroSeat(),
                observation.positionFromButton(), observation.street(), observation.holeCards(),
                observation.communityCards(), observation.publicActionHistory()), 100);
    }

    private static int percent(int value, String name) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be 0..100");
        return value;
    }
}
