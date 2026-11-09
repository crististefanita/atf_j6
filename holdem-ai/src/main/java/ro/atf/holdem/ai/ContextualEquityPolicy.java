package ro.atf.holdem.ai;

import java.util.*;
import java.util.stream.Collectors;
import ro.atf.holdem.core.*;

/** Uses sampled random-hand equity, pot price, live opponents, aggression and position. */
public final class ContextualEquityPolicy implements DecisionPolicy {
    public enum GameMode { CASH, TOURNAMENT }

    public record RaiseContext(int baseMargin, int multiwayMargin, int positionMargin, int aggressionMargin,
                               int largeBetMargin, int allInMargin, int commitmentMargin,
                               int commitmentThresholdPercent) {
        public RaiseContext {
            percent(baseMargin, "raise baseMargin");
            percent(multiwayMargin, "raise multiwayMargin");
            percent(positionMargin, "raise positionMargin");
            percent(aggressionMargin, "raise aggressionMargin");
            percent(largeBetMargin, "raise largeBetMargin");
            percent(allInMargin, "raise allInMargin");
            percent(commitmentMargin, "raise commitmentMargin");
            if (commitmentThresholdPercent < 50 || commitmentThresholdPercent > 100)
                throw new IllegalArgumentException("Raise commitmentThresholdPercent must be 50..100");
        }

        private String version() {
            return baseMargin + "." + multiwayMargin + "." + positionMargin + "." + aggressionMargin + "."
                    + largeBetMargin + "." + allInMargin + "." + commitmentMargin + "." + commitmentThresholdPercent;
        }
    }

    public record HardPressure(int cheapPreflopDiscount, int fullPotEquity, int doublePotEquity, int triplePotEquity,
                               boolean bigBlindValueRaise, RaiseContext raiseContext,
                               PreflopResponseModel responseModel, RaiseEvSizing.Settings raiseEv) {
        public static final HardPressure TOURNAMENT = new HardPressure(3, 76, 86, 94, false,
                new RaiseContext(8, 5, 2, 3, 3, 6, 8, 75));

        public HardPressure(int cheapPreflopDiscount, int fullPotEquity, int doublePotEquity, int triplePotEquity) {
            this(cheapPreflopDiscount, fullPotEquity, doublePotEquity, triplePotEquity, false);
        }

        public HardPressure(int cheapPreflopDiscount, int fullPotEquity, int doublePotEquity, int triplePotEquity,
                            boolean bigBlindValueRaise) {
            this(cheapPreflopDiscount, fullPotEquity, doublePotEquity, triplePotEquity, bigBlindValueRaise, null);
        }

        public HardPressure(int cheapPreflopDiscount, int fullPotEquity, int doublePotEquity, int triplePotEquity,
                            boolean bigBlindValueRaise, RaiseContext raiseContext) {
            this(cheapPreflopDiscount, fullPotEquity, doublePotEquity, triplePotEquity, bigBlindValueRaise,
                    raiseContext, null);
        }

        public HardPressure(int cheapPreflopDiscount, int fullPotEquity, int doublePotEquity, int triplePotEquity,
                            boolean bigBlindValueRaise, RaiseContext raiseContext, PreflopResponseModel responseModel) {
            this(cheapPreflopDiscount, fullPotEquity, doublePotEquity, triplePotEquity, bigBlindValueRaise,
                    raiseContext, responseModel, null);
        }

        public HardPressure {
            if (cheapPreflopDiscount < 0 || cheapPreflopDiscount > 12
                    || fullPotEquity < 70 || doublePotEquity <= fullPotEquity
                    || triplePotEquity <= doublePotEquity || triplePotEquity > 100)
                throw new IllegalArgumentException("Invalid Hard pressure thresholds");
            if (responseModel != null && raiseContext == null)
                throw new IllegalArgumentException("Response model requires a separate raise context");
            if (raiseEv != null && raiseEv.nearby() != null && (raiseContext == null || responseModel != null))
                throw new IllegalArgumentException("Nearby raise EV requires a raise context and its own paired responses");
            if (raiseEv != null && raiseEv.nearby() == null
                    && (responseModel == null || responseModel.foldWeight() != 0 || responseModel.raiseWeight() != 0))
                throw new IllegalArgumentException("Raise EV requires response probabilities without threshold weights");
        }
    }

