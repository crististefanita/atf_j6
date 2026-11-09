package ro.atf.holdem.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ro.atf.holdem.core.ActionType.*;

class HoldemRulesTest {
    private static final BlindLevel BLINDS = new BlindLevel(5, 10, 15);

    private HoldemHand hand(Integer... stacks) {
        return new HoldemHand(1, List.of(stacks), 0, BLINDS, HoldemHand.shuffledDeck(42));
    }

    @Test void headsUpOrderAndOpeningMinimum() {
        var hand = hand(100, 100);
        assertEquals(0, hand.actor());
        hand.act(0, CALL, 5);
        assertEquals(1, hand.actor());
        hand.act(1, CHECK, 0);
        assertEquals(Street.FLOP, hand.street());
        assertEquals(1, hand.actor());
        assertEquals(15, hand.legal().stream().filter(a -> a.type() == BET).findFirst().orElseThrow().minAmount());
        var before = hand.observeActor();
        assertThrows(IllegalArgumentException.class, () -> hand.act(1, BET, 14));
        assertEquals(before, hand.observeActor());
    }

    @Test void publicBettingLedgerShowsEveryActionAmountAndRunningHandContribution() {
        var hand = hand(100, 100);

        var opening = hand.viewFor(0).bettingActions();
        assertEquals(List.of(BettingAction.Kind.SMALL_BLIND, BettingAction.Kind.BIG_BLIND),
                opening.stream().map(BettingAction::kind).toList());
        assertEquals(List.of(5, 10), opening.stream().map(BettingAction::amount).toList());
        assertEquals(List.of(5, 10), opening.stream().map(BettingAction::totalContribution).toList());
        assertTrue(opening.stream().allMatch(action -> action.street() == Street.PREFLOP));

        hand.act(0, CALL, 5);
        hand.act(1, CHECK, 0);
        var ledger = hand.viewFor(0).bettingActions();
        assertEquals(List.of(BettingAction.Kind.SMALL_BLIND, BettingAction.Kind.BIG_BLIND,
                        BettingAction.Kind.CALL, BettingAction.Kind.CHECK),
                ledger.stream().map(BettingAction::kind).toList());
        assertEquals(List.of(5, 10, 5, 0), ledger.stream().map(BettingAction::amount).toList());
        assertEquals(List.of(5, 10, 10, 10), ledger.stream().map(BettingAction::totalContribution).toList());
        assertEquals(List.of(0, 1, 0, 1), ledger.stream().map(BettingAction::seat).toList());
        assertEquals(List.of(1, 2, 3, 4), ledger.stream().map(BettingAction::ordinal).toList());
        assertThrows(UnsupportedOperationException.class, () -> ledger.add(ledger.get(0)));
    }

    @Test void publicBettingLedgerDistinguishesRaiseCallAndAllInByStreet() {
        var hand = hand(100, 100, 100, 100);
        hand.act(3, RAISE, 20);
        hand.act(0, CALL, 20);
        hand.act(1, FOLD, 0);
        hand.act(2, CALL, 10);

        var ledger = hand.viewFor(0).bettingActions();
        assertEquals(List.of(BettingAction.Kind.RAISE, BettingAction.Kind.CALL,
                        BettingAction.Kind.FOLD, BettingAction.Kind.CALL),
                ledger.subList(2, ledger.size()).stream().map(BettingAction::kind).toList());
        assertEquals(List.of(Street.PREFLOP, Street.PREFLOP, Street.PREFLOP, Street.PREFLOP),
                ledger.subList(2, ledger.size()).stream().map(BettingAction::street).toList());
        assertEquals(List.of(5, 10, 20, 20, 5, 20),
                ledger.stream().map(BettingAction::totalContribution).toList());
    }

    @Test void rejectedBetDoesNotAppearInThePublicActionLedger() {
        var hand = hand(100, 100);
        var before = hand.viewFor(0).bettingActions();
        assertThrows(IllegalArgumentException.class, () -> hand.act(0, RAISE, 100));
        assertEquals(before, hand.viewFor(0).bettingActions());
    }

