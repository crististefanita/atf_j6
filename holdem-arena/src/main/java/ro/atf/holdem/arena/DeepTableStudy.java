package ro.atf.holdem.arena;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ro.atf.holdem.ai.*;
import ro.atf.holdem.core.*;

/** Duplicate-deal tournaments for studying non-stationary public betting tendencies. */
public final class DeepTableStudy {
    record Schedule(int seedBase, int seedsPerCell) {}
    record Settings(int windowHands, int priorStrength, int minimumEvidence, int minGainPercent) {}
    record Role(String name, List<PhaseStylePolicy.Phase> phases) {}
    record Trial(int stack, String field, int seats, long seed, int rotation) {
        String key() { return stack + "," + field + "," + seats + "," + seed + "," + rotation; }
    }
    record Run(String row, List<String> forecasts) {}

    private final Map<Integer, Tournament.Rules> profiles;
    private final List<Integer> stacks;
    private final List<Role> roles;
    private final Map<String, Schedule> schedules;
    private final Settings settings;
    private final int secondsPerHand;
    private final int handLimit;
    private final DecisionPolicy control;

    private DeepTableStudy(Path config) throws Exception {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        Map<?, ?> root;
        try (var reader = Files.newBufferedReader(config)) {
            root = mapping(new Yaml(new SafeConstructor(options)).load(reader), "schemaVersion", "blindProfiles",
                    "startingStacks", "levelSeconds", "secondsPerHand", "handLimit", "heroSamples", "read",
                    "fit", "validation", "holdout", "roles");
        }
        if (!Integer.valueOf(1).equals(root.get("schemaVersion"))) throw new IllegalArgumentException("Study schema");
        var template = TournamentProfiles.load(config.resolveSibling((String) root.get("blindProfiles")));
        var loaded = new TreeMap<Integer, Tournament.Rules>();
        template.forEach((seats, rules) -> loaded.put(seats, new Tournament.Rules(seats, rules.startingStack(),
                positive(root.get("levelSeconds")), rules.levels())));
        profiles = Map.copyOf(loaded);
        if (!profiles.keySet().equals(Set.of(4, 5, 6))) throw new IllegalArgumentException("Study seats must be 4, 5, 6");
        stacks = ((List<?>) root.get("startingStacks")).stream().map(DeepTableStudy::positive).distinct().toList();
        if (stacks.size() != 2) throw new IllegalArgumentException("Expected two different starting stacks");
        var read = mapping(root.get("read"), "windowHands", "priorStrength", "minimumEvidence", "minGainPercent");
        settings = new Settings(positive(read.get("windowHands")), positive(read.get("priorStrength")),
                positive(read.get("minimumEvidence")), positive(read.get("minGainPercent")));
        secondsPerHand = positive(root.get("secondsPerHand"));
        handLimit = positive(root.get("handLimit"));
        control = new ContextualEquityPolicy(positive(root.get("heroSamples")), 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.TOURNAMENT, ContextualEquityPolicy.ContextWeights.defaults(),
                ContextualEquityPolicy.HardPressure.TOURNAMENT);
        var plans = new LinkedHashMap<String, Schedule>();
        for (String name : List.of("fit", "validation", "holdout")) {
            var row = mapping(root.get(name), "seedBase", "seedsPerCell");
            plans.put(name, new Schedule(positive(row.get("seedBase")), positive(row.get("seedsPerCell"))));
        }
        schedules = Map.copyOf(plans);
        var configured = (Map<?, ?>) root.get("roles");
        var loadedRoles = new ArrayList<Role>();
        for (var entry : configured.entrySet()) {
            String name = (String) entry.getKey();
            if (!name.matches("[a-z]+")) throw new IllegalArgumentException("Invalid role " + name);
            var phases = ((List<?>) entry.getValue()).stream().map(value -> {
                var phase = mapping(value, "firstHand", "style");
                String style = (String) phase.get("style");
                return new PhaseStylePolicy.Phase(positive(phase.get("firstHand")), style, style(style));
            }).toList();
            new PhaseStylePolicy(name, phases); // Validate the complete schedule before any tournament.
            loadedRoles.add(new Role(name, phases));
        }
        if (loadedRoles.size() != 5) throw new IllegalArgumentException("Expected five opponent roles");
        roles = List.copyOf(loadedRoles);
    }

    private static Map<?, ?> mapping(Object value, String... fields) {
        if (value instanceof Map<?, ?> map && map.keySet().equals(Set.of(fields))) return map;
        throw new IllegalArgumentException("Unexpected study keys " + value);
    }

    private static int positive(Object value) {
        if (value instanceof Integer number && number > 0) return number;
        throw new IllegalArgumentException("Expected positive integer");
    }

