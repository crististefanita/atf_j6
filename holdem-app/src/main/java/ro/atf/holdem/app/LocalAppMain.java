package ro.atf.holdem.app;

import ro.atf.holdem.ai.TightAggressiveBaseline;
import ro.atf.holdem.core.*;
import ro.atf.holdem.online.OpenPokerConfig;
import ro.atf.holdem.online.OpenPokerTransport;

import java.util.List;

public final class LocalAppMain {
    private LocalAppMain() { }

    public static void main(String[] args) {
        var observation = new HoldemObservation(
                1,
                0,
                6,
                Street.PREFLOP,
                List.of(new Card(Card.Rank.ACE, Card.Suit.SPADES), new Card(Card.Rank.QUEEN, Card.Suit.SPADES)),
                List.of(),
                30,
                20,
                1980,
                0,
                List.of(1980, 2000, 2000, 2000, 2000, 2000),
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20), new LegalAction(ActionType.RAISE, 60, 60, 2000)),
                List.of("seat-4:post-blind:10", "seat-5:post-blind:20"));

        var decision = new TightAggressiveBaseline().decide(observation);
        var online = new OpenPokerTransport(OpenPokerConfig.fromEnvironment());
        System.out.println("ATF J6 local Hold'em vertical slice");
        System.out.println("hero=" + observation.holeCards().stream().map(Card::label).toList());
        System.out.println("decision=" + decision.action() + " amount=" + decision.amount() + " policy=" + decision.policyVersion());
        System.out.println("onlineEndpoint=" + online.endpoint() + " credentialPresent=" + online.hasCredential() + " dryRun=" + online.dryRun());
        System.out.println("status=local-only; no external action was sent");
    }
}
