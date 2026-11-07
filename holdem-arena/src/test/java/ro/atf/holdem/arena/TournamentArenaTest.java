package ro.atf.holdem.arena;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.holdem.ai.*;
import ro.atf.holdem.core.*;
import static org.junit.jupiter.api.Assertions.*;

class TournamentArenaTest {
    @TempDir Path temp;

    @Test void yamlDrivesRepeatableTournamentsAtEveryTableSize() throws IOException {
        var profiles = TournamentProfiles.load(profilePath());
        assertEquals(Set.of(4, 5, 6), profiles.keySet());
        for (var rules : profiles.values()) {
            var policies = Collections.<DecisionPolicy>nCopies(rules.seats(), new TightAggressiveBaseline());
            var first = new StringWriter();
            var second = new StringWriter();
            var outcome = TournamentArena.play(rules, 45, 12, 500, policies, new PrintWriter(first));
            assertEquals(outcome, TournamentArena.play(rules, 45, 12, 500, policies, new PrintWriter(second)));
            assertEquals(first.toString(), second.toString());
            assertEquals(rules.startingStack() * rules.seats(), outcome.stacks().get(outcome.winner()));
            assertEquals(1, outcome.stacks().stream().filter(n -> n > 0).count());
        }
    }

    @Test void rejectsDuplicateYamlKeys() throws IOException {
        var path = Files.writeString(temp.resolve("duplicate.yaml"), Files.readString(profilePath()) + "\nschemaVersion: 1\n");
        assertThrows(IllegalArgumentException.class, () -> TournamentProfiles.load(path));
    }

    @Test void arenaBluffMixComesFromTheVersionedPolicyYaml() throws IOException {
        Path policy = Path.of("config", "j6-policy.yaml");
        if (!Files.exists(policy)) policy = Path.of("..", "config", "j6-policy.yaml");
        var config = TournamentPolicyConfig.load(policy);
        assertEquals(18, config.bluffFrequencyPercent());
        assertEquals(2, config.latePositionWindow());
        assertEquals(45, config.looseOpenFrequencyPercent());
        assertEquals(20, config.looseRaiseFrequencyPercent());
        assertEquals(35, config.looseMaxCallPotPercent());
        assertEquals(0, config.looseBetPotPercent());
        assertEquals(10, config.looseLatePositionAdjustmentPercent());
        assertEquals(List.of(256, 2, 8, 18, 60), List.of(config.equitySamples(),
                config.cashCallMarginPercent(), config.tournamentCallMarginPercent(),
                config.equityRaiseEdgePercent(), config.valueBetThresholdPercent()));
        assertEquals(new ContextualEquityPolicy.ContextWeights(3, 3, 2, 5, 2, 2, 3, 3, 2, 2, 164),
                config.equityContextWeights());
        assertThrows(IllegalArgumentException.class, () -> new TournamentPolicyConfig(101, 2));
        assertThrows(IllegalArgumentException.class, () -> new TournamentPolicyConfig(18, 0));
        assertThrows(IllegalArgumentException.class, () -> new TournamentPolicyConfig(18, 2, 101, 20, 35));
        Path wrongVersion = Files.writeString(temp.resolve("policy-v2.yaml"), "schemaVersion: 2\nbluff:\n  frequencyPercent: 18\n  latePositionWindow: 2\n");
        assertThrows(IllegalArgumentException.class, () -> TournamentPolicyConfig.load(wrongVersion));
    }

    @Test void pairedCohortsChangeOnlyTheCandidateStyleSlot() throws IOException {
        Path policy = Path.of("config", "j6-policy.yaml");
        if (!Files.exists(policy)) policy = Path.of("..", "config", "j6-policy.yaml");
        var config = TournamentPolicyConfig.load(policy);
        for (int seats : List.of(4, 5, 6)) {
            var styles = new ArrayList<DecisionPolicy>();
            var controls = new ArrayList<DecisionPolicy>();
            for (int seat = 0; seat < seats; seat++) {
                styles.add(TournamentArena.policyForSeat(seat, 12, "loose", config));
                controls.add(TournamentArena.policyForSeat(seat, 12, "control", config));
            }
            int candidate = -1;
            for (int seat = 0; seat < seats; seat++) {
                if (!styles.get(seat).getClass().equals(controls.get(seat).getClass())) {
                    assertEquals(-1, candidate, "Only one candidate seat should be substituted");
                    candidate = seat;
                    assertInstanceOf(LooseAggressivePolicy.class, styles.get(seat));
                    assertInstanceOf(TightAggressiveBaseline.class, controls.get(seat));
                }
            }
            assertTrue(candidate >= 0);
            for (int seat = 0; seat < seats; seat++)
                if (seat != candidate) assertEquals(styles.get(seat).getClass(), controls.get(seat).getClass());
        }
        assertThrows(IllegalArgumentException.class, () -> TournamentArena.policyForSeat(0, 1, "oracle", config));
    }