    public record ContextWeights(int extraOpponentCallMargin, int opponentAggressionCallMargin,
                                 int raiseMarginPerExtraOpponent, int extraOpponentValueBetMargin,
                                 int earlyPositionCallMargin, int latePositionCallDiscount,
                                 int earlyPositionValueBetMargin, int latePositionValueBetDiscount,
                                 int opponentAggressionValueBetMargin, int tournamentValueBetMargin,
                                 int uncertaintyZHundredths, int largeBetCallMargin,
                                 int opponentAllInCallMargin, int tournamentEliminationRiskMargin,
                                 int tournamentShortStackCallDiscount, int tournamentCommitmentThresholdPercent,
                                 int shortStackShovePriorProbabilityPercent, int shortStackShovePriorOpportunities,
                                 int shortStackShoveWarmupOpportunities, int shortStackShoveSensitivity,
                                 int shortStackShoveMaxMarginAdjustment) {
        public ContextWeights {
            percent(extraOpponentCallMargin, "extraOpponentCallMargin");
            percent(opponentAggressionCallMargin, "opponentAggressionCallMargin");
            percent(raiseMarginPerExtraOpponent, "raiseMarginPerExtraOpponent");
            percent(extraOpponentValueBetMargin, "extraOpponentValueBetMargin");
            percent(earlyPositionCallMargin, "earlyPositionCallMargin");
            percent(latePositionCallDiscount, "latePositionCallDiscount");
            percent(earlyPositionValueBetMargin, "earlyPositionValueBetMargin");
            percent(latePositionValueBetDiscount, "latePositionValueBetDiscount");
            percent(opponentAggressionValueBetMargin, "opponentAggressionValueBetMargin");
            percent(tournamentValueBetMargin, "tournamentValueBetMargin");
            percent(largeBetCallMargin, "largeBetCallMargin");
            percent(opponentAllInCallMargin, "opponentAllInCallMargin");
            percent(tournamentEliminationRiskMargin, "tournamentEliminationRiskMargin");
            percent(tournamentShortStackCallDiscount, "tournamentShortStackCallDiscount");
            percent(shortStackShovePriorProbabilityPercent, "shortStackShovePriorProbabilityPercent");
            percent(shortStackShoveSensitivity, "shortStackShoveSensitivity");
            if (uncertaintyZHundredths < 0 || uncertaintyZHundredths > 500)
                throw new IllegalArgumentException("uncertaintyZHundredths must be 0..500");
            if (tournamentCommitmentThresholdPercent < 50 || tournamentCommitmentThresholdPercent > 100)
                throw new IllegalArgumentException("tournamentCommitmentThresholdPercent must be 50..100");
            if (shortStackShovePriorOpportunities < 1 || shortStackShovePriorOpportunities > 1000
                    || shortStackShoveWarmupOpportunities < 1 || shortStackShoveWarmupOpportunities > 100
                    || shortStackShoveMaxMarginAdjustment < 0 || shortStackShoveMaxMarginAdjustment > 20)
                throw new IllegalArgumentException("Invalid short-stack shove history calibration");
        }

        public ContextWeights(int extraOpponentCallMargin, int opponentAggressionCallMargin,
                              int raiseMarginPerExtraOpponent, int extraOpponentValueBetMargin,
                              int earlyPositionCallMargin, int latePositionCallDiscount,
                              int earlyPositionValueBetMargin, int latePositionValueBetDiscount,
                              int opponentAggressionValueBetMargin, int tournamentValueBetMargin,
                              int uncertaintyZHundredths) {
            this(extraOpponentCallMargin, opponentAggressionCallMargin, raiseMarginPerExtraOpponent,
                    extraOpponentValueBetMargin, earlyPositionCallMargin, latePositionCallDiscount,
                    earlyPositionValueBetMargin, latePositionValueBetDiscount, opponentAggressionValueBetMargin,
                    tournamentValueBetMargin, uncertaintyZHundredths, 3, 6, 8, 5, 75,
                    10, 10, 3, 16, 4);
        }

        public ContextWeights(int extraOpponentCallMargin, int opponentAggressionCallMargin,
                              int raiseMarginPerExtraOpponent, int extraOpponentValueBetMargin,
                              int earlyPositionCallMargin, int latePositionCallDiscount,
                              int earlyPositionValueBetMargin, int latePositionValueBetDiscount,
                              int opponentAggressionValueBetMargin, int tournamentValueBetMargin,
                              int uncertaintyZHundredths, int largeBetCallMargin,
                              int opponentAllInCallMargin, int tournamentEliminationRiskMargin,
                              int tournamentShortStackCallDiscount, int tournamentCommitmentThresholdPercent) {
            this(extraOpponentCallMargin, opponentAggressionCallMargin, raiseMarginPerExtraOpponent,
                    extraOpponentValueBetMargin, earlyPositionCallMargin, latePositionCallDiscount,
                    earlyPositionValueBetMargin, latePositionValueBetDiscount, opponentAggressionValueBetMargin,
                    tournamentValueBetMargin, uncertaintyZHundredths, largeBetCallMargin,
                    opponentAllInCallMargin, tournamentEliminationRiskMargin, tournamentShortStackCallDiscount,
                    tournamentCommitmentThresholdPercent, 10, 10, 3, 16, 4);
        }

        public static ContextWeights defaults() {
            return new ContextWeights(3, 3, 2, 5, 2, 2, 3, 3, 2, 2, 164, 3, 6, 8, 5, 75,
                    10, 10, 3, 16, 4);
        }

        public ContextWeights withShortStackShoveSensitivity(int sensitivity) {
            return new ContextWeights(extraOpponentCallMargin, opponentAggressionCallMargin,
                    raiseMarginPerExtraOpponent, extraOpponentValueBetMargin, earlyPositionCallMargin,
                    latePositionCallDiscount, earlyPositionValueBetMargin, latePositionValueBetDiscount,
                    opponentAggressionValueBetMargin, tournamentValueBetMargin, uncertaintyZHundredths,
                    largeBetCallMargin, opponentAllInCallMargin, tournamentEliminationRiskMargin,
                    tournamentShortStackCallDiscount, tournamentCommitmentThresholdPercent,
                    shortStackShovePriorProbabilityPercent, shortStackShovePriorOpportunities,
                    shortStackShoveWarmupOpportunities, sensitivity, shortStackShoveMaxMarginAdjustment);
        }
    }

    public static final String VERSION = "EQUITY-MC-0.4";
    private final MonteCarloEquityEstimator estimator = new MonteCarloEquityEstimator();
    private final int samples;
    private final int cashRiskMarginPercent;
    private final int tournamentRiskMarginPercent;
    private final int raiseEdgePercent;
    private final int valueBetThresholdPercent;
    private final ContextWeights contextWeights;
    private final GameMode gameMode;
    private final HardPressure hardPressure;
    private final String responseVersion;

    public ContextualEquityPolicy(int samples, int cashRiskMarginPercent, int tournamentRiskMarginPercent,
                                  int raiseEdgePercent, int valueBetThresholdPercent, GameMode gameMode) {
        this(samples, cashRiskMarginPercent, tournamentRiskMarginPercent, raiseEdgePercent,
                valueBetThresholdPercent, gameMode, ContextWeights.defaults());
    }

