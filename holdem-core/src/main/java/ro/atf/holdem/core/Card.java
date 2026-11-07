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

    public static Card fromLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.length() != 2) throw new IllegalArgumentException("Card label must have two characters");
        Rank rank = switch (Character.toUpperCase(label.charAt(0))) {
            case '2' -> Rank.TWO; case '3' -> Rank.THREE; case '4' -> Rank.FOUR;
            case '5' -> Rank.FIVE; case '6' -> Rank.SIX; case '7' -> Rank.SEVEN;
            case '8' -> Rank.EIGHT; case '9' -> Rank.NINE; case 'T' -> Rank.TEN;
            case 'J' -> Rank.JACK; case 'Q' -> Rank.QUEEN; case 'K' -> Rank.KING;
            case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("Unknown card rank: " + label.charAt(0));
        };
        Suit suit = switch (Character.toLowerCase(label.charAt(1))) {
            case 'c' -> Suit.CLUBS; case 'd' -> Suit.DIAMONDS;
            case 'h' -> Suit.HEARTS; case 's' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Unknown card suit: " + label.charAt(1));
        };
        return new Card(rank, suit);
    }
}
