package ro.atf.holdem.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.Street;

/** Empirical next-action probabilities for a funded opponent facing a heads-up preflop raise. */
public record PreflopResponseModel(int minSamples, int priorStrength, int foldWeight, int raiseWeight,
                                   int maxAdjustment, boolean penalizeRaisesOnly, Map<Spot, Counts> observations) {
    public PreflopResponseModel(int minSamples, int priorStrength, int foldWeight, int raiseWeight,
                               int maxAdjustment, Map<Spot, Counts> observations) {
        this(minSamples, priorStrength, foldWeight, raiseWeight, maxAdjustment, false, observations);
    }
    public enum Sequence { OPEN, AFTER_LIMP, RERAISE }

    public record Spot(Sequence sequence, boolean late, boolean cheap, boolean shortStack) {
        public Spot {
            java.util.Objects.requireNonNull(sequence, "sequence");
        }

        public String key() {
            return sequence + "." + (late ? "late" : "early") + "." + (cheap ? "cheap" : "priced")
                    + "." + (shortStack ? "short" : "funded");
        }

        public static Spot fromKey(String key) {
            String[] fields = key.split("\\.", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid response spot " + key);
            var spot = new Spot(Sequence.valueOf(fields[0]), fields[1].equals("late"),
                    fields[2].equals("cheap"), fields[3].equals("short"));
            if (!spot.key().equals(key)) throw new IllegalArgumentException("Invalid response spot " + key);
            return spot;
        }
    }

    public record Counts(int opportunities, int folds, int raises) {
        public Counts {
            if (opportunities <= 0 || folds < 0 || raises < 0 || (long) folds + raises > opportunities)
                throw new IllegalArgumentException("Invalid response observations");
        }
    }

    public record Estimate(int observations, double fold, double raise, double priorFold, double priorRaise,
                           int adjustment) {}

    public PreflopResponseModel {
        if (minSamples < 1 || priorStrength < 1 || foldWeight < 0 || foldWeight > 100
                || raiseWeight < 0 || raiseWeight > 100 || maxAdjustment < 0 || maxAdjustment > 10)
            throw new IllegalArgumentException("Invalid response model settings");
        observations = Map.copyOf(observations);
        if (observations.isEmpty()) throw new IllegalArgumentException("Response model requires observations");
    }

    public Estimate estimate(Spot spot) {
        Counts local = observations.get(spot);
        if (local == null || local.opportunities() < minSamples) return null;
        long n = 0, folds = 0, raises = 0;
        for (var entry : observations.entrySet()) {
            Spot sibling = entry.getKey();
            if (sibling.cheap() == spot.cheap() && sibling.shortStack() == spot.shortStack()) {
                var counts = entry.getValue();
                n += counts.opportunities();
                folds += counts.folds();
                raises += counts.raises();
            }
        }
        double priorFold = (double) folds / n, priorRaise = (double) raises / n;
        double denominator = local.opportunities() + (double) priorStrength;
        double fold = (local.folds() + priorStrength * priorFold) / denominator;
        double raise = (local.raises() + priorStrength * priorRaise) / denominator;
        double excessRaise = raise - priorRaise;
        if (penalizeRaisesOnly) excessRaise = Math.max(0, excessRaise);
        int delta = (int) Math.round(foldWeight * (priorFold - fold) + raiseWeight * excessRaise);
        return new Estimate(local.opportunities(), fold, raise, priorFold, priorRaise,
                Bounds.clamp(delta, -maxAdjustment, maxAdjustment));
    }

    String version() {
        var canonical = new TreeMap<String, Counts>();
        observations.forEach((spot, counts) -> canonical.put(spot.key(), counts));
        String settings = minSamples + ":" + priorStrength + ":" + foldWeight + ":" + raiseWeight + ":" + maxAdjustment
                + (penalizeRaisesOnly ? ":penalty" : "");
        return fingerprint(settings + canonical);
    }

    static String fingerprint(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                hex.append(Character.forDigit((digest[i] & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(digest[i] & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public static Spot facedRaise(HoldemObservation observation) {
        var context = context(observation);
        if (context == null || context.lastRaiser != context.opponent) return null;
        return spot(observation, context, observation.heroSeat(), observation.toCall(), context.lastRaise);
    }

    static Spot proposedRaise(HoldemObservation observation, int amount) {
        Quote quote = quote(observation, amount);
        return quote == null ? null : quote.spot();
    }

    record Quote(Spot spot, int opponentPrice, int effectiveStack, int opponentPhysicalSeat, boolean covered) {}

    static Quote quote(HoldemObservation observation, int amount) {
        return quote(observation, amount, false);
    }

    static Quote quote(HoldemObservation observation, int amount, boolean includeCover) {
        var context = context(observation);
        if (context == null || amount <= observation.toCall()) return null;
        var paid = observation.visibleContributions();
        long price = (long) paid.get(observation.heroSeat()) + amount - paid.get(context.opponent);
        int stack = observation.visibleStacks().get(context.opponent);
        if (price <= 0 || price > stack || price == stack && !includeCover) return null;
        long capacity = (long) observation.visibleStacks().get(context.opponent)
                + paid.get(context.opponent) - paid.get(observation.heroSeat());
        return new Quote(responseSpot(observation, context, context.opponent, price, context.nextRaise),
                Math.toIntExact(price), (int) Math.min(observation.heroStack(), capacity),
                context.opponentPhysicalSeat, price == stack);
    }

    private static Spot spot(HoldemObservation obs, PublicContext context, int responder, long price, Sequence sequence) {
        int stack = obs.visibleStacks().get(responder);
        if (price <= 0 || price >= stack) return null;
        return responseSpot(obs, context, responder, price, sequence);
    }

    private static Spot responseSpot(HoldemObservation obs, PublicContext context, int responder, long price, Sequence sequence) {
        int stack = obs.visibleStacks().get(responder);
        int other = responder == obs.heroSeat() ? context.opponent : obs.heroSeat();
        int distance = 1 + Math.floorMod(responder - context.bigBlind - 1, obs.playerCount());
        int otherDistance = 1 + Math.floorMod(other - context.bigBlind - 1, obs.playerCount());
        return new Spot(sequence, distance > otherDistance, price <= 2L * obs.bigBlind(), stack <= 10L * obs.bigBlind());
    }

    private record PublicContext(int opponent, int opponentPhysicalSeat, int bigBlind, int lastRaiser,
                                 Sequence lastRaise, Sequence nextRaise) {}

    private static PublicContext context(HoldemObservation obs) {
        if (obs.street() != Street.PREFLOP || obs.activeOpponentCount() != 1) return null;
        List<Integer> seats = List.of();
        var folded = new java.util.HashSet<Integer>();
        int blind = -1, raiser = -1;
        Sequence last = null, next = Sequence.OPEN;
        try {
            for (String event : obs.publicActionHistory()) {
                if (event.startsWith("SEATS [") && event.endsWith("]")) {
                    seats = Arrays.stream(event.substring(7, event.length() - 1).split(","))
                            .map(String::strip).map(Integer::parseInt).toList();
                } else {
                    String[] fields = event.split(" ");
                    if (fields.length == 3 && fields[0].equals("BB")) blind = Integer.parseInt(fields[1]);
                    if (fields.length != 4 || !fields[0].equals("PREFLOP")) continue;
                    int seat = Integer.parseInt(fields[1]);
                    switch (fields[2]) {
                        case "FOLD" -> folded.add(seat);
                        case "CALL" -> { if (next == Sequence.OPEN) next = Sequence.AFTER_LIMP; }
                        case "RAISE" -> { raiser = seat; last = next; next = Sequence.RERAISE; }
                        case "ALL_IN" -> { return null; }
                    }
                }
            }
        } catch (NumberFormatException invalid) {
            return null;
        }
        if (seats.size() != obs.playerCount() || seats.get(obs.heroSeat()) != obs.physicalHeroSeat()) return null;
        int bb = seats.indexOf(blind), opponent = -1;
        for (int seat = 0; seat < seats.size(); seat++) {
            if (seat == obs.heroSeat() || folded.contains(seats.get(seat))) continue;
            if (opponent >= 0 || obs.visibleStacks().get(seat) == 0) return null;
            opponent = seat;
        }
        return bb < 0 || opponent < 0 ? null
                : new PublicContext(opponent, seats.get(opponent), bb, seats.indexOf(raiser), last, next);
    }
}
