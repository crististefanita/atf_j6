package ro.atf.holdem.core;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {
    public enum Rank {
        TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13), ACE(14);
        private final int value;
        Rank(int value) { this.value = value; }
        public int value() { return value; }
    }

    public enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    public String label() {
        String rankLabel = switch (rank) {
            case TEN -> "T";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
            default -> Integer.toString(rank.value());
        };
        String suitLabel = switch (suit) {
            case CLUBS -> "c";
            case DIAMONDS -> "d";
            case HEARTS -> "h";
            case SPADES -> "s";
        };
        return rankLabel + suitLabel;
    }
}
