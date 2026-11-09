package ro.atf.holdem.arena;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import ro.atf.holdem.ai.ContextualEquityPolicy;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Strict reader for reproducible policy-mix knobs used by offline evaluations. */
public record TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                     int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                     int looseMaxCallPotPercent, int looseBetPotPercent,
                                     int looseLatePositionAdjustmentPercent, int equitySamples,
                                     int cashCallMarginPercent, int tournamentCallMarginPercent,
                                     int equityRaiseEdgePercent, int valueBetThresholdPercent,
                                     ContextualEquityPolicy.ContextWeights equityContextWeights,
                                     ContextualEquityPolicy.HardPressure hardPressure) {
    private static final int DEFAULT_EQUITY_SAMPLES = 256;
    private static final int DEFAULT_CASH_MARGIN = 2;
    private static final int DEFAULT_TOURNAMENT_MARGIN = 8;
    private static final int DEFAULT_RAISE_EDGE = 18;
    private static final int DEFAULT_VALUE_THRESHOLD = 60;

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                  int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                  int looseMaxCallPotPercent, int looseBetPotPercent,
                                  int looseLatePositionAdjustmentPercent, int equitySamples,
                                  int cashCallMarginPercent, int tournamentCallMarginPercent,
                                  int equityRaiseEdgePercent, int valueBetThresholdPercent,
                                  ContextualEquityPolicy.ContextWeights equityContextWeights) {
        this(bluffFrequencyPercent, latePositionWindow, looseOpenFrequencyPercent, looseRaiseFrequencyPercent,
                looseMaxCallPotPercent, looseBetPotPercent, looseLatePositionAdjustmentPercent, equitySamples,
                cashCallMarginPercent, tournamentCallMarginPercent, equityRaiseEdgePercent, valueBetThresholdPercent,
                equityContextWeights, null);
    }

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                  int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                  int looseMaxCallPotPercent, int looseBetPotPercent,
                                  int looseLatePositionAdjustmentPercent, int equitySamples,
                                  int cashCallMarginPercent, int tournamentCallMarginPercent,
                                  int equityRaiseEdgePercent, int valueBetThresholdPercent) {
        this(bluffFrequencyPercent, latePositionWindow, looseOpenFrequencyPercent, looseRaiseFrequencyPercent,
                looseMaxCallPotPercent, looseBetPotPercent, looseLatePositionAdjustmentPercent, equitySamples,
                cashCallMarginPercent, tournamentCallMarginPercent, equityRaiseEdgePercent, valueBetThresholdPercent,
                ContextualEquityPolicy.ContextWeights.defaults());
    }

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow) {
        this(bluffFrequencyPercent, latePositionWindow, 45, 20, 35, 0, 0, DEFAULT_EQUITY_SAMPLES,
                DEFAULT_CASH_MARGIN, DEFAULT_TOURNAMENT_MARGIN, DEFAULT_RAISE_EDGE, DEFAULT_VALUE_THRESHOLD);
    }

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                  int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                  int looseMaxCallPotPercent) {
        this(bluffFrequencyPercent, latePositionWindow, looseOpenFrequencyPercent,
                looseRaiseFrequencyPercent, looseMaxCallPotPercent, 0, 0, DEFAULT_EQUITY_SAMPLES,
                DEFAULT_CASH_MARGIN, DEFAULT_TOURNAMENT_MARGIN, DEFAULT_RAISE_EDGE, DEFAULT_VALUE_THRESHOLD);
    }

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                  int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                  int looseMaxCallPotPercent, int looseBetPotPercent) {
        this(bluffFrequencyPercent, latePositionWindow, looseOpenFrequencyPercent,
                looseRaiseFrequencyPercent, looseMaxCallPotPercent, looseBetPotPercent, 0, DEFAULT_EQUITY_SAMPLES,
                DEFAULT_CASH_MARGIN, DEFAULT_TOURNAMENT_MARGIN, DEFAULT_RAISE_EDGE, DEFAULT_VALUE_THRESHOLD);
    }

    public TournamentPolicyConfig(int bluffFrequencyPercent, int latePositionWindow,
                                  int looseOpenFrequencyPercent, int looseRaiseFrequencyPercent,
                                  int looseMaxCallPotPercent, int looseBetPotPercent,
                                  int looseLatePositionAdjustmentPercent) {
        this(bluffFrequencyPercent, latePositionWindow, looseOpenFrequencyPercent, looseRaiseFrequencyPercent,
                looseMaxCallPotPercent, looseBetPotPercent, looseLatePositionAdjustmentPercent,
                DEFAULT_EQUITY_SAMPLES, DEFAULT_CASH_MARGIN, DEFAULT_TOURNAMENT_MARGIN,
                DEFAULT_RAISE_EDGE, DEFAULT_VALUE_THRESHOLD);
    }

    public static TournamentPolicyConfig load(Path path) throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (var input = Files.newBufferedReader(path)) {
            Object document = new Yaml(new SafeConstructor(options)).load(input);
            if (!(document instanceof Map<?, ?> root) || !Integer.valueOf(1).equals(root.get("schemaVersion"))
                    || !(root.get("bluff") instanceof Map<?, ?> bluff)
                    || !bluff.keySet().equals(java.util.Set.of("frequencyPercent", "latePositionWindow"))
                    || !(bluff.get("frequencyPercent") instanceof Integer frequency)
                    || !(bluff.get("latePositionWindow") instanceof Integer window)
                    || !(root.get("looseAggressive") instanceof Map<?, ?> loose)
                    || !loose.keySet().equals(java.util.Set.of("openFrequencyPercent", "raiseFrequencyPercent",
                    "maxCallPotPercent", "betPotPercent", "latePositionAdjustmentPercent"))
                    || !(loose.get("openFrequencyPercent") instanceof Integer open)
                    || !(loose.get("raiseFrequencyPercent") instanceof Integer raise)
                    || !(loose.get("maxCallPotPercent") instanceof Integer callPot)
                    || !(loose.get("betPotPercent") instanceof Integer betPot)
                    || !(loose.get("latePositionAdjustmentPercent") instanceof Integer lateAdjustment)) {
                throw new IllegalArgumentException("Expected versioned bluff and looseAggressive policy settings");
            }
            if (!(root.get("equityDecision") instanceof Map<?, ?> equity)
                    || !equity.keySet().equals(java.util.Set.of("samples", "cashCallMarginPercent",
                    "tournamentCallMarginPercent", "raiseEdgePercent", "valueBetThresholdPercent", "contextWeights"))
                    || !(equity.get("samples") instanceof Integer samples)
                    || !(equity.get("cashCallMarginPercent") instanceof Integer cashMargin)
                    || !(equity.get("tournamentCallMarginPercent") instanceof Integer tournamentMargin)
                    || !(equity.get("raiseEdgePercent") instanceof Integer raiseEdge)
                    || !(equity.get("valueBetThresholdPercent") instanceof Integer valueThreshold))
                throw new IllegalArgumentException("Expected exact equityDecision settings");
            if (!(equity.get("contextWeights") instanceof Map<?, ?> weights)
                    || !weights.keySet().equals(java.util.Set.of("extraOpponentCallMargin", "opponentAggressionCallMargin",
                    "raiseMarginPerExtraOpponent", "extraOpponentValueBetMargin", "earlyPositionCallMargin",
                    "latePositionCallDiscount", "earlyPositionValueBetMargin", "latePositionValueBetDiscount",
                    "opponentAggressionValueBetMargin", "tournamentValueBetMargin",
                    "uncertaintyZHundredths", "largeBetCallMargin", "opponentAllInCallMargin",
                    "tournamentEliminationRiskMargin", "tournamentShortStackCallDiscount",
                    "tournamentCommitmentThresholdPercent", "shortStackShovePriorProbabilityPercent",
                    "shortStackShovePriorOpportunities", "shortStackShoveWarmupOpportunities",
                    "shortStackShoveSensitivity", "shortStackShoveMaxMarginAdjustment")))
                throw new IllegalArgumentException("Expected exact equityDecision.contextWeights settings");
            var contextWeights = new ContextualEquityPolicy.ContextWeights(
                    integer(weights, "extraOpponentCallMargin"), integer(weights, "opponentAggressionCallMargin"),
                    integer(weights, "raiseMarginPerExtraOpponent"), integer(weights, "extraOpponentValueBetMargin"),
                    integer(weights, "earlyPositionCallMargin"), integer(weights, "latePositionCallDiscount"),
                    integer(weights, "earlyPositionValueBetMargin"), integer(weights, "latePositionValueBetDiscount"),
                    integer(weights, "opponentAggressionValueBetMargin"), integer(weights, "tournamentValueBetMargin"),
                    integer(weights, "uncertaintyZHundredths"), integer(weights, "largeBetCallMargin"),
                    integer(weights, "opponentAllInCallMargin"), integer(weights, "tournamentEliminationRiskMargin"),
                    integer(weights, "tournamentShortStackCallDiscount"),
                    integer(weights, "tournamentCommitmentThresholdPercent"),
                    integer(weights, "shortStackShovePriorProbabilityPercent"),
                    integer(weights, "shortStackShovePriorOpportunities"),
                    integer(weights, "shortStackShoveWarmupOpportunities"),
                    integer(weights, "shortStackShoveSensitivity"),
                    integer(weights, "shortStackShoveMaxMarginAdjustment"));
            return new TournamentPolicyConfig(frequency, window, open, raise, callPot, betPot, lateAdjustment,
                    samples, cashMargin, tournamentMargin, raiseEdge, valueThreshold, contextWeights, hardPressure(root));
        } catch (org.yaml.snakeyaml.error.YAMLException failure) {
            throw new IllegalArgumentException("Invalid policy YAML", failure);
        }
    }

    private static ContextualEquityPolicy.HardPressure hardPressure(Map<?, ?> root) {
        if (!root.containsKey("hardPressure")) return null;
        if (!(root.get("hardPressure") instanceof Map<?, ?> hard))
            throw new IllegalArgumentException("Expected hardPressure settings");
        var required = java.util.Set.of("cheapPreflopDiscount", "fullPotEquity", "doublePotEquity", "triplePotEquity");
        var keys = new java.util.HashSet<>(hard.keySet());
        keys.remove("bigBlindValueRaise");
        keys.remove("raiseContext");
        if (!keys.equals(required))
            throw new IllegalArgumentException("Expected exact hardPressure settings");
        Object bigBlindValueRaise = hard.containsKey("bigBlindValueRaise") ? hard.get("bigBlindValueRaise") : false;
        if (!(bigBlindValueRaise instanceof Boolean enabled))
            throw new IllegalArgumentException("Expected boolean bigBlindValueRaise");
        return new ContextualEquityPolicy.HardPressure(integer(hard, "cheapPreflopDiscount"),
                integer(hard, "fullPotEquity"), integer(hard, "doublePotEquity"), integer(hard, "triplePotEquity"),
                enabled, raiseContext(hard));
    }

    private static ContextualEquityPolicy.RaiseContext raiseContext(Map<?, ?> hard) {
        if (!hard.containsKey("raiseContext")) return null;
        if (!(hard.get("raiseContext") instanceof Map<?, ?> context)
                || !context.keySet().equals(java.util.Set.of("baseMargin", "multiwayMargin", "positionMargin",
                    "aggressionMargin", "largeBetMargin", "allInMargin", "commitmentMargin",
                    "commitmentThresholdPercent")))
            throw new IllegalArgumentException("Expected exact raiseContext settings");
        return new ContextualEquityPolicy.RaiseContext(integer(context, "baseMargin"),
                integer(context, "multiwayMargin"), integer(context, "positionMargin"),
                integer(context, "aggressionMargin"), integer(context, "largeBetMargin"),
                integer(context, "allInMargin"), integer(context, "commitmentMargin"),
                integer(context, "commitmentThresholdPercent"));
    }

    public TournamentPolicyConfig {
        if (bluffFrequencyPercent < 0 || bluffFrequencyPercent > 100)
            throw new IllegalArgumentException("Bluff frequency must be 0..100");
        if (latePositionWindow < 1 || latePositionWindow > 6)
            throw new IllegalArgumentException("Late-position window must be 1..6 seats");
        validatePercent(looseOpenFrequencyPercent, "looseOpenFrequencyPercent");
        validatePercent(looseRaiseFrequencyPercent, "looseRaiseFrequencyPercent");
        validatePercent(looseMaxCallPotPercent, "looseMaxCallPotPercent");
        if (looseBetPotPercent < 0 || looseBetPotPercent > 200)
            throw new IllegalArgumentException("looseBetPotPercent must be 0..200");
        if (looseLatePositionAdjustmentPercent < 0 || looseLatePositionAdjustmentPercent > 50)
            throw new IllegalArgumentException("looseLatePositionAdjustmentPercent must be 0..50");
        if (equitySamples < 32 || equitySamples > 100_000)
            throw new IllegalArgumentException("equitySamples must be 32..100000");
        validatePercent(cashCallMarginPercent, "cashCallMarginPercent");
        validatePercent(tournamentCallMarginPercent, "tournamentCallMarginPercent");
        validatePercent(equityRaiseEdgePercent, "equityRaiseEdgePercent");
        validatePercent(valueBetThresholdPercent, "valueBetThresholdPercent");
        java.util.Objects.requireNonNull(equityContextWeights, "equityContextWeights");
    }

    private static int integer(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof Integer number) return number;
        throw new IllegalArgumentException("Expected integer " + key);
    }

    private static void validatePercent(int value, String name) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be 0..100");
    }
}