    public ContextualEquityPolicy(int samples, int cashRiskMarginPercent, int tournamentRiskMarginPercent,
                                  int raiseEdgePercent, int valueBetThresholdPercent, GameMode gameMode,
                                  ContextWeights contextWeights) {
        this(samples, cashRiskMarginPercent, tournamentRiskMarginPercent, raiseEdgePercent,
                valueBetThresholdPercent, gameMode, contextWeights, null);
    }

    public ContextualEquityPolicy(int samples, int cashRiskMarginPercent, int tournamentRiskMarginPercent,
                                  int raiseEdgePercent, int valueBetThresholdPercent, GameMode gameMode,
                                  ContextWeights contextWeights, HardPressure hardPressure) {
        if (samples < 32 || samples > 100_000) throw new IllegalArgumentException("samples must be 32..100000");
        percent(cashRiskMarginPercent, "cashRiskMarginPercent");
        percent(tournamentRiskMarginPercent, "tournamentRiskMarginPercent");
        percent(raiseEdgePercent, "raiseEdgePercent");
        percent(valueBetThresholdPercent, "valueBetThresholdPercent");
        this.samples = samples;
        this.cashRiskMarginPercent = cashRiskMarginPercent;
        this.tournamentRiskMarginPercent = tournamentRiskMarginPercent;
        this.raiseEdgePercent = raiseEdgePercent;
        this.valueBetThresholdPercent = valueBetThresholdPercent;
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
        this.contextWeights = Objects.requireNonNull(contextWeights, "contextWeights");
        this.hardPressure = hardPressure;
        var responseModel = hardPressure == null ? null : hardPressure.responseModel();
        var ev = hardPressure == null ? null : hardPressure.raiseEv();
        if ((responseModel != null || ev != null) && gameMode != GameMode.TOURNAMENT)
            throw new IllegalArgumentException("Response model requires tournament mode");
        this.responseVersion = (responseModel == null ? "" : "-response." + responseModel.version())
                + (ev == null ? "" : "-ev." + ev.calledEquityDiscountPp() + "." + ev.minEdgePercentOfBigBlind()
                        + "." + (ev.nearby() == null
                                ? ev.potPercents().stream().map(String::valueOf).collect(Collectors.joining("."))
                                : "near." + ev.nearby().version()));
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        List<LegalAction> legal = observation.legalActions();
        if (legal.isEmpty()) throw new IllegalArgumentException("No legal action");
        if (observation.activeOpponentCount() < 1)
            return chooseSafeFallback(observation, "No live opponent remains");

        var estimate = estimator.estimate(observation, samples);
        var pressure = callPressure(observation);
        var stackRisk = stackRisk(observation, contextWeights.tournamentCommitmentThresholdPercent());
        int shoveHistoryAdjustment = historicalShoveMarginAdjustment(observation, pressure);
        double equity = estimate.expectedShare() * 100.0;
        int callRequired = requiredEquityPercent(observation, estimate.standardError(), pressure, stackRisk,
                shoveHistoryAdjustment);
        var raiseContext = gameMode == GameMode.TOURNAMENT && hardPressure != null ? hardPressure.raiseContext() : null;
        String metrics = String.format(Locale.ROOT,
                "equity=%.1f%% win=%.1f%% tie=%.1f%% potOdds=%.1f%% required=%.1f%% samples=%d se=%.1f%% opponents=%d mode=%s pressurePotSized=%s opponentAllIn=%s shoveHistoryMargin=%+d callCommitment=%d%% covered=%s shortStack=%s",
                equity, estimate.winProbability() * 100.0, estimate.tieProbability() * 100.0,
                potOddsPercent(observation), (double) callRequired, estimate.samples(),
                estimate.standardError() * 100.0, observation.activeOpponentCount(), gameMode,
                pressure.potSizedAggression(), pressure.opponentAllInAggression(), shoveHistoryAdjustment,
                stackRisk.commitmentPercent(),
                stackRisk.opponentCanCover(), stackRisk.shortStack());

        if (observation.toCall() > 0) {
            LegalAction raise = find(legal, ActionType.RAISE);
            if (raise != null) {
                int raiseThreshold = raiseContext == null
                        ? Math.min(99, callRequired + raiseEdgePercent + (observation.activeOpponentCount() - 1)
                                * contextWeights.raiseMarginPerExtraOpponent())
                        : contextualRaiseThreshold(observation, pressure, estimate.standardError(), raiseContext);
                int cap = raiseContext == null ? raise.maxAmount() : contextualRaiseCap(observation, raise,
                        raiseContext, equity, raiseThreshold);
                if (raiseContext != null && raise.minAmount() > uncommittedRaiseCap(observation, raiseContext))
                    raiseThreshold = Math.min(99, raiseThreshold + raiseContext.commitmentMargin());
                int amount = raiseAmount(observation, raise, equity - raiseThreshold, cap, raiseContext != null);
                var model = raiseContext == null ? null : hardPressure.responseModel();
                var ev = raiseContext == null ? null : hardPressure.raiseEv();
                if (model != null && ev == null && amount > 0 && amount <= uncommittedRaiseCap(observation, raiseContext)) {
                    var spot = PreflopResponseModel.proposedRaise(observation, amount);
                    var response = spot == null ? null : model.estimate(spot);
                    if (response != null) {
                        raiseThreshold = Bounds.clamp(raiseThreshold + response.adjustment(), 0, 99);
                        metrics += String.format(Locale.ROOT,
                                " responseSpot=%s responseFold=%.1f%% responseRaise=%.1f%% responseN=%d responseAdjustment=%+d",
                                spot.key(), 100 * response.fold(), 100 * response.raise(), response.observations(),
                                response.adjustment());
                    }
                }
                metrics += " raiseRequired=" + raiseThreshold + "% raiseCap=" + cap;
                if (amount > 0 && equity >= raiseThreshold) {
                    if (ev != null) {
                        int sizingCap = Math.min(cap, uncommittedRaiseCap(observation, raiseContext));
                        double conservativeEquity = estimate.expectedShare()
                                - contextWeights.uncertaintyZHundredths() / 100.0 * estimate.standardError();
                        var comparison = RaiseEvSizing.select(observation, raise, sizingCap, amount,
                                conservativeEquity, model, ev);
                        if (comparison != null) {
                            var reference = comparison.reference();
                            var selected = comparison.selected();
                            metrics += String.format(Locale.ROOT,
                                    " evReferenceAmount=%d evReferenceChips=%.3f evSelectedChips=%.3f evFold=%.1f%% evReraise=%.1f%%",
                                    reference.offer().amount(), reference.chips(), selected.chips(),
                                    100 * selected.fold(), 100 * selected.reraise());
                            amount = selected.offer().amount();
                        }
                    }
                    return decision(ActionType.RAISE, amount,
                            metrics + "; raise only with equity edge and a non-all-in pot fraction");
                }
            }
            LegalAction call = find(legal, ActionType.CALL);
            if (call != null && equity >= callRequired)
                return decision(ActionType.CALL, call.amount(), metrics + "; call clears pot-price and risk margin");
            LegalAction shortCall = shortAllInCall(legal, observation);
            if (shortCall != null && equity >= callRequired)
                return decision(ActionType.ALL_IN, shortCall.amount(), metrics + "; match only a short all-in within pot odds");
            if (find(legal, ActionType.FOLD) != null)
                return decision(ActionType.FOLD, 0, metrics + "; fold below contextual call threshold");
        } else {
            LegalAction raise = find(legal, ActionType.RAISE);
            if (raise != null && pressure.aggressionCount() == 0) {
                int amount = hardBigBlindRaise(observation, raise, equity, estimate.standardError());
                if (amount > 0) return decision(ActionType.RAISE, amount,
                        metrics + "; Hard preflop value raise with the big-blind option");
            }
            LegalAction bet = find(legal, ActionType.BET);
            int valueThreshold = valueBetThreshold(observation);
            if (bet != null && equity >= valueThreshold) {
                int pressureBet = hardPressureBet(observation, bet, equity, estimate.standardError());
                if (pressureBet > 0) return decision(ActionType.BET, pressureBet,
                        metrics + "; Hard value pressure " + pressureBet / observation.pot() + "x pot");
                int amount = betAmount(observation, bet, equity - valueThreshold);
                if (amount > 0) return decision(ActionType.BET, amount,
                        metrics + "; value-bet fraction scales with equity edge and effective stack cap");
            }
        }

        if (find(legal, ActionType.CHECK) != null)
            return decision(ActionType.CHECK, 0, metrics + "; preserve the option to continue without paying");
        return chooseSafeFallback(observation, metrics + "; no priced action clears its threshold");
    }

