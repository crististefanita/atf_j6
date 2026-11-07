package ro.atf.holdem.ai;

import java.util.*;
import ro.atf.holdem.core.*;

/** Uses sampled random-hand equity, pot price, live opponents, aggression and position. */
public final class ContextualEquityPolicy implements DecisionPolicy {
    public enum GameMode { CASH, TOURNAMENT }

    public record ContextWeights(int extraOpponentCallMargin, int opponentAggressionCallMargin,
                                 int raiseMarginPerExtraOpponent, int extraOpponentValueBetMargin,
                                 int earlyPositionCallMargin, int latePositionCallDiscount,
                                 int earlyPositionValueBetMargin, int latePositionValueBetDiscount,
                                 int opponentAggressionValueBetMargin, int tournamentValueBetMargin,
                                 int uncertaintyZHundredths, int largeBetCallMargin,
                                 int opponentAllInCallMargin, int tournamentEliminationRiskMargin,
                                 int tournamentShortStackCallDiscount, int tournamentCommitmentThresholdPercent) {
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
            if (uncertaintyZHundredths < 0 || uncertaintyZHundredths > 500)
                throw new IllegalArgumentException("uncertaintyZHundredths must be 0..500");
            if (tournamentCommitmentThresholdPercent < 50 || tournamentCommitmentThresholdPercent > 100)
                throw new IllegalArgumentException("tournamentCommitmentThresholdPercent must be 50..100");
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
                    tournamentValueBetMargin, uncertaintyZHundredths, 3, 6, 8, 5, 75);
        }

        public static ContextWeights defaults() {
            return new ContextWeights(3, 3, 2, 5, 2, 2, 3, 3, 2, 2, 164, 3, 6, 8, 5, 75);
        }
    }

    public static final String VERSION = "EQUITY-MC-0.2";
    private final MonteCarloEquityEstimator estimator = new MonteCarloEquityEstimator();
    private final int samples;
    private final int cashRiskMarginPercent;
    private final int tournamentRiskMarginPercent;
    private final int raiseEdgePercent;
    private final int valueBetThresholdPercent;
    private final ContextWeights contextWeights;
    private final GameMode gameMode;

    public ContextualEquityPolicy(int samples, int cashRiskMarginPercent, int tournamentRiskMarginPercent,
                                  int raiseEdgePercent, int valueBetThresholdPercent, GameMode gameMode) {
        this(samples, cashRiskMarginPercent, tournamentRiskMarginPercent, raiseEdgePercent,
                valueBetThresholdPercent, gameMode, ContextWeights.defaults());
    }

    public ContextualEquityPolicy(int samples, int cashRiskMarginPercent, int tournamentRiskMarginPercent,
                                  int raiseEdgePercent, int valueBetThresholdPercent, GameMode gameMode,
                                  ContextWeights contextWeights) {
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
        double equity = estimate.expectedShare() * 100.0;
        int required = requiredEquityPercent(observation, estimate.standardError(), pressure, stackRisk);
        String metrics = String.format(Locale.ROOT,
                "equity=%.1f%% win=%.1f%% tie=%.1f%% potOdds=%.1f%% required=%.1f%% samples=%d se=%.1f%% opponents=%d mode=%s pressurePotSized=%s opponentAllIn=%s callCommitment=%d%% covered=%s shortStack=%s",
                equity, estimate.winProbability() * 100.0, estimate.tieProbability() * 100.0,
                potOddsPercent(observation), (double) required, estimate.samples(),
                estimate.standardError() * 100.0, observation.activeOpponentCount(), gameMode,
                pressure.potSizedAggression(), pressure.opponentAllInAggression(), stackRisk.commitmentPercent(),
                stackRisk.opponentCanCover(), stackRisk.shortStack());

        if (observation.toCall() > 0) {
            LegalAction raise = find(legal, ActionType.RAISE);
            int raiseThreshold = Math.min(99, required + raiseEdgePercent
                    + Math.max(0, observation.activeOpponentCount() - 1)
                    * contextWeights.raiseMarginPerExtraOpponent());
            if (raise != null && equity >= raiseThreshold) {
                int amount = raiseAmount(observation, raise, equity - raiseThreshold);
                if (amount > 0) return decision(ActionType.RAISE, amount,
                        metrics + "; raise only with equity edge and a non-all-in pot fraction");
            }
            LegalAction call = find(legal, ActionType.CALL);
            if (call != null && equity >= required)
                return decision(ActionType.CALL, call.amount(), metrics + "; call clears pot-price and risk margin");
            LegalAction shortCall = shortAllInCall(legal, observation);
            if (shortCall != null && equity >= required)
                return decision(ActionType.ALL_IN, shortCall.amount(), metrics + "; match only a short all-in within pot odds");
            if (find(legal, ActionType.FOLD) != null)
                return decision(ActionType.FOLD, 0, metrics + "; fold below contextual call threshold");
        } else {
            LegalAction bet = find(legal, ActionType.BET);
            int valueThreshold = valueBetThreshold(observation);
            if (bet != null && equity >= valueThreshold) {
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
        return requiredEquityPercent(observation, 0.0, callPressure(observation),
                stackRisk(observation, contextWeights.tournamentCommitmentThresholdPercent()));
    }

    private int requiredEquityPercent(HoldemObservation observation, double standardError,
                                      CallPressure pressure, StackRisk stackRisk) {
        double potOdds = potOddsPercent(observation);
        int baseMargin = gameMode == GameMode.CASH ? cashRiskMarginPercent : tournamentRiskMarginPercent;
        int opponentMargin = Math.max(0, observation.activeOpponentCount() - 1)
                * contextWeights.extraOpponentCallMargin();
        int aggressionMargin = Math.min(18,
                pressure.aggressionCount() * contextWeights.opponentAggressionCallMargin());
        int positionMargin = isLate(observation) ? -contextWeights.latePositionCallDiscount()
                : contextWeights.earlyPositionCallMargin();
        int uncertaintyMargin = (int) Math.ceil(contextWeights.uncertaintyZHundredths()
                / 100.0 * standardError * 100.0);
        int largeBetMargin = pressure.potSizedAggression() ? contextWeights.largeBetCallMargin() : 0;
        int allInMargin = pressure.opponentAllInAggression() ? contextWeights.opponentAllInCallMargin() : 0;
        int eliminationMargin = gameMode == GameMode.TOURNAMENT && stackRisk.coveredCommitment()
                ? contextWeights.tournamentEliminationRiskMargin() : 0;
        int shortStackDiscount = gameMode == GameMode.TOURNAMENT && stackRisk.shortStack()
                ? contextWeights.tournamentShortStackCallDiscount() : 0;
        return (int) Math.clamp(Math.ceil(potOdds) + baseMargin + opponentMargin
                + aggressionMargin + positionMargin + uncertaintyMargin + largeBetMargin + allInMargin
                + eliminationMargin - shortStackDiscount, 0, 98);
    }

    int valueBetThreshold(HoldemObservation observation) {
        int opponentMargin = Math.max(0, observation.activeOpponentCount() - 1)
                * contextWeights.extraOpponentValueBetMargin();
        int positionMargin = isLate(observation) ? -contextWeights.latePositionValueBetDiscount()
                : contextWeights.earlyPositionValueBetMargin();
        int aggressionMargin = Math.min(12, aggressionCountThisStreet(observation)
                * contextWeights.opponentAggressionValueBetMargin());
        int modeMargin = gameMode == GameMode.TOURNAMENT ? contextWeights.tournamentValueBetMargin() : 0;
        return (int) Math.clamp(valueBetThresholdPercent + opponentMargin + positionMargin
                + aggressionMargin + modeMargin, 5, 98);
    }

    private int betAmount(HoldemObservation observation, LegalAction legal, double edge) {
        int fractionPercent = (int) Math.clamp(25 + Math.floor(edge), 25, 75);
        long target = Math.max(legal.minAmount(), (long) observation.pot() * fractionPercent / 100);
        if (target > legal.maxAmount() || target >= observation.heroStack()) return legal.minAmount();
        return Math.toIntExact(target);
    }

    private int raiseAmount(HoldemObservation observation, LegalAction legal, double edge) {
        int callAmount = Math.min(observation.toCall(), observation.heroStack());
        long potAfterCall = (long) observation.pot() + callAmount;
        int fractionPercent = (int) Math.clamp(25 + Math.floor(edge), 25, 75);
        long raiseBy = Math.max((long) legal.minAmount() - callAmount,
                potAfterCall * fractionPercent / 100);
        long target = callAmount + raiseBy;
        if (target <= legal.minAmount()) target = legal.minAmount();
        if (target > legal.maxAmount() || target >= observation.heroStack()) return 0;
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

    private record CallPressure(int aggressionCount, boolean potSizedAggression, boolean opponentAllInAggression) {}
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
                price = Math.max(price, streetPaid[seat]);
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
                if (allInRaise) opponentAllIn = true;
            }
            streetPaid[seat] = target;
            totalPot += amount;
            price = Math.max(price, target);
        }
        return new CallPressure(aggressions, potSized, opponentAllIn);
    }

    private static StackRisk stackRisk(HoldemObservation observation, int commitmentThresholdPercent) {
        int callAmount = Math.min(observation.toCall(), observation.heroStack());
        int commitment = observation.heroStack() == 0 ? 0
                : (int) Math.min(100, ((long) callAmount * 100 + observation.heroStack() - 1) / observation.heroStack());
        boolean shortStack = (long) observation.heroStack() <= (long) observation.bigBlind() * 10;
        if (callAmount == 0 || observation.visibleContributions().size() != observation.playerCount())
            return new StackRisk(false, false, shortStack, commitment);

        List<Integer> physicalSeats = physicalSeats(observation);
        Set<Integer> folded = new HashSet<>();
        for (String event : observation.publicActionHistory()) {
            String[] fields = event.split(" ", -1);
            if (fields.length == 4 && fields[2].equals("FOLD")) {
                try { folded.add(Integer.parseInt(fields[1])); }
                catch (NumberFormatException ignored) { /* Ignore malformed public history. */ }
            }
        }
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

    private static List<Integer> physicalSeats(HoldemObservation observation) {
        for (String event : observation.publicActionHistory()) {
            if (!event.startsWith("SEATS [") || !event.endsWith("]")) continue;
            try {
                String body = event.substring("SEATS [".length(), event.length() - 1);
                List<Integer> parsed = body.isBlank() ? List.of()
                        : Arrays.stream(body.split(",")).map(String::trim).map(Integer::parseInt).toList();
                if (parsed.size() == observation.playerCount()
                        && observation.heroSeat() < parsed.size()
                        && parsed.get(observation.heroSeat()) == observation.physicalHeroSeat()) return parsed;
            } catch (NumberFormatException ignored) {
                // Fall through to compact-seat identity when public history is malformed.
            }
            break;
        }
        return java.util.stream.IntStream.range(0, observation.playerCount()).boxed().toList();
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
        List<Integer> physicalSeats = null;
        for (String event : observation.publicActionHistory()) {
            if (!event.startsWith("SEATS [") || !event.endsWith("]")) continue;
            try {
                String body = event.substring("SEATS [".length(), event.length() - 1);
                physicalSeats = body.isBlank() ? List.of()
                        : Arrays.stream(body.split(",")).map(String::trim).map(Integer::parseInt).toList();
            } catch (NumberFormatException ignored) {
                physicalSeats = null;
            }
            break;
        }
        if (physicalSeats == null || physicalSeats.size() != observation.playerCount()
                || observation.heroSeat() >= physicalSeats.size()
                || !physicalSeats.get(observation.heroSeat()).equals(observation.physicalHeroSeat()))
            return DeterministicBluffPolicy.isLatePosition(observation.positionFromButton(),
                    observation.playerCount(), 2);

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
                observation.playerCount(), 2);
        return actingOrder.size() - heroOrder - 1 <= 1;
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
        LegalAction legal = observation.legalActions().getFirst();
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
                + contextWeights.tournamentCommitmentThresholdPercent(),
                explanation);
    }

    private static LegalAction find(List<LegalAction> actions, ActionType type) {
        return actions.stream().filter(action -> action.type() == type).findFirst().orElse(null);
    }

    private static void percent(int value, String name) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be 0..100");
    }
}
