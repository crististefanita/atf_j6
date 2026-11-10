package ro.atf.holdem.arena;

import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemObservation;

/** Arena-only one-step bet when public evidence clears a conservative chip-value threshold. */
final class ReadBasedBetPolicy implements DecisionPolicy {
    private final DecisionPolicy control;
    private final PublicBettingRead history;
    private final PublicBettingRead.Mode mode;
    private final double prior;
    private final int priorStrength;
    private final int minimumEvidence;
    private final int minGainPercent;

    ReadBasedBetPolicy(DecisionPolicy control, PublicBettingRead history, PublicBettingRead.Mode mode,
                       double prior, int priorStrength, int minimumEvidence, int minGainPercent) {
        this.control = control;
        this.history = history;
        this.mode = mode;
        this.prior = prior;
        this.priorStrength = priorStrength;
        this.minimumEvidence = minimumEvidence;
        this.minGainPercent = minGainPercent;
    }

    @Override public Decision decide(HoldemObservation observation) {
        var original = control.decide(observation);
        if (original.action() != ActionType.CHECK || observation.toCall() != 0
                || !PublicBettingRead.supportedStreet(observation)) return original;
        var bet = observation.legalActions().stream().filter(action -> action.type() == ActionType.BET)
                .findFirst().orElse(null);
        if (bet == null) return original;
        int opponent = PublicBettingRead.singleOpponent(observation);
        if (opponent < 0) return original;
        int pot = observation.pot();
        int amount = Math.max(bet.minAmount(), pot / 2);
        if (amount > bet.maxAmount() || !PublicBettingRead.comparable(amount, pot)
                || 10L * amount > observation.heroStack()
                || 5L * amount > PublicBettingRead.opponentStack(observation, opponent)) return original;
        var read = history.estimate(opponent, observation.handNumber(), mode, prior, priorStrength);
        if (mode != PublicBettingRead.Mode.FIXED && read.samples() < minimumEvidence) return original;
        double edge = read.fold() * pot - (1.0 - read.fold()) * amount;
        if (edge * 100 < (long) minGainPercent * pot) return original;
        return new Decision(ActionType.BET, amount, original.policyVersion() + "-public-" + mode,
                original.explanation() + " publicReadSeat=" + opponent + " publicReadN=" + read.samples()
                        + " publicReadFold=" + read.fold() + " publicReadEdge=" + edge);
    }
}
