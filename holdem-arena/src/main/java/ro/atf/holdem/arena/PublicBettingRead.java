package ro.atf.holdem.arena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.Street;

/** Public heads-up replies to comparable flop and turn bets, published after settlement. */
final class PublicBettingRead implements TournamentArena.BettingObserver {
    enum Mode { FIXED, CUMULATIVE, RECENT, REGIME }

    record Reply(int hand, int seat, ActionType action) {}
    record Estimate(int samples, double fold) {}
    record Regimes(double low, double high, double population, double hazard) {
        Regimes {
            if (!(0 < low && low < high && high < 1 && low < population && population < high
                    && 0 < hazard && hazard < 1)) throw new IllegalArgumentException("Invalid fold regimes");
        }

        double baseline() { return (population - low) / (high - low); }
        double move(double posterior, int hands) {
            return baseline() + (posterior - baseline()) * Math.pow(1 - hazard, hands);
        }
    }
    private record Count(int replies, int folds) {}

    private final int windowHands;
    private final int[] replies;
    private final int[] folds;
    private final Regimes regimes;
    private final int[] lastReplyHand;
    private final double[] highPosterior;
    private final List<Reply> pending = new ArrayList<>();
    private final ArrayDeque<Reply> recent = new ArrayDeque<>();

    PublicBettingRead(int seats, int windowHands) {
        this(seats, windowHands, null);
    }

    PublicBettingRead(int seats, int windowHands, Regimes regimes) {
        if (seats < 2 || windowHands < 1) throw new IllegalArgumentException("Invalid betting history bounds");
        this.windowHands = windowHands;
        replies = new int[seats];
        folds = new int[seats];
        this.regimes = regimes;
        lastReplyHand = regimes == null ? null : new int[seats];
        highPosterior = regimes == null ? null : new double[seats];
    }

    @Override public void accepted(HoldemObservation observation, ActionType action, int amount) {
        if (facingOffer(observation))
            pending.add(new Reply(observation.handNumber(), observation.physicalHeroSeat(), action));
    }

    @Override public void settled(int handNumber) {
        for (var reply : pending) {
            replies[reply.seat()]++;
            if (reply.action() == ActionType.FOLD) folds[reply.seat()]++;
            recent.addLast(reply);
            if (regimes != null) updateRegime(reply);
        }
        pending.clear();
        while (!recent.isEmpty() && recent.getFirst().hand() <= handNumber - windowHands) recent.removeFirst();
    }

    Estimate estimate(int seat, int hand, Mode mode, double prior, int strength) {
        if (mode == Mode.REGIME) {
            if (regimes == null) throw new IllegalStateException("Regimes have not been calibrated");
            double high = highProbability(seat, hand);
            return new Estimate(replies[seat], regimes.low() + (regimes.high() - regimes.low()) * high);
        }
        var count = switch (mode) {
            case FIXED -> new Count(0, 0);
            case CUMULATIVE -> new Count(replies[seat], folds[seat]);
            case RECENT -> recentCount(seat, hand);
            case REGIME -> throw new IllegalStateException("Regime estimates use the separate posterior");
        };
        return new Estimate(count.replies(), (count.folds() + strength * prior) / (count.replies() + strength));
    }

    private double highProbability(int seat, int hand) {
        return lastReplyHand[seat] == 0 ? regimes.baseline()
                : regimes.move(highPosterior[seat], hand - lastReplyHand[seat]);
    }

    private void updateRegime(Reply reply) {
        int seat = reply.seat();
        double prior = highProbability(seat, reply.hand());
        boolean fold = reply.action() == ActionType.FOLD;
        double high = prior * (fold ? regimes.high() : 1 - regimes.high());
        double low = (1 - prior) * (fold ? regimes.low() : 1 - regimes.low());
        highPosterior[seat] = high / (high + low);
        lastReplyHand[seat] = reply.hand();
    }

    private Count recentCount(int seat, int hand) {
        int n = 0, f = 0;
        for (var reply : recent) {
            if (reply.seat() != seat || reply.hand() < hand - windowHands || reply.hand() >= hand) continue;
            n++;
            if (reply.action() == ActionType.FOLD) f++;
        }
        return new Count(n, f);
    }

    static boolean supportedStreet(HoldemObservation observation) {
        return observation.activeOpponentCount() == 1
                && (observation.street() == Street.FLOP || observation.street() == Street.TURN);
    }

    static boolean comparable(int amount, int potBefore) {
        return amount > 0 && potBefore > 0 && 35L * potBefore <= 100L * amount
                && 100L * amount <= 65L * potBefore;
    }

    static boolean facingOffer(HoldemObservation observation) {
        var events = observation.publicActionHistory();
        int call = observation.toCall();
        if (!supportedStreet(observation) || call < 1 || call >= observation.heroStack() || events.isEmpty()) return false;
        String[] latest = events.getLast().split(" ");
        return latest.length == 4 && latest[0].equals(observation.street().name()) && latest[2].equals("BET")
                && latest[3].equals(Integer.toString(call)) && comparable(call, observation.pot() - call)
                && opponentStack(observation, Integer.parseInt(latest[1])) > 0;
    }

    static int singleOpponent(HoldemObservation observation) {
        var seats = List.<Integer>of();
        var folded = new java.util.HashSet<Integer>();
        for (String event : observation.publicActionHistory()) {
            if (event.startsWith("SEATS [") && event.endsWith("]")) {
                seats = java.util.Arrays.stream(event.substring(7, event.length() - 1).split(","))
                        .map(String::strip).map(Integer::parseInt).toList();
            } else if (event.contains(" FOLD ")) {
                folded.add(Integer.parseInt(event.split(" ")[1]));
            }
        }
        return seats.stream().filter(seat -> seat != observation.physicalHeroSeat() && !folded.contains(seat))
                .findFirst().orElse(-1);
    }

    static int opponentStack(HoldemObservation observation, int seat) {
        var tendencies = observation.publicOpponentTendencies();
        for (int index = 0; index < tendencies.size(); index++)
            if (tendencies.get(index).physicalSeat() == seat) return observation.visibleStacks().get(index);
        return 0;
    }
}