    private static DecisionPolicy style(String name) {
        DecisionPolicy base = switch (name) {
            case "caller" -> new CallingStationPolicy();
            case "pressure" -> new PressurePolicy();
            case "wide" -> new LooseAggressivePolicy(69, 43, 42, 10)::decideWithSharedPreflopDraw;
            case "selective" -> new StrengthSelectivePolicy(StrengthSelectivePolicy.Style.BALANCED);
            case "cautious" -> new ContextualEquityPolicy(64, 15, 29, 22, 70,
                    ContextualEquityPolicy.GameMode.TOURNAMENT);
            default -> throw new IllegalArgumentException("Unknown table style " + name);
        };
        return observation -> {
            var decision = base.decide(observation);
            if (decision.action() != ActionType.BET || !PublicBettingRead.supportedStreet(observation)) return decision;
            var legal = observation.legalActions().stream().filter(a -> a.type() == ActionType.BET)
                    .findFirst().orElseThrow();
            int amount = Math.min(legal.maxAmount(), Math.max(legal.minAmount(), observation.pot() / 2));
            return new Decision(ActionType.BET, amount, decision.policyVersion(), decision.explanation());
        };
    }

    private List<Trial> trials(Schedule schedule) {
        var result = new ArrayList<Trial>();
        for (int depth = 0; depth < stacks.size(); depth++)
            for (int field = 0; field < 2; field++)
                for (int seats : List.of(4, 5, 6))
                    for (int seed = 0; seed < schedule.seedsPerCell(); seed++)
                        for (int rotation = 0; rotation < seats; rotation++)
                            result.add(new Trial(stacks.get(depth), field == 0 ? "fixed" : "switching", seats,
                                    schedule.seedBase() + 200_000L * depth + 40_000L * field + 1000L * seats + seed, rotation));
        return result;
    }

    private Run play(Trial trial, String arm, double prior, boolean forecast, PrintWriter trace,
                     PublicBettingRead.Regimes regimes) {
        var source = profiles.get(trial.seats());
        var rules = new Tournament.Rules(trial.seats(), trial.stack(), source.levelSeconds(), source.levels());
        var read = new PublicBettingRead(trial.seats(), settings.windowHands(), regimes);
        var opponents = new HashMap<Integer, PhaseStylePolicy>();
        var policies = new ArrayList<DecisionPolicy>();
        var rows = new ArrayList<String>();
        int[] counts = new int[3];
        long[] fingerprint = {0xcbf29ce484222325L};
        DecisionPolicy hero = arm.equals("control") ? control : new ReadBasedBetPolicy(control, read,
                PublicBettingRead.Mode.valueOf(arm.toUpperCase(Locale.ROOT)), prior, settings.priorStrength(),
                settings.minimumEvidence(), settings.minGainPercent());
        for (int seat = 0; seat < trial.seats(); seat++) {
            int roleIndex = Math.floorMod(seat - trial.rotation() - 1, trial.seats());
            if (seat == trial.rotation()) policies.add(hero);
            else {
                var role = roles.get(roleIndex);
                var phases = trial.field().equals("fixed") ? List.of(role.phases().getFirst()) : role.phases();
                var opponent = new PhaseStylePolicy(role.name(), phases);
                opponents.put(seat, opponent);
                policies.add(opponent);
            }
        }
        var measured = policies.stream().<DecisionPolicy>map(policy -> observation -> {
            var decision = policy.decide(observation);
            int seat = observation.physicalHeroSeat();
            fingerprint[0] = (fingerprint[0] ^ observation.handNumber()) * 0x100000001b3L;
            fingerprint[0] = (fingerprint[0] ^ (seat * 64 + observation.street().ordinal() * 8
                    + decision.action().ordinal())) * 0x100000001b3L;
            fingerprint[0] = (fingerprint[0] ^ decision.amount()) * 0x100000001b3L;
            if (seat == trial.rotation()) {
                counts[0]++;
                if (decision.explanation().contains(" publicReadSeat=")) counts[1]++;
            }
            return decision;
        }).toList();
        var observer = new TournamentArena.BettingObserver() {
            @Override public void accepted(HoldemObservation before, ActionType action, int amount) {
                var opponent = opponents.get(before.physicalHeroSeat());
                if (forecast && opponent != null && PublicBettingRead.facingOffer(before)) {
                    int hand = before.handNumber(), seat = before.physicalHeroSeat();
                    var phase = opponent.phase(hand);
                    var cumulative = read.estimate(seat, hand, PublicBettingRead.Mode.CUMULATIVE, prior, settings.priorStrength());
                    var recent = read.estimate(seat, hand, PublicBettingRead.Mode.RECENT, prior, settings.priorStrength());
                    String role = roles.get(Math.floorMod(seat - trial.rotation() - 1, trial.seats())).name();
                    String regimeForecast = regimes == null ? "" : "," + read.estimate(seat, hand,
                            PublicBettingRead.Mode.REGIME, prior, settings.priorStrength()).fold();
                    rows.add(trial.key() + "," + hand + "," + seat + "," + role + "," + phase.name() + "," + phase.firstHand()
                            + "," + action + "," + cumulative.samples() + "," + recent.samples()
                            + "," + prior + "," + cumulative.fold() + "," + recent.fold() + regimeForecast);
                }
                read.accepted(before, action, amount);
            }
            @Override public void settled(int handNumber) { read.settled(handNumber); counts[2] = handNumber; }
        };
        int win = -1;
        try {
            var result = TournamentArena.play(rules, trial.seed(), secondsPerHand, handLimit, measured, 0, trace,
                    Tournament.DealMode.FIXED_PHYSICAL_SEAT, observer);
            win = result.winner() == trial.rotation() ? 1 : 0;
        } catch (IllegalStateException failure) {
            if (!"Tournament did not finish within the hand limit".equals(failure.getMessage())) throw failure;
        }
        return new Run(trial.key() + "," + arm + "," + win + "," + counts[2] + "," + counts[0] + "," + counts[1]
                + "," + Long.toUnsignedString(fingerprint[0], 16), rows);
    }