    @Test void tuningProfilesLoadExactComparableLooseAggressiveThresholds() throws IOException {
        Path root = Path.of("config", "policy-tuning");
        if (!Files.exists(root)) root = Path.of("..", "config", "policy-tuning");
        var conservative = TournamentPolicyConfig.load(root.resolve("j6-policy-conservative.yaml"));
        var balanced = TournamentPolicyConfig.load(Path.of(root.getParent().toString(), "j6-policy.yaml"));
        var aggressive = TournamentPolicyConfig.load(root.resolve("j6-policy-aggressive.yaml"));
        var pot50 = TournamentPolicyConfig.load(root.resolve("j6-policy-pot50.yaml"));
        var pot100 = TournamentPolicyConfig.load(root.resolve("j6-policy-pot100.yaml"));
        var pot150 = TournamentPolicyConfig.load(root.resolve("j6-policy-pot150.yaml"));
        var positionNeutral = TournamentPolicyConfig.load(root.resolve("j6-policy-position-neutral.yaml"));
        assertEquals(List.of(25, 10, 25), List.of(conservative.looseOpenFrequencyPercent(),
                conservative.looseRaiseFrequencyPercent(), conservative.looseMaxCallPotPercent()));
        assertEquals(List.of(45, 20, 35), List.of(balanced.looseOpenFrequencyPercent(),
                balanced.looseRaiseFrequencyPercent(), balanced.looseMaxCallPotPercent()));
        assertEquals(10, balanced.looseLatePositionAdjustmentPercent());
        assertEquals(List.of(65, 35, 50), List.of(aggressive.looseOpenFrequencyPercent(),
                aggressive.looseRaiseFrequencyPercent(), aggressive.looseMaxCallPotPercent()));
        assertEquals(10, conservative.looseLatePositionAdjustmentPercent());
        assertEquals(10, aggressive.looseLatePositionAdjustmentPercent());
        assertEquals(List.of(50, 100, 150), List.of(pot50.looseBetPotPercent(), pot100.looseBetPotPercent(),
                pot150.looseBetPotPercent()));
        assertEquals(List.of(10, 10, 10), List.of(pot50.looseLatePositionAdjustmentPercent(),
                pot100.looseLatePositionAdjustmentPercent(), pot150.looseLatePositionAdjustmentPercent()));
        assertEquals(0, positionNeutral.looseLatePositionAdjustmentPercent());
        assertThrows(IllegalArgumentException.class, () -> new TournamentPolicyConfig(18, 2, 45, 20, 35, 201));
        assertThrows(IllegalArgumentException.class, () -> new TournamentPolicyConfig(18, 2, 45, 20, 35, 0, 51));
    }

    @Test void everyCheckedInPolicyYamlSatisfiesTheStrictContextWeightSchema() throws IOException {
        Path config = Path.of("config");
        if (!Files.exists(config)) config = Path.of("..", "config");
        var profiles = new ArrayList<Path>(List.of(config.resolve("j6-policy.yaml"),
                config.resolve("j6-policy-equity.yaml")));
        try (var tuning = Files.list(config.resolve("policy-tuning"))) {
            tuning.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted().forEach(profiles::add);
        }

        assertTrue(profiles.size() >= 4, "The checked-in comparison profile set must be present");
        for (Path profile : profiles) {
            assertDoesNotThrow(() -> TournamentPolicyConfig.load(profile),
                    "Policy YAML must include the exact current context schema: " + profile);
        }
    }

    @Test void cliKeepsTheRequestedStyleWhenAnExplicitPolicyYamlIsPassed() {
        assertEquals("mixed", TournamentArena.cohortFromArgs(new String[]{"profile", "trace", "10", "7000"}));
        assertEquals("calling", TournamentArena.cohortFromArgs(new String[]{"profile", "trace", "10", "7000", "calling"}));
        assertEquals("loose", TournamentArena.cohortFromArgs(new String[]{"profile", "trace", "10", "7000", "loose", "config.yaml"}));
        assertEquals(4, TournamentArena.buttonOffsetFromArgs(new String[]{"p", "t", "10", "7", "loose", "c.yaml", "4"}));
        assertEquals(0, TournamentArena.buttonOffsetFromArgs(new String[]{"p", "t", "10", "7", "loose", "c.yaml"}));
        assertEquals(Path.of("c.yaml"), TournamentArena.policyPathFromArgs(
                new String[]{"p", "t", "10", "7", "legacy-pot", "c.yaml", "4"}));
        assertEquals(Path.of("default.yaml"), TournamentArena.policyPathFromArgs(
                new String[]{"p", "t", "10"}, Path.of("default.yaml")));
        var config = new TournamentPolicyConfig(18, 2, 45, 20, 35);
        assertInstanceOf(CallingStationPolicy.class, TournamentArena.policyForSeat(3, 0,
                TournamentArena.cohortFromArgs(new String[]{"p", "t", "10", "7", "calling", "c.yaml"}), config));
        assertInstanceOf(PotSizedPolicy.class, TournamentArena.policyForSeat(3, 0, "sized",
                new TournamentPolicyConfig(18, 2, 45, 20, 35, 50)));
        assertInstanceOf(TightAggressiveBaseline.class, TournamentArena.policyForSeat(0, 0, "legacy", config));
        assertInstanceOf(CallingStationPolicy.class, TournamentArena.policyForSeat(1, 0, "legacy", config));
        assertInstanceOf(CallingStationPolicy.class, TournamentArena.policyForSeat(0, 1, "legacy", config));
        assertInstanceOf(PotSizedPolicy.class, TournamentArena.policyForSeat(3, 0, "legacy-pot",
                new TournamentPolicyConfig(18, 2, 45, 20, 35, 50)));
        assertInstanceOf(CallingStationPolicy.class, TournamentArena.policyForSeat(1, 0, "legacy-pot", config),
                "Only the defined candidate slot changes; old opponent seats remain check/call");
    }