    @Test void eliminatedPhysicalSeatCanObserveTheHandWithoutPrivateCardsOrActions() {
        var hand = new HoldemHand(2, List.of(0, 100, 100), 1, BLINDS, HoldemHand.shuffledDeck(43));
        var spectator = hand.viewFor(0);
        assertEquals(0, spectator.viewer());
        assertTrue(spectator.ownCards().isEmpty());
        assertTrue(spectator.legalActions().isEmpty());
        assertEquals(0, spectator.seats().get(1).shownCards().size());
        assertEquals(0, spectator.seats().get(2).shownCards().size());
        assertEquals(2, spectator.seats().stream().filter(HoldemTableViewState.Seat::dealtCards).count());
    }

    @Test void publicLedgerTracksShortAllInAndStreetTransitionsWithoutClearingEarlierActions() {
        var hand = hand(100, 100, 15);
        hand.act(0, CALL, 10);
        hand.act(1, CALL, 5);
        hand.act(2, CHECK, 0);
        hand.act(1, CHECK, 0);
        hand.act(2, ALL_IN, 5);
        hand.act(0, CALL, 5);
        hand.act(1, CALL, 5);

        var ledger = hand.viewFor(0).bettingActions();
        var shove = ledger.stream().filter(action -> action.kind() == BettingAction.Kind.ALL_IN)
                .findFirst().orElseThrow();
        assertEquals(Street.FLOP, shove.street());
        assertEquals(5, shove.amount());
        assertEquals(15, shove.totalContribution());
        assertTrue(ledger.stream().anyMatch(action -> action.street() == Street.PREFLOP));
        assertTrue(ledger.stream().anyMatch(action -> action.street() == Street.FLOP));
        assertTrue(ledger.stream().map(BettingAction::ordinal).reduce((left, right) -> {
            assertTrue(right > left);
            return right;
        }).isPresent());
    }

    @Test void headsUpRolesAndDealingOrderFollowEitherDealer() {
        var deck = HoldemHand.shuffledDeck(42);
        for (int dealer : new int[]{0, 1}) {
            int big = 1 - dealer;
            var hand = new HoldemHand(1, List.of(100, 100), dealer, BLINDS, deck);
            var before = hand.viewFor(dealer);
            assertEquals(dealer, before.dealer());
            assertEquals(dealer, before.smallBlind());
            assertEquals(big, before.bigBlind());
            assertEquals(dealer, before.actor());
            assertEquals(5, before.seats().get(dealer).contributed());
            assertEquals(10, before.seats().get(big).contributed());
            assertEquals(List.of(deck.get(1), deck.get(3)), before.ownCards());
            assertEquals(List.of(deck.get(0), deck.get(2)), hand.viewFor(big).ownCards());
            hand.act(dealer, CALL, 5);
            hand.act(big, CHECK, 0);
            assertEquals(Street.FLOP, hand.street());
            assertEquals(big, hand.actor());
            assertTrue(hand.viewFor(dealer).legalActions().isEmpty());
        }
    }

    @Test void privateCardsAndFutureDeckDoNotChangeTheActorProjection() {
        var original = HoldemHand.shuffledDeck(42);
        var changed = new ArrayList<>(original);
        Collections.swap(changed, 0, 51);
        var stacks = List.of(100, 100);
        var first = new HoldemHand(1, stacks, 0, BLINDS, original);
        var second = new HoldemHand(1, stacks, 0, BLINDS, changed);
        assertEquals(0, first.actor());
        assertEquals(first.observeActor(), second.observeActor());
    }

    @Test void tableViewerKeepsTheirCardsWhenTheActorChanges() {
        var deck = HoldemHand.shuffledDeck(42);
        var changed = new ArrayList<>(deck);
        Collections.swap(changed, 0, 51); // In heads-up the big blind receives the first card.
        var first = new HoldemHand(1, List.of(100, 100), 0, BLINDS, deck);
        var second = new HoldemHand(1, List.of(100, 100), 0, BLINDS, changed);
        var before = first.viewFor(0);
        assertEquals(before, second.viewFor(0));
        first.act(0, CALL, 5);
        second.act(0, CALL, 5);
        var waiting = first.viewFor(0);
        assertEquals(1, waiting.actor());
        assertEquals(before.ownCards(), waiting.ownCards());
        assertEquals(waiting, second.viewFor(0));
        assertTrue(waiting.legalActions().isEmpty());
        assertNotEquals(first.viewFor(1).ownCards(), second.viewFor(1).ownCards());
        assertEquals(List.of(5, 10), before.seats().stream().map(HoldemTableViewState.Seat::contributed).toList());
    }