    public static void main(String[] args) throws Exception {
        var study = new DeepTableStudy(Path.of(args[0]));
        Path output = Path.of(args[1]);
        String phase = args[2];
        double prior = phase.equals("fit") ? .5 : Double.parseDouble(Files.readString(output.resolve("prior.txt")).strip());
        if (!(prior > 0 && prior < 1)) throw new IllegalArgumentException("Fold prior must be inside (0, 1)");
        boolean regimePhase = phase.equals("regime-validation") || phase.equals("regime-holdout");
        boolean regimeTrace = phase.equals("trace") && Arrays.asList(args).contains("regime");
        var regimes = regimePhase || regimeTrace ? readRegimes(output.resolve("regime-fit.yaml"), prior) : null;
        if (phase.equals("trace")) {
            var trace = new PrintWriter(System.out, true);
            for (int i = 3; i < args.length; i += 6) {
                var trial = new Trial(Integer.parseInt(args[i]), args[i + 1], Integer.parseInt(args[i + 2]),
                        Long.parseLong(args[i + 3]), Integer.parseInt(args[i + 4]));
                study.play(trial, args[i + 5], prior, false, trace, regimes);
            }
            return;
        }
        boolean measure = !phase.equals("holdout") && !phase.equals("regime-holdout");
        var arms = measure ? List.of("control") : regimePhase ? List.of("control", "regime")
                : List.of("control", "fixed", "cumulative", "recent");
        var schedule = regimePhase ? new Schedule(Integer.parseInt(args[3]), Integer.parseInt(args[4]))
                : study.schedules.get(phase);
        Files.createDirectories(output);
        try (var pool = Executors.newFixedThreadPool(4);
             var runs = new PrintWriter(Files.newBufferedWriter(output.resolve(phase + "-runs.csv")));
             var forecasts = new PrintWriter(Files.newBufferedWriter(output.resolve(phase + "-forecasts.csv")))) {
            runs.println("stack,field,seats,seed,rotation,arm,win,hands,heroDecisions,betsChanged,actionHash");
            forecasts.println("stack,field,seats,seed,rotation,hand,seat,role,style,phaseStart,action,cumulativeN,recentN,fixed,cumulative,recent"
                    + (regimes == null ? "" : ",regime"));
            var tasks = new ArrayList<Callable<Run>>();
            for (var trial : study.trials(schedule))
                for (var arm : arms) tasks.add(() -> study.play(trial, arm, prior, measure, null, regimes));
            for (var task : pool.invokeAll(tasks)) {
                var result = task.get();
                runs.println(result.row());
                result.forecasts().forEach(forecasts::println);
            }
            if (runs.checkError() || forecasts.checkError()) throw new IllegalStateException("Study output failed");
        }
    }

    private static PublicBettingRead.Regimes readRegimes(Path path, double prior) throws Exception {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var reader = Files.newBufferedReader(path)) {
            var row = mapping(new Yaml(new SafeConstructor(options)).load(reader), "low", "high", "hazard", "fitForecastSha256");
            return new PublicBettingRead.Regimes(((Number) row.get("low")).doubleValue(),
                    ((Number) row.get("high")).doubleValue(), prior, ((Number) row.get("hazard")).doubleValue());
        }
    }
}
