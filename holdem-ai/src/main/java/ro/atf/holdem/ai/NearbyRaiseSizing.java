package ro.atf.holdem.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import ro.atf.holdem.ai.PreflopResponseModel.Counts;
import ro.atf.holdem.ai.PreflopResponseModel.Spot;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.LegalAction;

/** Paired response frequencies for small changes to a legal Hard raise. */
public record NearbyRaiseSizing(Scope scope, Limits limits, int minPairedSamples, Map<Key, PairedCounts> observations) {
    public enum Scope {
        FIRST_RAISE, RERAISE;

        public static Scope forSpot(Spot spot) {
            return spot.sequence() == PreflopResponseModel.Sequence.RERAISE ? RERAISE : FIRST_RAISE;
        }
    }

    public enum Direction { UP, DOWN }

    public record Limits(int deltaPercentOfBigBlind, int deltaPercentOfReference, int deltaPercentOfEffectiveStack,
                         int maxPercentOfEffectiveStack) {
        public Limits {
            if (deltaPercentOfBigBlind < 1 || deltaPercentOfBigBlind > 200
                    || deltaPercentOfReference < 1 || deltaPercentOfReference > 50
                    || deltaPercentOfEffectiveStack < 1 || deltaPercentOfEffectiveStack > 20
                    || maxPercentOfEffectiveStack < 1 || maxPercentOfEffectiveStack > 90)
                throw new IllegalArgumentException("Invalid nearby raise limits");
        }
    }

    public record Key(Spot spot, Direction direction) {
        public Key {
            Objects.requireNonNull(spot, "spot");
            Objects.requireNonNull(direction, "direction");
        }

        public static Key fromKey(String value) {
            int separator = value.lastIndexOf('.');
            if (separator <= 0) throw new IllegalArgumentException("Invalid nearby quote key");
            return new Key(Spot.fromKey(value.substring(0, separator)),
                    Direction.valueOf(value.substring(separator + 1)));
        }

        String key() { return spot.key() + "." + direction; }
    }

    public record PairedCounts(Counts reference, Counts alternate) {
        public PairedCounts {
            if (reference.opportunities() != alternate.opportunities())
                throw new IllegalArgumentException("Nearby quote counts must be paired");
        }
    }

    public record QuotePair(Key key, RaiseEvSizing.Offer reference, RaiseEvSizing.Offer alternate, int effectiveStack) {}

    public NearbyRaiseSizing {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(limits, "limits");
        if (limits.maxPercentOfEffectiveStack() > 75)
            throw new IllegalArgumentException("Bidirectional nearby sizing is capped at 75% of effective stack");
        if (minPairedSamples < 1) throw new IllegalArgumentException("Nearby quotes require paired evidence");
        observations = Map.copyOf(observations);
        if (observations.isEmpty() || observations.keySet().stream().anyMatch(key -> Scope.forSpot(key.spot()) != scope))
            throw new IllegalArgumentException("Nearby quote evidence must match the configured sequence");
    }

    public static List<QuotePair> quotes(HoldemObservation obs, LegalAction legal, int cap, int reference, Limits limits) {
        if (obs.toCall() <= 0 || legal.type() != ActionType.RAISE || reference < legal.minAmount()) return List.of();
        var origin = PreflopResponseModel.quote(obs, reference);
        if (origin == null) return List.of();
        int effective = origin.effectiveStack();
        int maximum = Math.min(Math.min(cap, legal.maxAmount()), percent(effective, limits.maxPercentOfEffectiveStack()));
        int delta = delta(obs.bigBlind(), reference, effective, limits);
        if (reference > maximum || delta == 0) return List.of();
        var result = new ArrayList<QuotePair>(2);
        var baseline = new RaiseEvSizing.Offer(reference, origin.opponentPrice(), origin.spot());
        int lower = Math.max(legal.minAmount(), reference - delta);
        int upper = (int) Math.min(maximum, (long) reference + delta);
        addQuote(result, obs, baseline, lower, Direction.DOWN, effective);
        addQuote(result, obs, baseline, upper, Direction.UP, effective);
        return List.copyOf(result);
    }

