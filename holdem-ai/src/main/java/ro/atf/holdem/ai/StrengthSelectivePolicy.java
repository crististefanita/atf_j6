package ro.atf.holdem.ai;

import java.util.List;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;

/** Arena opponent with hand-dependent preflop ranges and a shared draw across matched raise sizes. */
public final class StrengthSelectivePolicy implements DecisionPolicy {
    public enum Style {
        NARROW(49, 64, 47), ACTIVE(40, 57, 42),
        PRESSURE(36, 55, 41), BALANCED(44, 61, 45);

        private final int open;
        private final int raise;
        private final int call;

        Style(int open, int raise, int call) {
            this.open = open;
            this.raise = raise;
            this.call = call;
        }
    }

    private final Style style;
    private final TightAggressiveBaseline postflop = new TightAggressiveBaseline();

    public StrengthSelectivePolicy(Style style) {
        this.style = java.util.Objects.requireNonNull(style, "style");
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        if (observation.street() != Street.PREFLOP) return postflop.decide(observation);
        var legal = observation.legalActions();
        var raise = find(legal, ActionType.RAISE);
        boolean previouslyRaised = observation.publicActionHistory().stream()
                .anyMatch(event -> event.matches("PREFLOP \\d+ (RAISE|ALL_IN) \\d+"));
        int strength = strength(observation.holeCards()) + LooseAggressivePolicy.sharedPreflopRoll(observation) % 9 - 4;
        int price = observation.toCall();
        int odds = (int) (100L * price / Math.max(1L, observation.pot() + (long) price));
        if (previouslyRaised && strength >= style.raise + Math.max(0, odds - 25) / 3) {
            var allIn = find(legal, ActionType.ALL_IN);
            if (allIn != null && allIn.amount() > price && observation.heroStack() <= 12L * observation.bigBlind())
                return decision(allIn, allIn.amount(), "Strong short-stack shove over prior aggression");
            if (raise != null) return decision(raise, raise.minAmount(), "Strong hand raises over prior aggression");
        }
        if (!previouslyRaised && raise != null && strength >= style.open)
            return decision(raise, raise.minAmount(), "Hand strength clears the opening threshold");
        var check = find(legal, ActionType.CHECK);
        if (check != null) return decision(check, 0, "Check the weak range without paying");
        var call = find(legal, ActionType.CALL);
        if (call != null && strength >= style.call + Math.max(0, odds - 20) / 2)
            return decision(call, call.amount(), "Hand strength clears the actual pot price");
        var fold = find(legal, ActionType.FOLD);
        if (fold != null) return decision(fold, 0, "Insufficient hand strength at this price");
        var only = legal.stream().findFirst().orElseThrow();
        return decision(only, only.amount(), "Use the legal fallback");
    }

    private Decision decision(LegalAction action, int amount, String reason) {
        return new Decision(action.type(), amount, "SELECTIVE-1-" + style, reason);
    }

    private static LegalAction find(List<LegalAction> legal, ActionType type) {
        return legal.stream().filter(action -> action.type() == type).findFirst().orElse(null);
    }

    static int strength(List<Card> hole) {
        if (hole.size() != 2) return 0;
        var a = hole.get(0);
        var b = hole.get(1);
        int high = Math.max(a.rank().value(), b.rank().value());
        int low = Math.min(a.rank().value(), b.rank().value());
        if (high == low) return 43 + 3 * high;
        return 3 * high + low + (high == 14 ? 5 : 0) + (a.suit() == b.suit() ? 4 : 0)
                + (high - low == 1 ? 3 : 0);
    }
}