    @Test void buttonRotationKeepsTheSeededDeckStreamForTheWholeTournament() throws IOException {
        var rules = TournamentProfiles.load(profilePath()).get(4);
        var policies = Collections.<DecisionPolicy>nCopies(4, new TightAggressiveBaseline());
        var defaultTrace = new StringWriter();
        var rotatedTrace = new StringWriter();
        TournamentArena.play(rules, 45, 12, 500, policies, 0, new PrintWriter(defaultTrace));
        TournamentArena.play(rules, 45, 12, 500, policies, 1, new PrintWriter(rotatedTrace));
        var defaultLines = defaultTrace.toString().lines().toList();
        var rotatedLines = rotatedTrace.toString().lines().toList();
        assertTrue(defaultLines.getFirst().contains("firstButton=1"));
        assertTrue(rotatedLines.getFirst().contains("firstButton=2"));
        var defaultDeals = defaultLines.stream().filter(line -> line.startsWith("HAND "))
                .map(line -> line.replaceAll(".* dealSeed=(-?\\d+)$", "$1")).toList();
        var rotatedDeals = rotatedLines.stream().filter(line -> line.startsWith("HAND "))
                .map(line -> line.replaceAll(".* dealSeed=(-?\\d+)$", "$1")).toList();
        int commonHands = Math.min(defaultDeals.size(), rotatedDeals.size());
        assertTrue(commonHands > 1);
        assertEquals(defaultDeals.subList(0, commonHands), rotatedDeals.subList(0, commonHands),
                "Changing only the initial button must preserve each hand's shuffle seed");
    }

    @Test void tournamentDecisionTraceExposesOnlyTheActorAndPublicBetSizingContext() throws IOException {
        var rules = TournamentProfiles.load(profilePath()).get(4);
        var policies = Collections.<DecisionPolicy>nCopies(4, new TightAggressiveBaseline());
        var trace = new StringWriter();
        TournamentArena.play(rules, 45, 12, 500, policies, new PrintWriter(trace));
        var decision = trace.toString().lines().filter(line -> line.startsWith("DECISION ")).findFirst().orElseThrow();
        assertTrue(decision.matches(".*positionFromButton=\\d+ street=(PREFLOP|FLOP|TURN|RIVER) pot=\\d+ toCall=\\d+ heroStack=\\d+ activeOpponents=\\d+ bigBlind=\\d+ visibleSeatIds=\\[[0-5,]*\\] visibleStacks=\\[[0-9,]*\\] visibleContributions=\\[[0-9,]*\\] foldedSeatIds=\\[[0-5,]*\\] priorAllInActors=\\[[0-5,]*\\] priorAllInEvents=\\[[0-5:callraise,]*\\] publicOpponentTendencies=\\[[0-5:0-9,]*\\] legalActions=.*"), decision);
        assertFalse(decision.contains("holeCards="), "A replay trace must not expose opponents' private cards");
        assertFalse(decision.contains("futureBoard="), "A replay trace must not expose undealt board cards");
    }