    /** Quotes one legal reduction for settlement probes, including a control that exactly covers the opponent. */
    public static QuotePair lowerReraise(HoldemObservation obs, int reference, Limits limits) {
        if (obs.toCall() <= 0 || reference >= obs.heroStack()) return null;
        var legal = obs.legalActions().stream().filter(action -> action.type() == ActionType.RAISE)
                .findFirst().orElse(null);
        if (legal == null || reference <= legal.minAmount() || reference > legal.maxAmount()) return null;
        var origin = PreflopResponseModel.quote(obs, reference, true);
        if (origin == null || Scope.forSpot(origin.spot()) != Scope.RERAISE) return null;
        int effective = origin.effectiveStack();
        int lower = Math.max(legal.minAmount(), reference - delta(obs.bigBlind(), reference, effective, limits));
        if (lower == reference || lower > percent(effective, limits.maxPercentOfEffectiveStack())) return null;
        var alternate = PreflopResponseModel.quote(obs, lower);
        if (alternate == null) return null;
        return new QuotePair(new Key(origin.spot(), Direction.DOWN),
                new RaiseEvSizing.Offer(reference, origin.opponentPrice(), origin.spot()),
                new RaiseEvSizing.Offer(lower, alternate.opponentPrice(), alternate.spot()), effective);
    }

    private static int delta(int bigBlind, int reference, int effective, Limits limits) {
        return Math.min(percent(bigBlind, limits.deltaPercentOfBigBlind()),
                Math.min(percent(reference, limits.deltaPercentOfReference()),
                        percent(effective, limits.deltaPercentOfEffectiveStack())));
    }

    private static int percent(int chips, int percentage) {
        return (int) Math.min(Integer.MAX_VALUE, (long) chips * percentage / 100);
    }

    private static void addQuote(List<QuotePair> result, HoldemObservation obs, RaiseEvSizing.Offer baseline,
                                 int amount, Direction direction, int effective) {
        if (amount == baseline.amount()) return;
        var quote = PreflopResponseModel.quote(obs, amount);
        if (quote != null)
            result.add(new QuotePair(new Key(baseline.spot(), direction), baseline,
                    new RaiseEvSizing.Offer(amount, quote.opponentPrice(), quote.spot()), effective));
    }

    RaiseEvSizing.Comparison compare(HoldemObservation obs, List<QuotePair> quotes, double equity, double margin) {
        RaiseEvSizing.Comparison best = null;
        double bestGain = Double.NEGATIVE_INFINITY;
        for (var pair : quotes) {
            var counts = observations.get(pair.key());
            if (counts == null || counts.reference().opportunities() < minPairedSamples) continue;
            var reference = value(obs.pot(), pair.reference(), equity, counts.reference());
            var alternate = value(obs.pot(), pair.alternate(), equity, counts.alternate());
            double gain = alternate.chips() - reference.chips();
            if (gain > bestGain) {
                bestGain = gain;
                best = new RaiseEvSizing.Comparison(reference, gain >= margin ? alternate : reference);
            }
        }
        return best;
    }

    private static RaiseEvSizing.Value value(int pot, RaiseEvSizing.Offer offer, double equity, Counts counts) {
        double fold = counts.folds() / (double) counts.opportunities();
        double raise = counts.raises() / (double) counts.opportunities();
        return new RaiseEvSizing.Value(offer, fold, raise,
                RaiseEvSizing.expectedChips(pot, offer.amount(), offer.opponentPrice(), equity, fold, raise));
    }

    String version() {
        var canonical = new TreeMap<String, PairedCounts>();
        observations.forEach((key, counts) -> canonical.put(key.key(), counts));
        return PreflopResponseModel.fingerprint(scope + ":" + limits + ":" + minPairedSamples + ":" + canonical);
    }
}
