package ro.atf.holdem.ai;

import ro.atf.holdem.core.*;

import java.util.Comparator;

/** Deliberately small baseline; not a claim of strong poker play. */
public final class TightAggressiveBaseline implements DecisionPolicy {
    public static final String VERSION = "TAG-L0-0.1";

    @Override
    public Decision decide(HoldemObservation observation) {
        if (observation.legalActions().isEmpty()) throw new IllegalArgumentException("No legal action");
        boolean hasPair = observation.holeCards().size() == 2 && observation.holeCards().get(0).rank() == observation.holeCards().get(1).rank();
        boolean hasAce = observation.holeCards().stream().anyMatch(card -> card.rank() == Card.Rank.ACE);
        LegalAction raise = observation.legalActions().stream().filter(a -> a.type() == ActionType.RAISE).max(Comparator.comparingInt(LegalAction::maxAmount)).orElse(null);
        LegalAction call = observation.legalActions().stream().filter(a -> a.type() == ActionType.CALL).findFirst().orElse(null);
        LegalAction check = observation.legalActions().stream().filter(a -> a.type() == ActionType.CHECK).findFirst().orElse(null);
        LegalAction fold = observation.legalActions().stream().filter(a -> a.type() == ActionType.FOLD).findFirst().orElse(null);

        if (observation.street() == Street.PREFLOP && (hasPair || hasAce) && raise != null) {
            int amount = Math.max(raise.minAmount(), Math.min(raise.maxAmount(), Math.max(observation.pot() * 2, raise.minAmount())));
            return new Decision(ActionType.RAISE, amount, VERSION, "L0 premium preflop holding");
        }
        if (check != null && observation.toCall() == 0) return new Decision(ActionType.CHECK, 0, VERSION, "L0 no-cost continuation");
        if (call != null && observation.toCall() <= Math.max(1, observation.pot() / 3)) return new Decision(ActionType.CALL, call.amount(), VERSION, "L0 bounded pot-price call");
        if (fold != null) return new Decision(ActionType.FOLD, 0, VERSION, "L0 conservative fallback");
        return new Decision(observation.legalActions().get(0).type(), observation.legalActions().get(0).amount(), VERSION, "L0 first legal fallback");
    }
}