    int requiredEquityPercent(HoldemObservation observation) {
        var pressure = callPressure(observation);
        return requiredEquityPercent(observation, 0.0, pressure,
                stackRisk(observation, contextWeights.tournamentCommitmentThresholdPercent()),
                historicalShoveMarginAdjustment(observation, pressure));
    }

    private int requiredEquityPercent(HoldemObservation observation, double standardError,
                                      CallPressure pressure, StackRisk stackRisk, int shoveHistoryAdjustment) {
        double potOdds = potOddsPercent(observation);
        int baseMargin = gameMode == GameMode.CASH ? cashRiskMarginPercent : tournamentRiskMarginPercent;
        int opponentMargin = Math.max(0, observation.activeOpponentCount() - 1)
                * contextWeights.extraOpponentCallMargin();
        int aggressionMargin = Math.min(18,
                pressure.aggressionCount() * contextWeights.opponentAggressionCallMargin());
        int positionMargin = isLate(observation) ? -contextWeights.latePositionCallDiscount()
                : contextWeights.earlyPositionCallMargin();
        int uncertaintyMargin = uncertaintyMargin(standardError);
        int largeBetMargin = pressure.potSizedAggression() ? contextWeights.largeBetCallMargin() : 0;
        int allInMargin = pressure.opponentAllInAggression()
                ? contextWeights.opponentAllInCallMargin() + shoveHistoryAdjustment : 0;
        int eliminationMargin = gameMode == GameMode.TOURNAMENT && stackRisk.coveredCommitment()
                ? contextWeights.tournamentEliminationRiskMargin() : 0;
        int shortStackDiscount = gameMode == GameMode.TOURNAMENT && stackRisk.shortStack()
                ? contextWeights.tournamentShortStackCallDiscount() : 0;
        double required = Math.ceil(potOdds) + baseMargin + opponentMargin
                + aggressionMargin + positionMargin + uncertaintyMargin + largeBetMargin + allInMargin
                + eliminationMargin - shortStackDiscount;
        if (hardPressure != null && observation.street() == Street.PREFLOP && observation.toCall() > 0
                && observation.toCall() <= 2L * observation.bigBlind() && !pressure.opponentAllInAggression()
                && !stackRisk.coveredCommitment() && observation.toCall() < observation.heroStack()) {
            required = Math.min(required, Math.max(Math.ceil(potOdds) + uncertaintyMargin,
                    required - hardPressure.cheapPreflopDiscount()));
        }
        return (int) Bounds.clamp(required, 0.0, 98.0);
    }

