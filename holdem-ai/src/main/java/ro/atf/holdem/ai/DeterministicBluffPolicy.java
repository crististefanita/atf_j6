package ro.atf.holdem.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import ro.atf.holdem.core.*;

/** Small reproducible mixed policy for exercising bets without a premium hole card. */
public final class DeterministicBluffPolicy implements DecisionPolicy {
    public static final String VERSION = "BLUFF-MIXED-0.2";
    private static final int DEFAULT_LATE_POSITION_FREQUENCY = 18;
    private static final int DEFAULT_LATE_POSITION_WINDOW = 2;

    private final DecisionPolicy fallback;
    private final int latePositionFrequency;
    private final int latePositionWindow;

    public DeterministicBluffPolicy() {
        this(new TightAggressiveBaseline(), DEFAULT_LATE_POSITION_FREQUENCY, DEFAULT_LATE_POSITION_WINDOW);
    }

    public DeterministicBluffPolicy(int latePositionFrequency, int latePositionWindow) {
        this(new TightAggressiveBaseline(), latePositionFrequency, latePositionWindow);
    }

    public DeterministicBluffPolicy(DecisionPolicy fallback, int latePositionFrequency, int latePositionWindow) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        if (latePositionFrequency < 0 || latePositionFrequency > 100) throw new IllegalArgumentException("Bluff frequency must be 0..100");
        if (latePositionWindow < 1) throw new IllegalArgumentException("Late-position window must be positive");
        this.latePositionFrequency = latePositionFrequency;
        this.latePositionWindow = latePositionWindow;
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        var opening = observation.legalActions().stream()
                .filter(action -> action.type() == ActionType.BET)
                .min(Comparator.comparingInt(LegalAction::minAmount))
                .orElse(null);
        boolean noAce = observation.holeCards().stream()
                .noneMatch(card -> card.rank() == Card.Rank.ACE);
        boolean noPrivatePair = noPrivatePair(observation);
        boolean late = isLatePosition(observation.positionFromButton(), observation.playerCount(), latePositionWindow);
        boolean checkedToHero = checkedToHero(observation);
        boolean visibleAce = observation.communityCards().stream()
                .anyMatch(card -> card.rank() == Card.Rank.ACE);

        if (opening != null && noAce && noPrivatePair && late && visibleAce && checkedToHero
                && observation.street() != Street.PREFLOP
                && PolicySeed.rollPercent(observation) < latePositionFrequency) {
            return new Decision(ActionType.BET, opening.minAmount(), VERSION,
                    "Deterministic scare-card bluff after checks, no ace in hole cards");
        }
        Decision decision = fallback.decide(observation);
        return new Decision(decision.action(), decision.amount(), VERSION,
                "Fallback: " + decision.explanation());
    }

    /** Position zero is the button and therefore acts last after the flop in this table model. */
    static boolean isLatePosition(int positionFromButton, int playerCount, int window) {
        if (positionFromButton < 0 || positionFromButton >= playerCount || window < 1)
            throw new IllegalArgumentException("Invalid position window");
        int effectiveWindow = Math.min(window, playerCount);
        return positionFromButton == 0 || positionFromButton >= playerCount - effectiveWindow + 1;
    }

    private static boolean checkedToHero(HoldemObservation observation) {
        String street = observation.street().name();
        List<String> history = observation.publicActionHistory();
        int boardIndex = -1;
        for (int index = 0; index < history.size(); index++) {
            if (history.get(index).startsWith(street + " [")) boardIndex = index;
        }
        if (boardIndex < 0) return false;
        List<String> streetActions = history.subList(boardIndex + 1, history.size()).stream()
                .filter(event -> event.startsWith(street + " ")).collect(Collectors.toList());
        return !streetActions.isEmpty() && streetActions.stream()
                .allMatch(event -> event.matches(street + " \\d+ CHECK 0"));
    }

    private static boolean noPrivatePair(HoldemObservation observation) {
        if (observation.holeCards().size() != 2 || observation.communityCards().size() < 3) return false;
        Card.Rank first = observation.holeCards().get(0).rank();
        Card.Rank second = observation.holeCards().get(1).rank();
        return first != second && observation.communityCards().stream()
                .noneMatch(card -> card.rank() == first || card.rank() == second);
    }
}