    @Test void tournamentSharesOnlyCompletedHandsShortStackShoveHistory() {
        var rules = new Tournament.Rules(4, 100, 12, List.of(new BlindLevel(5, 10, 15),
                new BlindLevel(10, 20, 25), new BlindLevel(20, 40, 50), new BlindLevel(40, 80, 100),
                new BlindLevel(80, 160, 200), new BlindLevel(160, 320, 400), new BlindLevel(320, 640, 800)));
        var policies = new ArrayList<DecisionPolicy>();
        policies.add(observation -> observation.handNumber() == 1
                && observation.legalActions().stream().anyMatch(action -> action.type() == ActionType.ALL_IN
                && action.amount() > observation.toCall())
                ? new Decision(ActionType.ALL_IN, observation.heroStack(), "TEST-SHORT-SHOVE", "public-history probe")
                : safeCheckOrFold(observation));
        for (int seat = 1; seat < 4; seat++) policies.add(TournamentArenaTest::safeCheckOrFold);
        var trace = new StringWriter();

        TournamentArena.play(rules, 13, 12, 500, policies, new PrintWriter(trace));

        var lines = trace.toString().lines().toList();
        var firstHand = lines.stream().dropWhile(line -> !line.startsWith("HAND 1 "))
                .takeWhile(line -> !line.startsWith("STACKS ")).filter(line -> line.startsWith("DECISION ")).toList();
        assertTrue(firstHand.stream().allMatch(line -> line.contains("publicOpponentTendencies=[0:0:0")),
                "The current hand must not leak its own later actions into earlier decisions");
        var secondHand = lines.stream().dropWhile(line -> !line.startsWith("HAND 2 "))
                .takeWhile(line -> !line.startsWith("STACKS ")).filter(line -> line.startsWith("DECISION ")).toList();
        assertFalse(secondHand.isEmpty());
        assertTrue(secondHand.stream().allMatch(line -> line.contains("0:1:1")),
                "A completed public short-stack shove should be visible in the next hand");
    }

    private static Decision safeCheckOrFold(ro.atf.holdem.core.HoldemObservation observation) {
        return observation.toCall() > 0
                ? new Decision(ActionType.FOLD, 0, "TEST-FOLD", "avoid price")
                : new Decision(ActionType.CHECK, 0, "TEST-CHECK", "no price");
    }

    @Test void legacyPotCandidateReplacesOnlyItsSeededSeatsAcrossEveryTableSize() {
        var sizing = new TournamentPolicyConfig(18, 2, 45, 20, 35, 50);
        for (int seats = 4; seats <= 6; seats++) {
            for (int seedResidue = 0; seedResidue < 4; seedResidue++) {
                for (int seat = 0; seat < seats; seat++) {
                    var policy = TournamentArena.policyForSeat(seat, seedResidue, "legacy-pot", sizing);
                    boolean candidate = Math.floorMod(seat + seedResidue, 4) == 3;
                    if (candidate) assertInstanceOf(PotSizedPolicy.class, policy);
                    else if (Math.floorMod(seat + seedResidue, 2) == 0)
                        assertInstanceOf(TightAggressiveBaseline.class, policy);
                    else assertInstanceOf(CallingStationPolicy.class, policy);
                }
            }
        }
    }

    @Test void equityCandidateIsSelectableAndChangesOnlyItsSeededSeatSlots() throws IOException {
        Path policy = Path.of("config", "j6-policy.yaml");
        if (!Files.exists(policy)) policy = Path.of("..", "config", "j6-policy.yaml");
        var config = TournamentPolicyConfig.load(policy);
        Path equityPolicy = policy.resolveSibling("j6-policy-equity.yaml");
        var equityConfig = TournamentPolicyConfig.load(equityPolicy);
        assertEquals(512, equityConfig.equitySamples());
        assertEquals(config.cashCallMarginPercent(), equityConfig.cashCallMarginPercent());
        Path missingEquity = Files.writeString(temp.resolve("missing-equity.yaml"),
                Files.readString(policy).replaceAll("(?s)equityDecision:\\R.*$", ""));
        assertThrows(IllegalArgumentException.class, () -> TournamentPolicyConfig.load(missingEquity));
        for (int seats = 4; seats <= 6; seats++) {
            for (int seedResidue = 0; seedResidue < 4; seedResidue++) {
                for (int seat = 0; seat < seats; seat++) {
                    boolean candidate = Math.floorMod(seat + seedResidue, 4) == 3;
                    var policyCandidate = TournamentArena.policyForSeat(seat, seedResidue, "equity", config);
                    var control = TournamentArena.policyForSeat(seat, seedResidue, "control", config);
                    if (candidate) assertInstanceOf(ContextualEquityPolicy.class, policyCandidate);
                    else assertEquals(control.getClass(), policyCandidate.getClass());

                    var legacyEquity = TournamentArena.policyForSeat(seat, seedResidue, "legacy-equity", config);
                    var legacy = TournamentArena.policyForSeat(seat, seedResidue, "legacy", config);
                    if (candidate) assertInstanceOf(ContextualEquityPolicy.class, legacyEquity);
                    else assertEquals(legacy.getClass(), legacyEquity.getClass());
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> TournamentArena.policyForSeat(0, 0, "unknown", config));
    }

    private static Path profilePath() {
        var local = Path.of("config", "j6-tournaments.yaml");
        return Files.exists(local) ? local : Path.of("..").resolve(local);
    }
}