    private int hardBigBlindRaise(HoldemObservation observation, LegalAction legal, double equity, double standardError) {
        if (hardPressure == null || !hardPressure.bigBlindValueRaise() || gameMode != GameMode.TOURNAMENT
                || observation.street() != Street.PREFLOP) return 0;
        double conservativeEquity = equity - contextWeights.uncertaintyZHundredths() * standardError;
        double threshold = Math.max(45.0, 100.0 / (observation.activeOpponentCount() + 1) + 20.0);
        if (conservativeEquity < threshold) return 0;

        var seats = physicalSeats(observation);
        var folded = foldedSeats(observation);
        int largestOpponentStack = 0;
        for (int seat = 0; seat < observation.playerCount(); seat++) {
            if (seat != observation.heroSeat() && !folded.contains(seats.get(seat)))
                largestOpponentStack = Math.max(largestOpponentStack, observation.visibleStacks().get(seat));
        }
        int cap = Math.min(Math.min(legal.maxAmount(), observation.heroStack() - 1), largestOpponentStack);
        if (cap < legal.minAmount()) return 0;
        return Math.min(cap, Math.max(legal.minAmount(), observation.pot() / 2));
    }

    private int hardPressureBet(HoldemObservation observation, LegalAction legal, double equity, double standardError) {
        if (hardPressure == null || observation.activeOpponentCount() != 1 || observation.pot() == 0
                || (observation.street() != Street.TURN && observation.street() != Street.RIVER)) return 0;
        double conservativeEquity = equity - contextWeights.uncertaintyZHundredths() * standardError;
        if (conservativeEquity < hardPressure.fullPotEquity()) return 0;
        var cards = new ArrayList<>(observation.communityCards());
        cards.addAll(observation.holeCards());
        if (cards.size() < 5) return 0;
        var category = HandRank.evaluateBestOf(cards).category();
        if (category.compareTo(HandRank.Category.TWO_PAIR) < 0) return 0;
        long cap = Math.min(Math.min(legal.maxAmount(), observation.heroStack() - 1L), headsUpEffectiveStack(observation));
        long triple = 3L * observation.pot();
        if (observation.street() == Street.RIVER && conservativeEquity >= hardPressure.triplePotEquity()
                && category.compareTo(HandRank.Category.STRAIGHT) >= 0
                && triple >= legal.minAmount() && triple <= cap) return Math.toIntExact(triple);
        long doubled = 2L * observation.pot();
        if (conservativeEquity >= hardPressure.doublePotEquity() && doubled >= legal.minAmount() && doubled <= cap)
            return Math.toIntExact(doubled);
        return observation.pot() >= legal.minAmount() && observation.pot() <= cap ? observation.pot() : 0;
    }

    private static long headsUpEffectiveStack(HoldemObservation observation) {
        var seats = physicalSeats(observation);
        var folded = foldedSeats(observation);
        long capacity = -1;
        for (int seat = 0; seat < observation.playerCount(); seat++) {
            if (seat == observation.heroSeat() || folded.contains(seats.get(seat))) continue;
            if (capacity != -1) return 0;
            capacity = (long) observation.visibleStacks().get(seat) + observation.visibleContributions().get(seat);
        }
        return capacity - observation.visibleContributions().get(observation.heroSeat());
    }

    int valueBetThreshold(HoldemObservation observation) {
        int opponentMargin = Math.max(0, observation.activeOpponentCount() - 1)
                * contextWeights.extraOpponentValueBetMargin();
        int positionMargin = isLate(observation) ? -contextWeights.latePositionValueBetDiscount()
                : contextWeights.earlyPositionValueBetMargin();
        int aggressionMargin = Math.min(12, aggressionCountThisStreet(observation)
                * contextWeights.opponentAggressionValueBetMargin());
        int modeMargin = gameMode == GameMode.TOURNAMENT ? contextWeights.tournamentValueBetMargin() : 0;
        return Bounds.clamp(valueBetThresholdPercent + opponentMargin + positionMargin
                + aggressionMargin + modeMargin, 5, 98);
    }

    private int betAmount(HoldemObservation observation, LegalAction legal, double edge) {
        int fractionPercent = (int) Bounds.clamp(25 + Math.floor(edge), 25.0, 75.0);
        long target = Math.max(legal.minAmount(), (long) observation.pot() * fractionPercent / 100);
        if (target > legal.maxAmount() || target >= observation.heroStack()) return legal.minAmount();
        return Math.toIntExact(target);
    }

    private int contextualRaiseThreshold(HoldemObservation observation, CallPressure pressure, double standardError,
                                         RaiseContext weights) {
        int position = isLate(observation, true) ? -weights.positionMargin() : weights.positionMargin();
        int opponentMargin = (observation.activeOpponentCount() - 1) * weights.multiwayMargin();
        int aggression = Math.min(3, pressure.aggressionCount()) * weights.aggressionMargin();
        int pricePressure = (pressure.potSizedAggression() ? weights.largeBetMargin() : 0)
                + (pressure.opponentAllInAggression() ? weights.allInMargin() : 0);
        return Bounds.clamp((int) Math.ceil(potOddsPercent(observation)) + uncertaintyMargin(standardError)
                + raiseEdgePercent + weights.baseMargin() + position + opponentMargin + aggression + pricePressure,
                0, 99);
    }

    private int uncertaintyMargin(double standardError) {
        return (int) Math.ceil(contextWeights.uncertaintyZHundredths() / 100.0 * standardError * 100.0);
    }

