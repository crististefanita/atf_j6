package ro.atf.holdem.ai;

import java.util.*;
import ro.atf.holdem.core.*;

/** Estimates pot share against uniformly random live opponent cards, never hidden cards. */
public final class MonteCarloEquityEstimator {
    public record Estimate(int samples, double expectedShare, double winProbability,
                          double tieProbability, double standardError) {
        public Estimate {
            if (samples < 1 || !probability(expectedShare) || !probability(winProbability)
                    || !probability(tieProbability) || !Double.isFinite(standardError) || standardError < 0)
                throw new IllegalArgumentException("Invalid equity estimate");
        }

        private static boolean probability(double value) {
            return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
        }
    }

    private static final int MAX_SAMPLES = 1_000_000;

    public Estimate estimate(HoldemObservation observation, int samples) {
        Objects.requireNonNull(observation, "observation");
        return estimate(observation, samples, seedFor(observation));
    }

    /** Explicit seeds make hand comparisons reproducible across candidate policies. */
    public Estimate estimate(HoldemObservation observation, int samples, long seed) {
        Objects.requireNonNull(observation, "observation");
        return estimate(observation.holeCards(), observation.communityCards(), observation.activeOpponentCount(), samples, seed);
    }

    /** Estimates from visible cards regardless of whose turn it is. */
    public Estimate estimate(List<Card> hole, List<Card> community, int opponents, int samples, long seed) {
        if (samples < 1 || samples > MAX_SAMPLES) throw new IllegalArgumentException("samples must be 1..1,000,000");
        if (hole.size() != 2 || community.size() > 5) throw new IllegalArgumentException("Invalid visible cards");
        if (opponents < 1 || opponents > 22) throw new IllegalArgumentException("Invalid live opponent count");

        var known = new HashSet<Card>();
        known.addAll(hole);
        known.addAll(community);
        if (known.size() != hole.size() + community.size())
            throw new IllegalArgumentException("Visible cards must be distinct");

        var deck = new ArrayList<Card>(52 - known.size());
        for (Card.Suit suit : Card.Suit.values())
            for (Card.Rank rank : Card.Rank.values()) {
                var card = new Card(rank, suit);
                if (!known.contains(card)) deck.add(card);
            }
        int missingBoard = 5 - community.size();
        int draws = opponents * 2 + missingBoard;
        if (draws > deck.size()) throw new IllegalArgumentException("Not enough unseen cards for this table state");

        var random = new SplittableRandom(seed);
        int wins = 0;
        int ties = 0;
        double shares = 0.0;
        double squaredShares = 0.0;
        for (int trial = 0; trial < samples; trial++) {
            Card[] draw = deck.toArray(new Card[deck.size()]);
            for (int i = 0; i < draws; i++) {
                int selected = i + random.nextInt(draw.length - i);
                Card temporary = draw[i];
                draw[i] = draw[selected];
                draw[selected] = temporary;
            }

            var board = new ArrayList<Card>(5);
            board.addAll(community);
            int index = opponents * 2;
            for (int i = 0; i < missingBoard; i++) board.add(draw[index++]);
            int heroRank = rank(hole, board);
            int bestRank = heroRank;
            int winningPlayers = 1;
            boolean heroIsBest = true;
            for (int opponent = 0; opponent < opponents; opponent++) {
                int opponentRank = rank(List.of(draw[opponent * 2], draw[opponent * 2 + 1]), board);
                if (opponentRank > bestRank) {
                    bestRank = opponentRank;
                    winningPlayers = 1;
                    heroIsBest = false;
                } else if (opponentRank == bestRank) {
                    winningPlayers++;
                }
            }
            double share = heroIsBest ? 1.0 / winningPlayers : 0.0;
            if (heroIsBest && winningPlayers == 1) wins++;
            else if (heroIsBest) ties++;
            shares += share;
            squaredShares += share * share;
        }

        double expectedShare = shares / samples;
        double variance = samples < 2 ? 0.0
                : Math.max(0.0, (squaredShares - samples * expectedShare * expectedShare) / (samples - 1));
        return new Estimate(samples, expectedShare, (double) wins / samples,
                (double) ties / samples, Math.sqrt(variance / samples));
    }

    private static int rank(List<Card> hole, List<Card> board) {
        var cards = new ArrayList<Card>(hole.size() + board.size());
        cards.addAll(hole);
        cards.addAll(board);
        return HandRank.bestOf(cards);
    }

    private static long seedFor(HoldemObservation observation) {
        long seed = 0x9E3779B97F4A7C15L ^ observation.handNumber();
        seed = Long.rotateLeft(seed ^ observation.heroSeat(), 13);
        seed = Long.rotateLeft(seed ^ observation.activeOpponentCount(), 19);
        seed = Long.rotateLeft(seed ^ observation.street().ordinal(), 23);
        seed = Long.rotateLeft(seed ^ observation.holeCards().hashCode(), 29);
        seed = Long.rotateLeft(seed ^ observation.communityCards().hashCode(), 31);
        seed = Long.rotateLeft(seed ^ observation.publicActionHistory().hashCode(), 37);
        return seed;
    }
}
