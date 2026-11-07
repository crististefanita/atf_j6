package ro.atf.holdem.ai;

import java.util.Objects;
import ro.atf.holdem.core.*;

/** Adversarial tournament role that deliberately tests premium short-stack shove responses. */
public final class ShortStackShovePolicy implements DecisionPolicy {
    public static final String VERSION_PREFIX = "SHORT-STACK-SHOVE-0.1-BB";
    private final int maxStackBigBlinds;
    private final TightAggressiveBaseline fallback = new TightAggressiveBaseline();

    public ShortStackShovePolicy(int maxStackBigBlinds) {
        if (maxStackBigBlinds < 1 || maxStackBigBlinds > 20)
            throw new IllegalArgumentException("maxStackBigBlinds must be 1..20");
        this.maxStackBigBlinds = maxStackBigBlinds;
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        boolean shortStack = (long) observation.heroStack() <= (long) observation.bigBlind() * maxStackBigBlinds;
        if (shortStack && observation.street() == Street.PREFLOP && premium(observation.holeCards())) {
            LegalAction allIn = observation.legalActions().stream()
                    .filter(action -> action.type() == ActionType.ALL_IN
                            && action.amount() == observation.heroStack())
                    .findFirst().orElse(null);
            if (allIn != null) {
                String response = observation.toCall() == 0 ? "unopened push"
                        : allIn.amount() > observation.toCall() ? "premium re-shove" : "premium short call";
                return new Decision(ActionType.ALL_IN, allIn.amount(), version(),
                        response + " at " + maxStackBigBlinds + " BB with a public short stack");
            }
        }
        return fallback.decide(observation);
    }

    public String version() { return VERSION_PREFIX + maxStackBigBlinds; }

    private static boolean premium(java.util.List<Card> hole) {
        if (hole.size() != 2) return false;
        int first = hole.get(0).rank().value();
        int second = hole.get(1).rank().value();
        int high = Math.max(first, second);
        int low = Math.min(first, second);
        return first == second && first >= Card.Rank.NINE.value()
                || high == Card.Rank.ACE.value() && low >= Card.Rank.TEN.value()
                || high >= Card.Rank.KING.value() && low >= Card.Rank.QUEEN.value();
    }
}