    private static int contextualRaiseCap(HoldemObservation observation, LegalAction legal, RaiseContext weights,
                                          double equity, int threshold) {
        var seats = physicalSeats(observation);
        var folded = foldedSeats(observation);
        long largestOpponentCapacity = 0;
        for (int seat = 0; seat < observation.playerCount(); seat++) {
            int stack = observation.visibleStacks().get(seat);
            if (seat != observation.heroSeat() && stack > 0 && !folded.contains(seats.get(seat)))
                largestOpponentCapacity = Math.max(largestOpponentCapacity,
                        (long) stack + observation.visibleContributions().get(seat));
        }
        long effective = largestOpponentCapacity - observation.visibleContributions().get(observation.heroSeat());
        long cap = Math.min(effective, Math.min(legal.maxAmount(), observation.heroStack() - 1L));
        if (equity < Math.min(99, threshold + weights.commitmentMargin()))
            cap = Math.min(cap, uncommittedRaiseCap(observation, weights));
        return (int) Math.max(0, cap);
    }

    private static int uncommittedRaiseCap(HoldemObservation observation, RaiseContext weights) {
        return (int) (((long) observation.heroStack() * weights.commitmentThresholdPercent() - 1) / 100);
    }

    private int raiseAmount(HoldemObservation observation, LegalAction legal, double edge, int cap, boolean clamp) {
        if (cap < legal.minAmount()) return 0;
        int callAmount = Math.min(observation.toCall(), observation.heroStack());
        long potAfterCall = (long) observation.pot() + callAmount;
        int fractionPercent = (int) Bounds.clamp(25 + Math.floor(edge), 25.0, 75.0);
        long raiseBy = Math.max((long) legal.minAmount() - callAmount,
                potAfterCall * fractionPercent / 100);
        long target = callAmount + raiseBy;
        if (clamp) return Math.toIntExact(Math.min(target, cap));
        if (target > cap || target >= observation.heroStack()) return 0;
        return Math.toIntExact(target);
    }

    private static double potOddsPercent(HoldemObservation observation) {
        if (observation.toCall() <= 0) return 0.0;
        int callAmount = Math.min(observation.toCall(), observation.heroStack());
        long potAfterCall = (long) observation.pot() + callAmount;
        return potAfterCall == 0 ? 100.0 : 100.0 * callAmount / potAfterCall;
    }

    private static int aggressionCountThisStreet(HoldemObservation observation) {
        return callPressure(observation).aggressionCount();
    }

    private record PublicAllInRaise(int physicalSeat, int remainingStack) {}
    private record CallPressure(int aggressionCount, boolean potSizedAggression, boolean opponentAllInAggression,
                                List<PublicAllInRaise> opponentAllInRaises) {}
    private record StackRisk(boolean coveredCommitment, boolean opponentCanCover, boolean shortStack,
                             int commitmentPercent) {}

