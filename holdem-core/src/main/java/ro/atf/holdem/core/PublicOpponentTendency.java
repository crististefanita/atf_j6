package ro.atf.holdem.core;

/** Public tournament-local evidence from raise-capable preflop decisions above 2 and up to 10 big blinds. */
public record PublicOpponentTendency(int physicalSeat, int shoveOpportunities, int allInRaises) {
    public static final int MIN_PROFILE_STACK_BIG_BLINDS_EXCLUSIVE = 2;
    public static final int MAX_PROFILE_STACK_BIG_BLINDS = 10;

    public static boolean inProfileStackRange(int remainingStack, int bigBlind) {
        return remainingStack >= 0 && bigBlind > 0
                && (long) remainingStack > (long) bigBlind * MIN_PROFILE_STACK_BIG_BLINDS_EXCLUSIVE
                && (long) remainingStack <= (long) bigBlind * MAX_PROFILE_STACK_BIG_BLINDS;
    }

    public PublicOpponentTendency {
        if (physicalSeat < 0 || shoveOpportunities < 0 || allInRaises < 0
                || allInRaises > shoveOpportunities)
            throw new IllegalArgumentException("Invalid public opponent tendency");
    }
}
