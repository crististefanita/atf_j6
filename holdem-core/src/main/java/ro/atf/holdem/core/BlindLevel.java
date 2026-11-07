package ro.atf.holdem.core;

public record BlindLevel(int smallBlind, int bigBlind, int minimumOpen) {
    public BlindLevel {
        if (smallBlind < 1 || bigBlind <= smallBlind || minimumOpen < bigBlind)
            throw new IllegalArgumentException("Expected 0 < small blind < big blind <= minimum open");
    }
}
