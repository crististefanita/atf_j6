package ro.atf.j6.release;

import atf.reporting.contract.AtfAssertion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Publishes the offline Hold'em achievement qualification with the project's Extent renderer. */
public final class PublishAchievementCalibrationExtent {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PublishAchievementCalibrationExtent() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected <catalog.yaml> <cohorts> <results.json> <extent.html>");
        var catalogPath = Path.of(args[0]);
        var cohortsPath = Path.of(args[1]);
        var resultsPath = Path.of(args[2]);
        var results = JSON.readTree(resultsPath.toFile());
        var catalog = JSON.readTree(catalogPath.toFile());
        var checks = new ArrayList<AtfAssertion>();
        String catalogHash = sha256(catalogPath);
        String declaredHash = results.required("catalogSha256").asText();
        checks.add(new AtfAssertion("Catalog SHA-256", catalogHash, declaredHash, catalogHash.equals(declaredHash)));
        checks.add(new AtfAssertion("Results identity", "Raw results document is available with a SHA-256",
                resultsPath.getFileName() + " " + sha256(resultsPath), true));

        var cohorts = new HashMap<String, Map<String, JsonNode>>();
        var pairs = new HashSet<String>();
        for (var cohort : results.required("cohorts")) {
            String stage = cohort.required("stage").asText();
            String difficulty = cohort.required("difficulty").asText();
            String key = stage + "-" + difficulty;
            if (!key.matches("(calibration|holdout|product-budget)-(NORMAL|HARD)") || !pairs.add(key))
                throw new IllegalArgumentException("Unexpected cohort " + key);
            String name = cohort.required("file").asText();
            if (!name.equals(key + ".csv")) throw new IllegalArgumentException("Unexpected cohort file " + name);
            String actualHash = sha256(cohortsPath.resolve(name));
            String expectedHash = cohort.required("sha256").asText();
            checks.add(new AtfAssertion(key + " CSV SHA-256", expectedHash, actualHash, expectedHash.equals(actualHash)));
            int count = cohort.required("count").asInt();
            int completed = cohort.required("completed").asInt();
            int hands = cohort.required("hands").asInt();
            checks.add(new AtfAssertion(key + " completed tournaments", "All configured attempts included; censored runs disclosed",
                    completed + "/" + count + " completed, " + (count - completed) + " censored, " + hands + " hands, first seed "
                            + cohort.required("firstSeed").asLong() + ", " + cohort.required("samples").asInt() + " equity samples",
                    completed >= 0 && completed <= count && hands >= count));
            cohorts.put(key, byId(cohort.required("measurements")));
        }
        if (cohorts.size() != 6) throw new IllegalArgumentException("Six cohorts are required");

        var rules = new HashMap<String, JsonNode>();
        for (var tier : catalog.required("tiers")) rules.putAll(byId(tier));
        rules.putAll(byId(catalog.required("retired")));
        var verdicts = byId(results.required("verdicts"));
        var measured = byId(results.required("rows"));
        if (!rules.keySet().equals(verdicts.keySet()) || !rules.keySet().equals(measured.keySet())
                || cohorts.values().stream().anyMatch(cohort -> !rules.keySet().equals(cohort.keySet())))
            throw new IllegalArgumentException("Objective inventory differs across catalog, verdicts and measurements");

        for (var row : results.required("rows")) {
            String id = row.required("id").asText();
            int target = row.required("target").asInt();
            String tier = row.required("tier").asText();
            String metric = row.required("metric").asText();
            var decision = verdicts.get(id);
            String verdict = decision.required("verdict").asText();
            boolean matchesCatalog = target == rules.get(id).required("target").asInt()
                    && metric.equals(rules.get(id).required("metric").asText());
            boolean retired = rules.get(id).has("reason");
            boolean validVerdict = retired ? verdict.equals("RETRAS") : Set.of("CONFIRMAT", "RAR").contains(verdict);
            checks.add(new AtfAssertion(id + " · " + verdict, tier + " · " + metric + " · target " + target,
                    decision.required("reason").asText(), matchesCatalog && validVerdict));
            for (String stage : new String[]{"calibration", "holdout", "product-budget"}) {
                var normal = cohorts.get(stage + "-NORMAL").get(id);
                var hard = cohorts.get(stage + "-HARD").get(id);
                checks.add(new AtfAssertion(id + " · " + stage,
                        "Normal and Hard evidence at 4–6 seats. Events, median hand and reached windows",
                        "N " + compact(normal) + " | H " + compact(hard),
                        validMeasurement(normal) && validMeasurement(hard)));
            }
        }
        ReleaseAuditExtent.write(Path.of(args[3]), PublishAchievementCalibrationExtent.class.getSimpleName(),
                "Hold’em achievement calibration and independent confirmation", checks);
        if (checks.stream().anyMatch(check -> !check.passed())) throw new IllegalStateException("Achievement evidence audit failed");
    }

    private static Map<String, JsonNode> byId(JsonNode rows) {
        if (!rows.isArray()) throw new IllegalArgumentException("Expected an objective array");
        var result = new HashMap<String, JsonNode>();
        for (var row : rows) {
            String id = row.required("id").asText();
            if (result.put(id, row) != null) throw new IllegalArgumentException("Duplicate objective " + id);
        }
        return result;
    }

    private static boolean validMeasurement(JsonNode m) {
        int events = m.required("creditEvents").asInt();
        int opportunities = m.required("opportunities").asInt();
        if (events < 0 || events > opportunities || !m.required("bySeats").isObject()) return false;
        for (var window : m.required("windows")) {
            int reached = window.required("reached").asInt();
            if (reached < 0 || reached > window.required("count").asInt()) return false;
        }
        return true;
    }

    private static String compact(JsonNode m) {
        return "events " + m.required("creditEvents").asInt() + "/" + m.required("opportunities").asInt()
                + ", first " + hand(m.required("medianFirstOpportunityHand")) + ", unlock "
                + hand(m.required("medianUnlockHand")) + ", 3t " + window(m, "3") + ", 6t " + window(m, "6");
    }

    private static String window(JsonNode m, String size) {
        var window = m.required("windows").required(size);
        return window.required("reached").asInt() + "/" + window.required("count").asInt();
    }

    private static String hand(JsonNode node) { return node.isNull() ? "—" : node.asText(); }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
