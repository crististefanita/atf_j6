package ro.atf.holdem.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HandRank;

/** Preflop heads-up equity against an empirical response range, with shared draws across offers. */
public final class ConditionalRangeEquity {
    public enum Bucket {
        PREMIUM(62), SMALL_PAIR(48), BROADWAY(128), OTHER(1088);

        private final int population;

        Bucket(int population) {
            this.population = population;
        }

        public static Bucket of(Card first, Card second) {
            int high = Math.max(first.rank().value(), second.rank().value());
            int low = Math.min(first.rank().value(), second.rank().value());
            if ((high == low && high >= 10) || (high == 14 && low >= 12)) return PREMIUM;
            if (high == low) return SMALL_PAIR;
            return low >= 10 ? BROADWAY : OTHER;
        }
    }

    public record Counts(int fold, int call, int raise) {
        public Counts {
            if (fold < 0 || call < 0 || raise < 0) throw new IllegalArgumentException("Negative response count");
        }
    }

    public record Response(double probability, double equity) {}
    public record Estimate(double foldProbability, Response call, Response raise, double uniformEquity) {}

    private record BucketEquity(int combinations, double share) {}
    private record Combo(Card first, Card second) {}

    private static final List<Card> DECK = deck();
    private final int samplesPerBucket;
    private final long seed;
    private final Map<Combo, Map<Bucket, BucketEquity>> cache = new ConcurrentHashMap<>();

    public ConditionalRangeEquity(int samplesPerBucket, long seed) {
        if (samplesPerBucket < 1 || samplesPerBucket > 10_000)
            throw new IllegalArgumentException("Invalid conditional equity sample budget");
        this.samplesPerBucket = samplesPerBucket;
        this.seed = seed;
    }

    public Estimate estimate(List<Card> hole, Map<Bucket, Counts> range) {
        if (hole.size() != 2 || hole.get(0).equals(hole.get(1)))
            throw new IllegalArgumentException("Expected two distinct preflop cards");
        if (range.size() != Bucket.values().length) throw new IllegalArgumentException("Incomplete response range");
        var buckets = cache.computeIfAbsent(canonical(hole), this::sample);
        double folds = 0, calls = 0, raises = 0, callEquity = 0, raiseEquity = 0, uniform = 0;
        int combinations = 0;
        for (var bucket : Bucket.values()) {
            var sample = buckets.get(bucket);
            var response = range.get(bucket);
            double available = (double) sample.combinations() / bucket.population;
            folds += available * response.fold();
            calls += available * response.call();
            raises += available * response.raise();
            callEquity += available * response.call() * sample.share();
            raiseEquity += available * response.raise() * sample.share();
            uniform += sample.combinations() * sample.share();
            combinations += sample.combinations();
        }
        double weight = folds + calls + raises;
        if (weight == 0) throw new IllegalArgumentException("Empty response range");
        return new Estimate(folds / weight, response(calls, callEquity, weight), response(raises, raiseEquity, weight),
                uniform / combinations);
    }

    private static Response response(double weight, double equity, double total) {
        return new Response(weight / total, weight == 0 ? Double.NaN : equity / weight);
    }

    private Map<Bucket, BucketEquity> sample(Combo hero) {
        var unseen = new ArrayList<>(DECK);
        unseen.remove(hero.first());
        unseen.remove(hero.second());
        var groups = new EnumMap<Bucket, List<Combo>>(Bucket.class);
        for (var bucket : Bucket.values()) groups.put(bucket, new ArrayList<>());
        for (int i = 0; i < unseen.size(); i++) {
            for (int j = i + 1; j < unseen.size(); j++) {
                var first = unseen.get(i);
                var second = unseen.get(j);
                groups.get(Bucket.of(first, second)).add(new Combo(first, second));
            }
        }
        var random = new SplittableRandom(seed);
        var result = new EnumMap<Bucket, BucketEquity>(Bucket.class);
        for (var bucket : Bucket.values()) {
            var combos = groups.get(bucket);
            int doubledShare = 0;
            for (int trial = 0; trial < samplesPerBucket; trial++) {
                var opponent = combos.get(random.nextInt(combos.size()));
                var boardDeck = new ArrayList<>(unseen);
                boardDeck.remove(opponent.first());
                boardDeck.remove(opponent.second());
                for (int i = 0; i < 5; i++) {
                    int index = i + random.nextInt(boardDeck.size() - i);
                    java.util.Collections.swap(boardDeck, i, index);
                }
                var board = boardDeck.subList(0, 5);
                int relativeRank = Integer.compare(rank(hero, board), rank(opponent, board));
                doubledShare += relativeRank + 1;
            }
            result.put(bucket, new BucketEquity(combos.size(), doubledShare / (2.0 * samplesPerBucket)));
        }
        return result;
    }

    private static int rank(Combo hole, List<Card> board) {
        var cards = new ArrayList<>(board);
        cards.add(hole.first());
        cards.add(hole.second());
        return HandRank.bestOf(cards);
    }

    private static Combo canonical(List<Card> hole) {
        var a = hole.get(0);
        var b = hole.get(1);
        var high = a.rank().value() >= b.rank().value() ? a : b;
        var low = high == a ? b : a;
        return new Combo(new Card(high.rank(), Card.Suit.CLUBS),
                new Card(low.rank(), high.suit() == low.suit() ? Card.Suit.CLUBS : Card.Suit.DIAMONDS));
    }

    private static List<Card> deck() {
        var result = new ArrayList<Card>(52);
        for (var rank : Card.Rank.values())
            for (var suit : Card.Suit.values()) result.add(new Card(rank, suit));
        return List.copyOf(result);
    }
}