    private static CallPressure callPressure(HoldemObservation observation) {
        List<Integer> physicalSeats = physicalSeats(observation);
        int[] streetPaid = new int[observation.playerCount()];
        int price = 0;
        long totalPot = 0;
        Street activeStreet = Street.PREFLOP;
        int aggressions = 0;
        boolean potSized = false, opponentAllIn = false;
        var allInRaiseAmounts = new TreeMap<Integer, Integer>();
        for (String event : observation.publicActionHistory()) {
            Street marker = streetMarker(event);
            if (marker != null) {
                activeStreet = marker;
                Arrays.fill(streetPaid, 0);
                price = 0;
                continue;
            }
            String[] fields = event.split(" ", -1);
            if (fields.length == 3 && (fields[0].equals("SB") || fields[0].equals("BB"))) {
                int seat = localSeat(physicalSeats, fields[1], observation.playerCount());
                int amount = positiveOrZero(fields[2]);
                streetPaid[seat] += amount;
                totalPot += amount;
                price = Math.max(price, fields[0].equals("BB")
                        ? Math.max(streetPaid[seat], observation.bigBlind()) : streetPaid[seat]);
                continue;
            }
            if (fields.length != 4 || !fields[0].equals(activeStreet.name())) continue;
            int seat;
            ActionType action;
            int amount;
            try {
                seat = localSeat(physicalSeats, fields[1], observation.playerCount());
                action = ActionType.valueOf(fields[2]);
                amount = positiveOrZero(fields[3]);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            int previousPrice = price;
            int target = streetPaid[seat] + amount;
            boolean allInRaise = action == ActionType.ALL_IN && target > previousPrice;
            boolean aggression = action == ActionType.BET || action == ActionType.RAISE || allInRaise;
            if (activeStreet == observation.street() && seat != localSeatForHero(observation, physicalSeats)
                    && aggression) {
                aggressions++;
                int raiseBy = Math.max(0, target - previousPrice);
                if (totalPot > 0 && (long) raiseBy * 100 >= totalPot * 75) potSized = true;
                if (allInRaise) {
                    opponentAllIn = true;
                    allInRaiseAmounts.put(physicalSeats.get(seat), amount);
                }
            }
            streetPaid[seat] = target;
            totalPot += amount;
            price = Math.max(price, target);
        }
        List<PublicAllInRaise> opponentAllInRaises = allInRaiseAmounts.entrySet().stream()
                .map(entry -> new PublicAllInRaise(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        return new CallPressure(aggressions, potSized, opponentAllIn, opponentAllInRaises);
    }

    private int historicalShoveMarginAdjustment(HoldemObservation observation, CallPressure pressure) {
        if (gameMode != GameMode.TOURNAMENT || !pressure.opponentAllInAggression()
                || pressure.opponentAllInRaises().isEmpty()
                || contextWeights.shortStackShoveSensitivity() == 0
                || contextWeights.shortStackShoveMaxMarginAdjustment() == 0) return 0;
        var bySeat = new HashMap<Integer, PublicOpponentTendency>();
        for (var tendency : observation.publicOpponentTendencies())
            bySeat.put(tendency.physicalSeat(), tendency);
        double totalDelta = 0;
        int measuredRaisers = 0;
        for (PublicAllInRaise raise : pressure.opponentAllInRaises()) {
            PublicOpponentTendency tendency = bySeat.get(raise.physicalSeat());
            if (tendency == null || tendency.shoveOpportunities() == 0) continue;
            if (!PublicOpponentTendency.inProfileStackRange(raise.remainingStack(), observation.bigBlind())) continue;
            double prior = contextWeights.shortStackShovePriorProbabilityPercent() / 100.0;
            double posterior = (tendency.allInRaises() + prior * contextWeights.shortStackShovePriorOpportunities())
                    / (tendency.shoveOpportunities() + contextWeights.shortStackShovePriorOpportunities());
            double evidence = Math.min(1.0, tendency.shoveOpportunities()
                    / (double) contextWeights.shortStackShoveWarmupOpportunities());
            totalDelta += (prior - posterior) * contextWeights.shortStackShoveSensitivity() * evidence;
            measuredRaisers++;
        }
        if (measuredRaisers == 0) return 0;
        int adjustment = (int) Math.round(totalDelta / measuredRaisers);
        return Bounds.clamp(adjustment, -contextWeights.shortStackShoveMaxMarginAdjustment(),
                contextWeights.shortStackShoveMaxMarginAdjustment());
    }

    private static StackRisk stackRisk(HoldemObservation observation, int commitmentThresholdPercent) {
        int callAmount = Math.min(observation.toCall(), observation.heroStack());
        int commitment = observation.heroStack() == 0 ? 0
                : (int) Math.min(100, ((long) callAmount * 100 + observation.heroStack() - 1) / observation.heroStack());
        boolean shortStack = (long) observation.heroStack() <= (long) observation.bigBlind() * 10;
        if (callAmount == 0 || observation.visibleContributions().size() != observation.playerCount())
            return new StackRisk(false, false, shortStack, commitment);

        List<Integer> physicalSeats = physicalSeats(observation);
        Set<Integer> folded = foldedSeats(observation);
        int heroContribution = observation.visibleContributions().get(observation.heroSeat());
        long targetContribution = (long) heroContribution + callAmount;
        boolean canCover = false;
        for (int seat = 0; seat < observation.playerCount(); seat++) {
            if (seat == observation.heroSeat() || folded.contains(physicalSeats.get(seat))) continue;
            long opponentCapacity = (long) observation.visibleContributions().get(seat)
                    + observation.visibleStacks().get(seat);
            if (opponentCapacity >= targetContribution) { canCover = true; break; }
        }
        boolean coveredCommitment = commitment >= commitmentThresholdPercent && canCover;
        return new StackRisk(coveredCommitment, canCover, shortStack, commitment);
    }

    private static Set<Integer> foldedSeats(HoldemObservation observation) {
        Set<Integer> folded = new HashSet<>();
        for (String event : observation.publicActionHistory()) {
            String[] fields = event.split(" ", -1);
            if (fields.length == 4 && fields[2].equals("FOLD")) {
                try { folded.add(Integer.parseInt(fields[1])); }
                catch (NumberFormatException ignored) { /* Ignore malformed public history. */ }
            }
        }
        return folded;
    }

    private static List<Integer> physicalSeats(HoldemObservation observation) {
        for (String event : observation.publicActionHistory()) {
            if (!event.startsWith("SEATS [") || !event.endsWith("]")) continue;
            try {
                String body = event.substring("SEATS [".length(), event.length() - 1);
                List<Integer> parsed = body.isBlank() ? List.of()
                        : Arrays.stream(body.split(",")).map(String::trim).map(Integer::parseInt)
                        .collect(Collectors.toList());
                if (parsed.size() == observation.playerCount()
                        && observation.heroSeat() < parsed.size()
                        && parsed.get(observation.heroSeat()) == observation.physicalHeroSeat()) return parsed;
            } catch (NumberFormatException ignored) {
                // Fall through to compact-seat identity when public history is malformed.
            }
            break;
        }
        return java.util.stream.IntStream.range(0, observation.playerCount()).boxed().collect(Collectors.toList());
    }

    private static int localSeat(List<Integer> physicalSeats, String token, int playerCount) {
        int physical = Integer.parseInt(token);
        int local = physicalSeats.indexOf(physical);
        if (local < 0 && physical >= 0 && physical < playerCount) return physical;
        if (local < 0) throw new IllegalArgumentException("Unknown physical seat " + physical);
        return local;
    }

    private static int localSeatForHero(HoldemObservation observation, List<Integer> physicalSeats) {
        int local = physicalSeats.indexOf(observation.physicalHeroSeat());
        return local < 0 ? observation.heroSeat() : local;
    }

    private static int positiveOrZero(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) throw new IllegalArgumentException("Negative public action amount");
        return parsed;
    }

    private static Street streetMarker(String event) {
        for (Street street : List.of(Street.FLOP, Street.TURN, Street.RIVER))
            if (event.startsWith(street + " [")) return street;
        return null;
    }

    static boolean isLate(HoldemObservation observation) {
        return isLate(observation, false);
    }

    private static boolean isLate(HoldemObservation observation, boolean forRaise) {
        int fallbackWindow = forRaise ? Math.max(1, observation.playerCount() / 2) : 2;
        List<Integer> physicalSeats = null;
        for (String event : observation.publicActionHistory()) {
            if (!event.startsWith("SEATS [") || !event.endsWith("]")) continue;
            try {
                String body = event.substring("SEATS [".length(), event.length() - 1);
                physicalSeats = body.isBlank() ? List.of()
                        : Arrays.stream(body.split(",")).map(String::trim).map(Integer::parseInt)
                        .collect(Collectors.toList());
            } catch (NumberFormatException ignored) {
                physicalSeats = null;
            }
            break;
        }
        if (physicalSeats == null || physicalSeats.size() != observation.playerCount()
                || observation.heroSeat() >= physicalSeats.size()
                || !physicalSeats.get(observation.heroSeat()).equals(observation.physicalHeroSeat()))
            return DeterministicBluffPolicy.isLatePosition(observation.positionFromButton(),
                    observation.playerCount(), fallbackWindow);

        var folded = new HashSet<Integer>();
        int bigBlindPhysicalSeat = -1;
        for (String event : observation.publicActionHistory()) {
            String[] fields = event.split(" ", -1);
            if (fields.length >= 3 && fields[0].equals("BB")) {
                try { bigBlindPhysicalSeat = Integer.parseInt(fields[1]); }
                catch (NumberFormatException ignored) { bigBlindPhysicalSeat = -1; }
            }
            if (fields.length == 4 && fields[2].equals("FOLD")) {
                try { folded.add(Integer.parseInt(fields[1])); }
                catch (NumberFormatException ignored) { /* Ignore a malformed public-history entry. */ }
            }
        }

        int buttonLocalSeat = Math.floorMod(observation.heroSeat() - observation.positionFromButton(),
                observation.playerCount());
        int firstToActLocalSeat = buttonLocalSeat + 1;
        if (observation.street() == Street.PREFLOP && bigBlindPhysicalSeat >= 0) {
            int bigBlindLocalSeat = physicalSeats.indexOf(bigBlindPhysicalSeat);
            if (bigBlindLocalSeat >= 0) firstToActLocalSeat = bigBlindLocalSeat + 1;
        }

        var actingOrder = new ArrayList<Integer>();
        for (int offset = 0; offset < observation.playerCount(); offset++) {
            int localSeat = Math.floorMod(firstToActLocalSeat + offset, observation.playerCount());
            int physicalSeat = physicalSeats.get(localSeat);
            if (!folded.contains(physicalSeat) && observation.visibleStacks().get(localSeat) > 0)
                actingOrder.add(localSeat);
        }
        int heroOrder = actingOrder.indexOf(observation.heroSeat());
        if (heroOrder < 0) return DeterministicBluffPolicy.isLatePosition(observation.positionFromButton(),
                observation.playerCount(), fallbackWindow);
        int window = forRaise ? Math.max(1, actingOrder.size() / 2) : 2;
        return actingOrder.size() - heroOrder <= window;
    }

    private static LegalAction shortAllInCall(List<LegalAction> legal, HoldemObservation observation) {
        LegalAction action = find(legal, ActionType.ALL_IN);
        return action != null && action.amount() <= observation.toCall() ? action : null;
    }

    private Decision chooseSafeFallback(HoldemObservation observation, String explanation) {
        for (ActionType preferred : List.of(ActionType.CHECK, ActionType.FOLD, ActionType.CALL, ActionType.ALL_IN)) {
            LegalAction legal = find(observation.legalActions(), preferred);
            if (legal != null) return decision(preferred, legal.amount(), explanation);
        }
        LegalAction legal = observation.legalActions().get(0);
        return decision(legal.type(), legal.amount(), explanation);
    }

    private Decision decision(ActionType action, int amount, String explanation) {
        return new Decision(action, amount, VERSION + "-s" + samples + "-" + gameMode.toString().toLowerCase(Locale.ROOT)
                + "-cash" + cashRiskMarginPercent + "-mtt" + tournamentRiskMarginPercent
                + "-raise" + raiseEdgePercent + "-value" + valueBetThresholdPercent + "-ctx"
                + contextWeights.extraOpponentCallMargin() + "." + contextWeights.opponentAggressionCallMargin()
                + "." + contextWeights.raiseMarginPerExtraOpponent() + "."
                + contextWeights.extraOpponentValueBetMargin() + "." + contextWeights.earlyPositionCallMargin()
                + "." + contextWeights.latePositionCallDiscount() + "."
                + contextWeights.earlyPositionValueBetMargin() + "."
                + contextWeights.latePositionValueBetDiscount() + "."
                + contextWeights.opponentAggressionValueBetMargin() + "."
                + contextWeights.tournamentValueBetMargin() + "." + contextWeights.uncertaintyZHundredths() + "."
                + contextWeights.largeBetCallMargin() + "." + contextWeights.opponentAllInCallMargin() + "."
                + contextWeights.tournamentEliminationRiskMargin() + "."
                + contextWeights.tournamentShortStackCallDiscount() + "."
                + contextWeights.tournamentCommitmentThresholdPercent() + "."
                + contextWeights.shortStackShovePriorProbabilityPercent() + "."
                + contextWeights.shortStackShovePriorOpportunities() + "."
                + contextWeights.shortStackShoveWarmupOpportunities() + "."
                + contextWeights.shortStackShoveSensitivity() + "."
                + contextWeights.shortStackShoveMaxMarginAdjustment()
                + (hardPressure == null ? "" : "-hard2." + hardPressure.cheapPreflopDiscount()
                        + "." + hardPressure.fullPotEquity() + "." + hardPressure.doublePotEquity()
                        + "." + hardPressure.triplePotEquity() + (hardPressure.bigBlindValueRaise() ? "-bb-option" : "")
                        + (hardPressure.raiseContext() == null ? "" : "-split." + hardPressure.raiseContext().version()))
                + responseVersion,
                explanation);
    }

    private static LegalAction find(List<LegalAction> actions, ActionType type) {
        return actions.stream().filter(action -> action.type() == type).findFirst().orElse(null);
    }

    private static void percent(int value, String name) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be 0..100");
    }
}
