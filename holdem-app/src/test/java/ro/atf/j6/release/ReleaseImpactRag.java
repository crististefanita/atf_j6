package ro.atf.j6.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline, deterministic retrieval for release impact review; it never evaluates release gates. */
public final class ReleaseImpactRag {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{3,}");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FEEDBACK_STATE = Pattern.compile("(?im)^\\*\\*Stare:\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern SECRET = Pattern.compile("(?i)(bearer\\s+|(?:password|token|secret|credential)\\s*[:=]\\s*)([^\\s,;]+)|\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b");
    private static final Pattern LOCAL_PATH = Pattern.compile("(?i)(?<![\\w])(?:[A-Z]:[\\\\/]+(?:work|users|tools|install|program files)(?:[\\\\/]+[^\\s\\\"'<>|,;]+)*)");
    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "from", "this", "that", "are", "was", "were",
            "into", "when", "then", "given", "shall", "must", "can", "has", "have", "not", "only", "per", "care", "mai", "este",
            "sunt", "din", "prin", "pentru", "fara", "toate", "toata", "acest", "aceasta", "aceste", "unei", "unui",
            "despre", "daca", "unde", "sau", "vor", "poate", "trebuie", "fiind", "class", "interface", "package", "public",
            "private", "protected", "static", "final", "void", "string", "return", "import", "null", "true", "false",
            "override", "extends", "implements", "int", "long", "boolean", "new", "test", "source", "path", "signal");
    private static final int MAX_DOCUMENT_BYTES = 1_000_000;
    private static final int MAX_HITS = 15;
    private static final int MAX_TEST_SUGGESTIONS = 8;

    private ReleaseImpactRag() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
                "Expected: <project-root> <base-version> <base-commit> <candidate-commit> <draft-version> <output.json>");
        Path output = Path.of(args[5]);
        ObjectNode result = review(Path.of(args[0]), args[1], args[2], args[3], args[4], output);
        System.out.printf("Release impact context: %s citations=%d suggestedSuites=%d output=%s%n",
                result.path("contextState").asText(), result.path("retrievedSources").size(),
                result.path("suggestedTestSuites").size(), output.toAbsolutePath());
    }

    public static ObjectNode review(Path sourceRoot, String baseCommit, String candidateCommit,
                                    String candidateVersion, Path output) throws Exception {
        return review(sourceRoot, "", baseCommit, candidateCommit, candidateVersion, output);
    }

    public static ObjectNode review(Path sourceRoot, String baseVersion, String baseCommit, String candidateCommit,
                                    String candidateVersion, Path output) throws Exception {
        Path root = sourceRoot.toRealPath();
        Path target = output.toAbsolutePath().normalize();
        if (target.startsWith(root)) throw new IllegalArgumentException("Impact context must be written outside the source tree.");
        String head = git(root, "rev-parse", "HEAD").trim();
        if (!head.equals(resolveCommit(root, candidateCommit))) throw new IllegalArgumentException("Candidate commit must be the checked-out HEAD.");
        if (!git(root, "status", "--porcelain", "--untracked-files=all").isBlank()) {
            throw new IllegalStateException("Impact context requires a clean candidate worktree.");
        }
        String base = resolveCommit(root, baseCommit);
        JsonNode index = JSON.readTree(root.resolve("release/index.json").toFile());
        if (!candidateVersion.equals(index.path("currentVersion").asText())
                || !"DRAFT".equals(index.path("state").asText())) {
            throw new IllegalArgumentException("Impact context is advisory for the indexed DRAFT version only.");
        }

        List<String> changedFiles = changedFiles(root, base, head);
        String diff = git(root, "diff", "--no-ext-diff", "--no-color", "--unified=0", base + "..." + head, "--");
        Map<String, Integer> query = new HashMap<>();
        changedFiles.forEach(path -> addTokens(query, path, 3));
        Set<String> changeSignals = new HashSet<>();
        for (String line : diff.split("\\R")) if (line.startsWith("+") && !line.startsWith("+++")) {
            addTokens(query, line.substring(1), 1);
            changeSignals.addAll(tokens(line.substring(1)));
        }

        List<SourceChunk> corpus = corpus(root);
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (SourceChunk chunk : corpus) for (String term : chunk.terms()) documentFrequency.merge(term, 1, Integer::sum);
        List<RankedChunk> hits = corpus.stream().map(chunk -> rank(chunk, query, documentFrequency, corpus.size()))
                .filter(hit -> hit.score() > 0).sorted(Comparator.comparingInt(RankedChunk::score).reversed()
                        .thenComparing(hit -> hit.chunk().path()).thenComparing(hit -> hit.chunk().heading()))
                .limit(MAX_HITS).toList();

        JsonNode suites = JSON.readTree(root.resolve("config/release-test-suites.json").toFile());
        List<RankedSuite> recommended = rankSuites(suites, query).stream().limit(MAX_TEST_SUGGESTIONS).toList();
        boolean citedChangeSignal = hits.stream().anyMatch(hit -> hit.chunk().terms().stream().anyMatch(changeSignals::contains));
        boolean enoughEvidence = !changedFiles.isEmpty() && citedChangeSignal && !recommended.isEmpty();
        String contextState = enoughEvidence ? "CONTEXT_READY" : "NEEDS_EVIDENCE";
        ObjectNode result = JSON.createObjectNode();
        result.put("schemaVersion", 1);
        result.put("kind", "offline-release-impact-context");
        result.put("contextState", contextState);
        result.put("advisoryOnly", true);
        result.put("gateEffect", "NONE");
        result.put("canChangeReleaseIndex", false);
        result.put("canDeclareTestPass", false);
        result.put("candidateVersion", candidateVersion);
        if (!baseVersion.isBlank()) result.put("baseReleaseVersion", baseVersion);
        if (index.hasNonNull("changeClass")) result.put("declaredChangeClass", index.path("changeClass").asText());
        if (index.hasNonNull("changeRationale")) result.put("changeRationale", redact(index.path("changeRationale").asText()));
        result.put("candidateCommit", head);
        result.put("baseReleaseCommit", base);
        result.put("testReceiptState", "NOT_EVALUATED");
        result.put("evidenceNote", enoughEvidence ? "Changed-text signals have versioned source and suite citations."
                : "No sufficiently specific source and suite mapping was retrieved; human review is required.");
        ArrayNode files = result.putArray("changedFiles");
        changedFiles.forEach(files::add);

        ArrayNode citations = result.putArray("retrievedSources");
        hits.forEach(hit -> {
            SourceChunk chunk = hit.chunk();
            ObjectNode citation = citations.addObject();
            citation.put("rank", citations.size());
            citation.put("score", hit.score());
            citation.put("path", chunk.path());
            citation.put("heading", chunk.heading());
            citation.put("startLine", chunk.startLine());
            citation.put("endLine", chunk.endLine());
            citation.put("sourceSha256", chunk.sourceSha256());
            citation.put("chunkSha256", chunk.chunkSha256());
            citation.put("indexedCommit", head);
            citation.put("version", candidateVersion);
            if (!chunk.feedbackState().isBlank()) citation.put("feedbackState", chunk.feedbackState());
            citation.put("excerpt", redact(chunk.text()));
        });

        ArrayNode testSuggestions = result.putArray("suggestedTestSuites");
        String suiteInventoryDigest = digest(read(root.resolve("config/release-test-suites.json")));
        recommended.forEach(suite -> {
            ObjectNode row = testSuggestions.addObject();
            row.put("module", suite.module());
            row.put("testClass", suite.testClass());
            row.put("relevanceScore", suite.score());
            row.put("inventoryPath", "config/release-test-suites.json");
            row.put("inventorySha256", suiteInventoryDigest);
            row.put("indexedCommit", head);
        });
        result.put("corpusSha256", corpusDigest(corpus));
        Files.createDirectories(target.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), result);
        return result;
    }

    private static List<SourceChunk> corpus(Path root) throws Exception {
        var files = new ArrayList<Path>();
        for (String directory : List.of("docs/feedback", "docs/requirements", "docs/testing", "docs/release", "docs/architecture")) {
            Path path = root.resolve(directory);
            if (Files.isDirectory(path)) try (var walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".md")).forEach(files::add);
            }
        }
        for (String path : List.of("README.md", "release/README.md", "release/index.json", "config/release-test-suites.json",
                "library-provenance.json", "holdem-android/ui-library/provenance.json")) {
            if (Files.isRegularFile(root.resolve(path))) files.add(root.resolve(path));
        }
        Path releaseRoot = root.resolve("release");
        if (Files.isDirectory(releaseRoot)) try (var walk = Files.walk(releaseRoot, 3)) {
            walk.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> path.matches("release/\\d+\\.\\d+\\.\\d+/(README|release-notes)\\.md")
                            || path.matches("release/\\d+\\.\\d+\\.\\d+/reports/README\\.md"))
                    .map(root::resolve).forEach(files::add);
        }
        List<Path> uniqueFiles = files.stream().distinct().sorted().toList();
        List<SourceChunk> chunks = new ArrayList<>();
        for (Path file : uniqueFiles) {
            byte[] bytes = read(file);
            if (bytes.length > MAX_DOCUMENT_BYTES) continue;
            String content = new String(bytes, StandardCharsets.UTF_8);
            String relative = root.relativize(file).toString().replace('\\', '/');
            chunks.addAll(split(relative, digest(bytes), content));
        }
        return List.copyOf(chunks);
    }

    private static List<SourceChunk> split(String path, String fileDigest, String content) throws Exception {
        String[] lines = content.split("\\R", -1);
        List<SourceChunk> chunks = new ArrayList<>();
        String heading = "Document";
        String feedbackState = feedbackState(content);
        int start = 1;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = HEADING.matcher(lines[i]);
            if (matcher.matches()) {
                addChunk(chunks, path, fileDigest, heading, start, i, text, feedbackState);
                heading = matcher.group(2).strip();
                start = i + 1;
                text.setLength(0);
            }
            text.append(lines[i]).append('\n');
            if (text.length() >= 16_000) {
                addChunk(chunks, path, fileDigest, heading, start, i + 1, text, feedbackState);
                start = i + 2;
                text.setLength(0);
            }
        }
        addChunk(chunks, path, fileDigest, heading, start, lines.length, text, feedbackState);
        return chunks;
    }

    private static void addChunk(List<SourceChunk> chunks, String path, String fileDigest, String heading,
                                 int start, int end, StringBuilder text, String state) throws Exception {
        String value = text.toString().strip();
        if (value.isBlank()) return;
        chunks.add(new SourceChunk(path, heading, Math.max(1, start), Math.max(start, end), fileDigest,
                digest(value.getBytes(StandardCharsets.UTF_8)), value, state, tokens(value)));
    }

    private static RankedChunk rank(SourceChunk chunk, Map<String, Integer> query,
                                    Map<String, Integer> documentFrequency, int corpusSize) {
        int score = 0;
        for (String term : chunk.terms()) {
            int weight = query.getOrDefault(term, 0);
            if (weight == 0) continue;
            int inverseFrequency = 1 + (int) Math.floor(Math.log((corpusSize + 1.0) / (documentFrequency.getOrDefault(term, 1) + 1.0)));
            score += weight * Math.max(1, inverseFrequency);
        }
        return new RankedChunk(chunk, score);
    }

    private static List<RankedSuite> rankSuites(JsonNode inventory, Map<String, Integer> query) {
        List<RankedSuite> rows = new ArrayList<>();
        for (String scope : List.of("maven", "android")) for (JsonNode suite : inventory.path(scope)) {
            String module = suite.path("module").asText();
            String testClass = suite.path("testClass").asText();
            Map<String, Integer> terms = new HashMap<>();
            addTokens(terms, module + " " + testClass, 1);
            int score = terms.entrySet().stream().mapToInt(entry -> entry.getValue() * query.getOrDefault(entry.getKey(), 0)).sum();
            if (score > 0) rows.add(new RankedSuite(module, testClass, score));
        }
        rows.sort(Comparator.comparingInt(RankedSuite::score).reversed().thenComparing(RankedSuite::module).thenComparing(RankedSuite::testClass));
        return List.copyOf(rows);
    }

    private static List<String> changedFiles(Path root, String base, String head) throws Exception {
        String output = git(root, "diff", "--name-only", "--diff-filter=ACMR", base + "..." + head, "--");
        return output.lines().map(String::strip).filter(path -> !path.isBlank()).distinct().sorted().toList();
    }

    private static String feedbackState(String text) {
        Matcher matcher = FEEDBACK_STATE.matcher(text);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static String redact(String value) {
        String withoutSecrets = SECRET.matcher(value).replaceAll("$1[REDACTED]");
        return LOCAL_PATH.matcher(withoutSecrets).replaceAll("[LOCAL_PATH_REDACTED]");
    }

    private static void addTokens(Map<String, Integer> target, String value, int weight) {
        for (String term : tokens(value)) target.merge(term, weight, Integer::sum);
    }

    private static Set<String> tokens(String value) {
        String splitCamel = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        String normalized = Normalizer.normalize(splitCamel, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN.matcher(normalized);
        Set<String> result = new HashSet<>();
        while (matcher.find()) if (!STOP_WORDS.contains(matcher.group())) result.add(matcher.group());
        return Set.copyOf(result);
    }

    private static String corpusDigest(List<SourceChunk> chunks) throws Exception {
        var values = new LinkedHashMap<String, String>();
        chunks.stream().map(SourceChunk::path).distinct().sorted().forEach(path -> chunks.stream()
                .filter(chunk -> chunk.path().equals(path)).findFirst().ifPresent(chunk -> values.put(path, chunk.sourceSha256())));
        String canonical = values.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue()).reduce("", (a, b) -> a + b + "\n");
        return digest(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveCommit(Path root, String reference) throws Exception {
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("A source commit is required for every citation.");
        return git(root, "rev-parse", "--verify", reference + "^{commit}").trim();
    }

    private static String git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exit = process.waitFor();
        String result = output.toString(StandardCharsets.UTF_8);
        if (exit != 0) throw new IOException("Git command failed with exit " + exit + ": " + String.join(" ", arguments));
        return result;
    }

    private static byte[] read(Path path) throws IOException { return Files.readAllBytes(path); }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record SourceChunk(String path, String heading, int startLine, int endLine, String sourceSha256,
                               String chunkSha256, String text, String feedbackState, Set<String> terms) { }
    private record RankedChunk(SourceChunk chunk, int score) { }
    private record RankedSuite(String module, String testClass, int score) { }
}
