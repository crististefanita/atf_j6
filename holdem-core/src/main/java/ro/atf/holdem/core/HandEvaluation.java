package ro.atf.holdem.core;

import java.util.HashSet;
import java.util.List;

/** A ranked Hold'em hand plus the exact five cards selected from its input. */
public record HandEvaluation(int score, HandRank.Category category, List<Card> bestFive) {
    public HandEvaluation {
        if (category == null || HandRank.categoryOfScore(score) != category
                || bestFive == null || bestFive.size() != 5 || new HashSet<>(bestFive).size() != 5) {
            throw new IllegalArgumentException("Invalid best-five evaluation");
        }
        bestFive = List.copyOf(bestFive);
    }
}