    @Test void tableViewKeepsOpponentCardsHiddenUntilTheHandIsSettled() {
        var hand = new HoldemHand(1, List.of(100, 100, 100, 100), 0, BLINDS, HoldemHand.shuffledDeck(55));
        var duringHand = hand.viewFor(0);
        assertTrue(duringHand.seats().stream().allMatch(seat -> seat.shownCards().isEmpty()));
        assertEquals(2, duringHand.ownCards().size());

        while (hand.actor() >= 0) {
            var legal = hand.legal();
            var action = legal.stream().filter(option -> option.type() == CHECK).findFirst()
                    .orElseGet(() -> legal.stream().filter(option -> option.type() == CALL).findFirst().orElseThrow());
            hand.act(hand.actor(), action.type(), action.amount());
        }

        var settled = hand.viewFor(0);
        assertEquals(Street.COMPLETE, settled.street());
        assertTrue(settled.seats().stream().allMatch(seat -> seat.shownCards().size() == 2));
        assertTrue(settled.seats().stream().allMatch(seat -> seat.handEvaluation() != null
                && seat.handEvaluation().bestFive().size() == 5));
        assertEquals(settled.ownCards(), settled.seats().get(0).shownCards());
    }

    @Test void foldWinDoesNotInventAShowdownCombinationAndSeparatesPotFromReturnedChips() {
        var hand = hand(100, 100, 100);
        hand.act(0, FOLD, 0);
        hand.act(1, FOLD, 0);

        var result = hand.viewFor(2);

        assertEquals(Street.COMPLETE, result.street());
        assertEquals(0, result.board().size());
        assertNull(result.seats().get(2).handEvaluation());
        assertTrue(result.seats().get(2).potWon() > 0);
        assertTrue(result.seats().get(2).returned() > 0);
        assertEquals(result.seats().get(2).received(), result.seats().get(2).potWon()
                + result.seats().get(2).returned());
    }

