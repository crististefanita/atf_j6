package ro.atf.holdem.arena;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TournamentStrategyArenaTest {
    @Test void hardLineupAddsOnlyThePressureProfileToAnEqualBudgetControl() throws Exception {
        var lineup = TournamentStrategyLineups.load(config("j6-strategy-lineups-hard-pressure.yaml"));
        var control = TournamentPolicyConfig.load(projectPath(lineup.equityPolicyFiles().get("equity-stack-aware")));
        var hard = TournamentPolicyConfig.load(projectPath(lineup.equityPolicyFiles().get("equity-hard-pressure")));
        assertNull(control.hardPressure());
        assertEquals(ro.atf.holdem.ai.ContextualEquityPolicy.HardPressure.TOURNAMENT, hard.hardPressure());
        assertEquals(control.equitySamples(), hard.equitySamples());
        assertEquals(control.equityContextWeights(), hard.equityContextWeights());
        for (int seats : List.of(4, 5, 6)) assertTrue(lineup.forSeats(seats).contains("equity-hard-pressure"));
    }

    @TempDir Path temp;

    @Test void optionalResponseModelRequiresValidPublicCellsAndExactKeys() throws Exception {
        String active = Files.readString(config("policy-tuning/j6-policy-hard-pressure.yaml")).stripTrailing();
        assertNull(TournamentPolicyConfig.load(config("policy-tuning/j6-policy-hard-pressure.yaml"))
                .hardPressure().responseModel());
        String block = """

                  responseModel:
                    minSamples: 32
                    priorStrength: 30
                    foldWeight: 16
                    raiseWeight: 24
                    maxAdjustment: 4
                    observations:
                      OPEN.late.cheap.funded: {opportunities: 40, folds: 21, raises: 9}
                """;
        Path path = temp.resolve("hard-response.yaml");
        var model = TournamentPolicyConfig.load(Files.writeString(path, active + block)).hardPressure().responseModel();
        assertEquals(32, model.minSamples());
        assertEquals(40, model.observations().values().iterator().next().opportunities());
        assertFalse(model.penalizeRaisesOnly());
        String penalty = block.replace("    observations:", "    penalizeRaisesOnly: true\n    observations:");
        assertTrue(TournamentPolicyConfig.load(Files.writeString(path, active + penalty))
                .hardPressure().responseModel().penalizeRaisesOnly());
        for (String invalid : List.of(block.replace("folds: 21", "folds: 32"),
                block.replace("OPEN.late", "OPEN.middle"), block.replace("raiseWeight", "raiseBias"),
                penalty.replace("penalizeRaisesOnly: true", "penalizeRaisesOnly: 'true'"))) {
            Files.writeString(path, active + invalid);
            assertThrows(IllegalArgumentException.class, () -> TournamentPolicyConfig.load(path));
        }
    }

    @Test void hardRaiseWeightsAreIndependentAndRejectIncompleteOrUnknownKeys() throws Exception {
        String legacy = Files.readString(config("policy-tuning/j6-policy-hard-pressure.yaml"))
                .replaceAll("(?ms)^  raiseContext:.*\\z", "").stripTrailing();
        var path = temp.resolve("hard-context.yaml");
        assertNull(TournamentPolicyConfig.load(Files.writeString(path, legacy)).hardPressure().raiseContext());
        String context = """

                  raiseContext:
                    baseMargin: 8
                    multiwayMargin: 5
                    positionMargin: 4
                    aggressionMargin: 3
                    largeBetMargin: 4
                    allInMargin: 6
                    commitmentMargin: 10
                    commitmentThresholdPercent: 60
                """;
        var parsed = TournamentPolicyConfig.load(Files.writeString(path, legacy + context)).hardPressure().raiseContext();
        assertEquals(new ro.atf.holdem.ai.ContextualEquityPolicy.RaiseContext(8, 5, 4, 3, 4, 6, 10, 60), parsed);
        for (String invalid : List.of(context.replace("positionMargin", "position"),
                context.replace("    positionMargin: 4\n", ""),
                context.replace("ThresholdPercent: 60", "ThresholdPercent: 49"))) {
            Files.writeString(path, legacy + invalid);
            assertThrows(IllegalArgumentException.class, () -> TournamentPolicyConfig.load(path));
        }
    }

    @Test void bigBlindValueRaiseIsOptionalAndRequiresABoolean() throws Exception {
        String valid = Files.readString(config("policy-tuning/j6-policy-hard-pressure.yaml"))
                .replaceAll("(?m)^  bigBlindValueRaise:.*\\R?", "").stripTrailing();
        var path = temp.resolve("hard-pressure.yaml");
        assertFalse(TournamentPolicyConfig.load(Files.writeString(path, valid)).hardPressure().bigBlindValueRaise());
        assertTrue(TournamentPolicyConfig.load(Files.writeString(path, valid + "\n  bigBlindValueRaise: true\n"))
                .hardPressure().bigBlindValueRaise());
        Files.writeString(path, valid + "\n  bigBlindValueRaise: 'true'\n");
        assertThrows(IllegalArgumentException.class, () -> TournamentPolicyConfig.load(path));
    }

    @Test void everyRoleVisitsEverySeatAndButtonPositionForEachTableSize() throws Exception {
        var lineups = TournamentStrategyLineups.load(config("j6-strategy-lineups.yaml"));
        for (int seats = 4; seats <= 6; seats++) {
            var roster = lineups.forSeats(seats);
            var roleByPosition = new HashMap<String, Set<Integer>>();
            var rolesBySeat = new HashMap<String, Set<Integer>>();
            for (int policyOffset = 0; policyOffset < seats; policyOffset++) {
                var rotated = TournamentStrategyArena.rotate(roster, policyOffset);
                assertEquals(seats, new HashSet<>(rotated).size());
                for (int seat = 0; seat < seats; seat++)
                    rolesBySeat.computeIfAbsent(rotated.get(seat), ignored -> new HashSet<>()).add(seat);
                for (int buttonOffset = 0; buttonOffset < seats; buttonOffset++) {
                    int button = Math.floorMod(17 + buttonOffset, seats);
                    for (int seat = 0; seat < seats; seat++) {
                        String role = rotated.get(seat);
                        int position = Math.floorMod(seat - button, seats);
                        roleByPosition.computeIfAbsent(role, ignored -> new HashSet<>()).add(position);
                    }
                }
            }
            for (String role : roster) {
                assertEquals(seats, rolesBySeat.get(role).size(), role + " must visit all physical seats");
                assertEquals(seats, roleByPosition.get(role).size(), role + " must visit every button position");
            }
        }
    }

    @Test void strictLineupLoaderRejectsDuplicateRolesAndUnknownKeys() throws Exception {
        Path source = config("j6-strategy-lineups.yaml");
        String valid = Files.readString(source);
        Path duplicate = Files.writeString(temp.resolve("bad-lineup-duplicate.yaml"),
                valid.replace("[tag, pressure, equity-baseline, equity-stack-aware]",
                        "[tag, tag, equity-baseline, equity-stack-aware]"));
        Path unknown = Files.writeString(temp.resolve("bad-lineup-key.yaml"),
                valid.replace("schemaVersion: 1", "schemaVersion: 1\nextra: true"));
        try {
            assertThrows(IllegalArgumentException.class, () -> TournamentStrategyLineups.load(duplicate));
            assertThrows(IllegalArgumentException.class, () -> TournamentStrategyLineups.load(unknown));
        } finally {
            Files.deleteIfExists(duplicate);
            Files.deleteIfExists(unknown);
        }
    }

    @Test void adversarialShortStackLineupExposesTheConfigurableShoveRole() throws Exception {
        var lineup = TournamentStrategyLineups.load(config("j6-strategy-lineups-shortstack.yaml"));
        assertEquals(8, lineup.shortStackShoveMaxBigBlinds());
        for (int seats : List.of(4, 5, 6)) assertTrue(lineup.forSeats(seats).contains("short-stack-shove"));
        assertEquals(6, lineup.forSeats(6).size());
    }

    @Test void historyAblationRotatesTwoOtherwiseIdenticalEquityPolicies() throws Exception {
        var lineup = TournamentStrategyLineups.load(config("j6-strategy-lineups-shove-ablation.yaml"));
        for (int seats : List.of(4, 5, 6)) {
            assertTrue(lineup.forSeats(seats).contains("equity-history-control"));
            assertTrue(lineup.forSeats(seats).contains("equity-history-aware"));
        }
        var control = TournamentPolicyConfig.load(config("policy-tuning/j6-policy-equity-history-control.yaml"))
                .equityContextWeights();
        var aware = TournamentPolicyConfig.load(config("policy-tuning/j6-policy-equity-stack-aware.yaml"))
                .equityContextWeights();
        assertEquals(control, aware.withShortStackShoveSensitivity(0),
                "The ablation must differ only in public shove-history sensitivity");
        assertEquals(0, control.shortStackShoveSensitivity());
        assertEquals(16, aware.shortStackShoveSensitivity());

        var defaults = TournamentPolicyConfig.load(config("j6-policy-equity.yaml"));
        var variants = new HashMap<String, TournamentPolicyConfig>();
        lineup.equityPolicyFiles().forEach((role, file) -> {
            try { variants.put(role, TournamentPolicyConfig.load(projectPath(file))); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        var trace = new StringWriter();
        TournamentStrategyArena.play(TournamentProfiles.load(config("j6-tournaments-stress.yaml")).get(4),
                41, 2, 1, lineup.forSeats(4), defaults, variants, lineup.shortStackShoveMaxBigBlinds(),
                new PrintWriter(trace));
        assertTrue(trace.toString().contains("id=equity-history-control "));
        assertTrue(trace.toString().contains("id=equity-history-aware "));
    }

    @Test void traceNamesTheRotatedSeatRosterAndLogsOnlyPublicDecisionContext() throws Exception {
        Path profile = config("j6-tournaments.yaml");
        var rules = TournamentProfiles.load(profile).get(4);
        var lineup = TournamentStrategyLineups.load(config("j6-strategy-lineups.yaml"));
        var defaults = TournamentPolicyConfig.load(config("j6-policy-equity.yaml"));
        var variants = Map.of(
                "equity-baseline", TournamentPolicyConfig.load(config("policy-tuning/j6-policy-equity-baseline.yaml")),
                "equity-stack-aware", TournamentPolicyConfig.load(config("policy-tuning/j6-policy-equity-stack-aware.yaml")));
        var trace = new StringWriter();
        TournamentStrategyArena.play(rules, 41, 2, 1, lineup.forSeats(4), defaults, variants,
                lineup.shortStackShoveMaxBigBlinds(), new PrintWriter(trace));
        var lines = trace.toString().lines().toList();
        assertEquals(4, lines.stream().filter(line -> line.startsWith("STRATEGY ")).count());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("STRATEGY seat=0 id=equity-stack-aware ")));
        var decision = lines.stream().filter(line -> line.startsWith("DECISION ")).findFirst().orElseThrow();
        assertTrue(decision.contains("visibleSeatIds=["), decision);
        assertTrue(decision.contains("visibleStacks=["), decision);
        assertTrue(decision.contains("visibleContributions=["), decision);
        assertTrue(decision.contains("foldedSeatIds=["), decision);
        assertTrue(decision.contains("priorAllInActors=["), decision);
        assertTrue(decision.contains("priorAllInEvents=["), decision);
        assertTrue(decision.contains("bigBlind="), decision);
        assertTrue(decision.contains("legalActions="), decision);
        assertFalse(decision.contains("holeCards="), "Decision trace must not reveal private cards");
        assertFalse(decision.contains("dealSeed="), "Decision trace must not reveal a future deal");
    }

    private static Path config(String relative) {
        Path local = Path.of("config").resolve(relative);
        return Files.exists(local) ? local : Path.of("..").resolve(local);
    }

    private static Path projectPath(Path relative) {
        return Files.exists(relative) ? relative : Path.of("..").resolve(relative);
    }

}