    @Test void policyObservationSchemaHasNoSeedDeckOrOpponentHoleCardField() {
        var names = Arrays.stream(HoldemObservation.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("handNumber", "heroSeat", "playerCount", "activeOpponentCount", "street", "holeCards",
                "communityCards", "pot", "toCall", "heroStack", "positionFromButton",
                "visibleStacks", "legalActions", "publicActionHistory", "physicalHeroSeat", "bigBlind",
                "visibleContributions", "publicOpponentTendencies"), names);
        assertFalse(names.stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).contains("seed")
                || name.toLowerCase(Locale.ROOT).contains("deck")
                || name.toLowerCase(Locale.ROOT).contains("opponenthole")
                || name.toLowerCase(Locale.ROOT).contains("remainingcards")));
    }

    @Test void publicOpponentTendenciesStayBoundToVisiblePhysicalSeats() {
        var hand = hand(100, 0, 100, 100);
        var stats = List.of(new PublicOpponentTendency(0, 2, 1),
                new PublicOpponentTendency(1, 9, 4), new PublicOpponentTendency(2, 1, 0),
                new PublicOpponentTendency(3, 3, 2));

        var observation = hand.observeActor(stats);

        assertEquals(List.of(0, 2, 3), observation.publicOpponentTendencies().stream()
                .map(PublicOpponentTendency::physicalSeat).toList());
        assertEquals(List.of(2, 1, 3), observation.publicOpponentTendencies().stream()
                .map(PublicOpponentTendency::shoveOpportunities).toList());
        assertEquals(List.of(1, 0, 2), observation.publicOpponentTendencies().stream()
                .map(PublicOpponentTendency::allInRaises).toList());
        assertThrows(IllegalArgumentException.class, () -> new PublicOpponentTendency(2, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> hand.observeActor(List.of(new PublicOpponentTendency(4, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> hand.observeActor(List.of(
                new PublicOpponentTendency(2, 1, 0), new PublicOpponentTendency(2, 2, 1))));
        assertThrows(NullPointerException.class, () -> hand.observeActor(null));
    }

    @Test void publicShoveProfileUsesAnExclusiveTwoBigBlindAndInclusiveTenBigBlindBoundary() {
        assertFalse(PublicOpponentTendency.inProfileStackRange(40, 20));
        assertTrue(PublicOpponentTendency.inProfileStackRange(41, 20));
        assertTrue(PublicOpponentTendency.inProfileStackRange(200, 20));
        assertFalse(PublicOpponentTendency.inProfileStackRange(201, 20));
        assertFalse(PublicOpponentTendency.inProfileStackRange(20, 0));
        assertFalse(PublicOpponentTendency.inProfileStackRange(-1, 20));
    }

    @Test void observationReportsOnlyOpponentsStillInTheHand() {
        var hand = hand(100, 100, 100);
        assertEquals(2, hand.observeActor().activeOpponentCount());
        hand.act(hand.actor(), FOLD, 0);
        hand.act(hand.actor(), CALL, 5);
        hand.act(hand.actor(), CHECK, 0);
        assertEquals(Street.FLOP, hand.street());
        assertEquals(1, hand.observeActor().activeOpponentCount());
    }

    @Test void observationSeparatesCompactSeatIndexFromPublicPhysicalSeatIdentity() {
        var hand = new HoldemHand(1, List.of(100, 0, 100, 100), 0, BLINDS,
                HoldemHand.shuffledDeck(4));
        assertEquals(0, hand.actor());
        hand.act(hand.actor(), FOLD, 0);
        var observation = hand.observeActor();

        assertEquals(1, observation.heroSeat(), "policy-facing index remains compact among funded seats");
        assertEquals(2, observation.physicalHeroSeat(), "public history retains the actor's stable table seat");
        assertTrue(observation.publicActionHistory().stream().anyMatch(event -> event.equals("PREFLOP 0 FOLD 0")));
    }

    @Test void observationCarriesPublicBlindAndPerSeatContributionContext() {
        var hand = new HoldemHand(1, List.of(100, 150, 200), 0, new BlindLevel(5, 10, 15),
                HoldemHand.shuffledDeck(17));
        var observation = hand.observeActor();
        assertEquals(10, observation.bigBlind());
        assertEquals(List.of(0, 5, 10), observation.visibleContributions(),
                "Public stack and contribution arrays use the same compact active-seat order");
        assertEquals(observation.pot(), observation.visibleContributions().stream().mapToInt(Integer::intValue).sum());
        assertEquals(observation.visibleStacks().get(observation.heroSeat()), observation.heroStack());
    }

    @Test void cardLabelsRoundTripStrictly() {
        for (Card card : List.of(new Card(Card.Rank.ACE, Card.Suit.SPADES),
                new Card(Card.Rank.TEN, Card.Suit.HEARTS), new Card(Card.Rank.TWO, Card.Suit.CLUBS)))
            assertEquals(card, Card.fromLabel(card.label()));
        assertEquals(Card.fromLabel("Ah"), Card.fromLabel("aH"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromLabel("1x"));
        assertThrows(IllegalArgumentException.class, () -> Card.fromLabel("Ace"));
    }

    @Test void oneShortRaiseDoesNotReopenAnActorWhoAlreadyCalled() {
        var hand = hand(100, 100, 25, 100);
        hand.act(3, RAISE, 20);
        hand.act(0, CALL, 20);
        hand.act(1, CALL, 15);
        hand.act(2, ALL_IN, 15);
        assertEquals(3, hand.actor());
        assertEquals(List.of(FOLD, CALL), hand.legal().stream().map(LegalAction::type).toList());
        assertEquals(5, hand.observeActor().toCall());
    }

    @Test void cumulativeShortRaisesReopenAtTheLastFullRaiseSize() {
        var hand = hand(30, 100, 100, 100, 25);
        hand.act(3, RAISE, 20);
        hand.act(4, ALL_IN, 25);
        hand.act(0, ALL_IN, 30);
        hand.act(1, CALL, 25);
        hand.act(2, CALL, 20);
        assertEquals(3, hand.actor());
        assertEquals(20, hand.legal().stream().filter(a -> a.type() == RAISE).findFirst().orElseThrow().minAmount());
    }

    @Test void fourShortAllInsCumulativelyReopenBeforeAnOddTiedSidePot() {
        // Deck positions 13, 14, 15, 17 and 19 become the five-card board;
        // positions 12, 16 and 18 are burns.
        var ordered = cards("9c 9h 2c 3c 4c 5c 4d 5d 2d 3d 4h 5h 6c Kc Qd Jh 6d 9s 7c 8h");
        var deck = new ArrayList<>(ordered);
        HoldemHand.shuffledDeck(7).stream().filter(card -> !ordered.contains(card)).forEach(deck::add);
        var hand = new HoldemHand(1, List.of(25, 30, 80, 80, 15, 20), 0,
                new BlindLevel(5, 10, 10), deck);

        hand.act(3, CALL, 10);
        hand.act(4, ALL_IN, 15);
        hand.act(5, ALL_IN, 20);
        hand.act(0, ALL_IN, 25);
        hand.act(1, ALL_IN, 25);
        hand.act(2, CALL, 20);
        assertEquals(3, hand.actor());
        assertEquals(30, hand.legal().stream().filter(action -> action.type() == RAISE)
                .findFirst().orElseThrow().minAmount());
        hand.act(3, RAISE, 30);
        hand.act(2, CALL, 10);
        hand.act(2, CHECK, 0);
        hand.act(3, CHECK, 0);
        hand.act(2, CHECK, 0);
        hand.act(3, CHECK, 0);
        hand.act(2, CHECK, 0);
        hand.act(3, CHECK, 0);
        assertEquals(Street.COMPLETE, hand.street());

        assertEquals(List.of(0, 76, 134, 40, 0, 0), hand.stacks());
        assertEquals(List.of(
                "POT cap=15 amount=90 eligible=[1, 2, 3, 4, 5, 0] winners=[1, 2]",
                "POT cap=20 amount=25 eligible=[1, 2, 3, 5, 0] winners=[1, 2]",
                "POT cap=25 amount=20 eligible=[1, 2, 3, 0] winners=[1, 2]",
                "POT cap=30 amount=15 eligible=[1, 2, 3] winners=[1, 2]",
                "POT cap=40 amount=20 eligible=[2, 3] winners=[2]",
                "SHOWDOWN [0, 76, 94, 0, 0, 0]"), hand.history().stream()
                .filter(event -> event.startsWith("POT ") || event.startsWith("SHOWDOWN ")).toList());
    }

    @Test void sidePotsExcludeFoldedChipsAndReturnAnUncalledOverage() {
        var settlement = PotSettlement.settle(new int[]{20, 35, 55, 15}, new int[]{9, 8, 7, -1}, 2);
        assertArrayEquals(new int[]{75, 30, 20, 0}, settlement.awards());
        assertArrayEquals(new int[]{75, 30, 0, 0}, settlement.potWon());
        assertArrayEquals(new int[]{0, 0, 20, 0}, settlement.returned());
        assertArrayEquals(new int[]{2, 0, 1},
                PotSettlement.settle(new int[]{1, 1, 1}, new int[]{10, -1, 10}, 2).awards());
    }

    @Test void shortAllInFromTheSmallBlindDoesNotReopenTheOriginalCaller() {
        var blind = new BlindLevel(40, 80, 80);
        var hand = new HoldemHand(12, List.of(0, 120, 1970, 1110), 3, blind,
                HoldemHand.shuffledDeck(8820756843173759038L));
        hand.act(3, CALL, 80);
        hand.act(1, ALL_IN, 80);
        assertEquals(2, hand.actor());
        assertTrue(hand.legal().stream().anyMatch(action -> action.type() == RAISE));
        hand.act(2, CALL, 40);
        assertEquals(3, hand.actor());
        var before = hand.observeActor();
        assertThrows(IllegalArgumentException.class, () -> hand.act(3, RAISE, 120));
        assertThrows(IllegalArgumentException.class, () -> hand.act(3, ALL_IN, 1030));
        assertEquals(before, hand.observeActor());
        hand.act(3, CALL, 40);
        assertEquals(Street.FLOP, hand.street());
    }

    @Test void allInShowdownAuditsEachPotAndSeparatesTheUncalledReturn() {
        var prefix = cards("Qc Tc Ac Kc Qd Td Ad Kd 3c 2c 4d 7h 5c 9s 6c Jc");
        var deck = new ArrayList<>(prefix);
        HoldemHand.shuffledDeck(0).stream().filter(card -> !prefix.contains(card)).forEach(deck::add);
        var hand = new HoldemHand(1, List.of(20, 35, 55, 15), 0, BLINDS, deck);
        hand.act(3, ALL_IN, 15);
        hand.act(0, ALL_IN, 20);
        hand.act(1, CALL, 15);
        hand.act(2, ALL_IN, 45);
        hand.act(1, CALL, 15);
        assertEquals(List.of(15, 30, 20, 60), hand.stacks());
        assertEquals(List.of(
                "POT cap=15 amount=60 eligible=[1, 2, 3, 0] winners=[3]",
                "POT cap=20 amount=15 eligible=[1, 2, 0] winners=[0]",
                "POT cap=35 amount=30 eligible=[1, 2] winners=[1]",
                "RETURN seat=2 amount=20",
                "SHOWDOWN [15, 30, 20, 60]"), hand.history().stream()
                .filter(event -> event.startsWith("POT ") || event.startsWith("RETURN ") || event.startsWith("SHOWDOWN "))
                .toList());
    }

    @Test void evaluatorUsesBestFiveAndTreatsWheelAsFiveHigh() {
        var board = cards("As Ks Qs Js Ts");
        var first = new ArrayList<>(cards("2c 3d"));
        first.addAll(board);
        var second = new ArrayList<>(cards("9c 9d"));
        second.addAll(board);
        assertEquals(HandRank.bestOf(first), HandRank.bestOf(second));
        assertTrue(HandRank.bestOf(cards("2c 3h 4s 5d 6c")) > HandRank.bestOf(cards("Ac 2h 3s 4d 5c")));
        assertThrows(IllegalArgumentException.class, () -> HandRank.bestOf(cards("Ac Ac 3s 4d 5c")));
    }

    @Test void evaluatorReturnsTheExactDeterministicFiveCardsWithoutUsingSuitAsARankTieBreaker() {
        var royalBoard = cards("As Ks Qs Js Ts 2c 3d");
        var royal = HandRank.evaluateBestOf(royalBoard);
        assertEquals(HandRank.Category.STRAIGHT_FLUSH, royal.category());
        assertEquals(HandRank.bestOf(royalBoard), royal.score());
        assertEquals(royalBoard.subList(0, 5), royal.bestFive());
        assertThrows(UnsupportedOperationException.class, () -> royal.bestFive().add(cards("4c").get(0)));

        var doubleTrips = cards("As Ah Ad Ks Kh Kd 2c");
        var fullHouse = HandRank.evaluateBestOf(doubleTrips);
        assertEquals(HandRank.Category.FULL_HOUSE, fullHouse.category());
        assertEquals(cards("As Ah Ad Ks Kh"), fullHouse.bestFive());

        var wheel = HandRank.evaluateBestOf(cards("Ac 2h 3s 4d 5c 9h Kd"));
        assertEquals(HandRank.Category.STRAIGHT, wheel.category());
        assertEquals(cards("Ac 2h 3s 4d 5c"), wheel.bestFive());
    }

    @Test void handCategoryNamesCoverEveryPackedRankClass() {
        assertEquals(HandRank.Category.HIGH_CARD, HandRank.categoryOf(cards("Ac 9d 7h 4s 2c")));
        assertEquals(HandRank.Category.ONE_PAIR, HandRank.categoryOf(cards("Ac Ad 9h 4s 2c")));
        assertEquals(HandRank.Category.TWO_PAIR, HandRank.categoryOf(cards("Ac Ad 9h 9s 2c")));
        assertEquals(HandRank.Category.THREE_OF_A_KIND, HandRank.categoryOf(cards("Ac Ad Ah 9s 2c")));
        assertEquals(HandRank.Category.STRAIGHT, HandRank.categoryOf(cards("Ac Kd Qh Js Tc")));
        assertEquals(HandRank.Category.FLUSH, HandRank.categoryOf(cards("Ac 9c 7c 4c 2c")));
        assertEquals(HandRank.Category.FULL_HOUSE, HandRank.categoryOf(cards("Ac Ad Ah 9s 9c")));
        assertEquals(HandRank.Category.FOUR_OF_A_KIND, HandRank.categoryOf(cards("Ac Ad Ah As 9c")));
        assertEquals(HandRank.Category.STRAIGHT_FLUSH, HandRank.categoryOf(cards("Ac Kc Qc Jc Tc")));
    }

    private static List<Card> cards(String labels) {
        var byLabel = new HashMap<String, Card>();
        HoldemHand.shuffledDeck(0).forEach(card -> byLabel.put(card.label(), card));
        return Arrays.stream(labels.split(" ")).map(byLabel::get).toList();
    }

    @Test void minuteAndEliminationsAccumulateOnlyAtTheNextHand() {
        var levels = List.of(BLINDS, new BlindLevel(10, 20, 25), new BlindLevel(20, 40, 50),
                new BlindLevel(30, 60, 70), new BlindLevel(40, 80, 90), new BlindLevel(50, 100, 110));
        var tournament = new Tournament(new Tournament.Rules(4, 100, 60, levels), 0);
        var first = tournament.startHand(59, 42);
        assertEquals(0, first.levelIndex());
        assertEquals(0, tournament.settledRank(0));
        var hand = first.hand();
        assertThrows(IllegalStateException.class, () -> tournament.startHand(60, 43));
        assertEquals(BLINDS, hand.level());
        while (hand.actor() >= 0) hand.act(hand.actor(), FOLD, 0);
        assertEquals(1, tournament.startHand(60, 43).levelIndex());

        // A fold win above leaves all seats funded. A separate controlled all-in eliminates seats.
        var knockout = new Tournament(new Tournament.Rules(4, 100, 60, levels), 0);
        // Find a split showdown so at least two seats remain and the following level is observable.
        for (long seed = 0; seed < 100; seed++) {
            knockout = new Tournament(new Tournament.Rules(4, 100, 60, levels), 0);
            var allIn = knockout.startHand(0, seed).hand();
            while (allIn.actor() >= 0) allIn.act(allIn.actor(), ALL_IN, allIn.observeActor().heroStack());
            long alive = knockout.stacks().stream().filter(n -> n > 0).count();
            if (alive > 1 && alive < 4) {
                var stacks = knockout.stacks();
                for (int seat = 0; seat < stacks.size(); seat++) {
                    assertEquals(stacks.get(seat) == 0 ? alive + 1 : 0, knockout.settledRank(seat));
                }
                assertTrue(knockout.finishingRanks().stream().allMatch(rank -> rank == 0));
                var next = knockout.startHand(60, 100);
                assertEquals(4 - alive, next.eliminated());
                assertEquals(5 - alive, next.levelIndex());
                for (int seat = 0; seat < stacks.size(); seat++) {
                    assertEquals(stacks.get(seat) == 0 ? alive + 1 : 0, knockout.settledRank(seat));
                }
                return;
            }
        }
        fail("Expected a deterministic split showdown");
    }

    @Test void tournamentRanksSimultaneousKnockoutsTiedAndKeepsOneChampion() {
        var levels = List.of(new BlindLevel(5, 10, 10));
        Tournament tournament = null;
        HoldemHand actual = null;
        for (long seed = 0; seed < 100; seed++) {
            tournament = new Tournament(new Tournament.Rules(4, 100, 60, levels), 0);
            actual = tournament.startHand(0, seed).hand();
            while (actual.actor() >= 0) actual.act(actual.actor(), ALL_IN, actual.observeActor().heroStack());
            if (actual.stacks().stream().filter(stack -> stack > 0).count() == 1) break;
            tournament = null;
        }
        assertNotNull(tournament, "A deterministic all-in hand must produce a unique champion");
        assertNotNull(actual);
        Tournament completed = tournament;
        for (int seat = 0; seat < actual.stacks().size(); seat++) {
            assertEquals(actual.stacks().get(seat) == 0 ? 2 : 1, completed.settledRank(seat));
        }
        completed.finish();
        var ranks = completed.finishingRanks();
        int champion = actual.stacks().indexOf(400);
        assertTrue(champion >= 0);
        assertEquals(1, ranks.get(champion));
        assertEquals(3, ranks.stream().filter(rank -> rank == 2).count());
        assertEquals(0, ranks.stream().filter(rank -> rank == 0).count());
        assertDoesNotThrow(completed::finish, "Finalizing a completed tournament is idempotent");
        assertThrows(IllegalStateException.class, () -> completed.startHand(60, 20));
    }
}
