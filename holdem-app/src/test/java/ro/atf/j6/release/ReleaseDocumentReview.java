package ro.atf.j6.release;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Compares explicitly identified Markdown claims without assigning authority or release status. */
final class ReleaseDocumentReview {
    private static final Pattern CLAIM = Pattern.compile(
            "`([^`\\r\\n]+)`[ \\t]+<!--\\s*rag:(contract|observed)\\s+([a-z][a-z0-9.-]*)\\s*-->");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern CLOSED = Pattern.compile("(?iu)^(?:închis|rezolvat|closed|resolved)\\b");
    private static final List<String> CONTRACT_DIRECTORIES = List.of(
            "docs/requirements/", "docs/architecture/", "docs/testing/", "docs/release/");
    private static final List<String> CONTRACT_GUIDES = List.of("README.md", "holdem-android/README.md", "release/README.md");

    private ReleaseDocumentReview() { }

    static List<Claim> readClaims(String path, String sourceDigest, String content, String feedbackState) {
        if (!path.endsWith(".md")) return List.of();
        boolean feedback = path.startsWith("docs/feedback/") && !path.equals("docs/feedback/README.md");
        boolean contract = CONTRACT_GUIDES.contains(path) || CONTRACT_DIRECTORIES.stream().anyMatch(path::startsWith);
        if ((!feedback && !contract) || (feedback && CLOSED.matcher(feedbackState).find())) return List.of();

        List<Claim> claims = new ArrayList<>();
        String fence = "";
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            var delimiter = FENCE.matcher(lines[i]);
            if (delimiter.matches()) {
                String marker = delimiter.group(1);
                if (fence.isEmpty()) {
                    fence = marker;
                } else if (marker.startsWith(fence) && delimiter.group(2).isBlank()) {
                    fence = "";
                }
                continue;
            }
            if (!fence.isEmpty()) continue;
            var matcher = CLAIM.matcher(lines[i]);
            while (matcher.find()) {
                if (!matcher.group(2).equals(feedback ? "observed" : "contract")) continue;
                String value = normalize(matcher.group(1));
                if (!value.isEmpty()) claims.add(new Claim(matcher.group(3), value, feedback,
                        path, i + 1, sourceDigest, lines[i].strip()));
            }
        }
        return claims;
    }

    static void appendFindings(ArrayNode findings, List<Claim> claims, String commit, UnaryOperator<String> redact) {
        Map<String, List<Claim>> byKey = new TreeMap<>();
        for (Claim claim : claims) byKey.computeIfAbsent(claim.key(), key -> new ArrayList<>()).add(claim);

        byKey.forEach((key, entries) -> {
            List<Claim> contracts = entries.stream().filter(claim -> !claim.observed()).toList();
            if (contracts.isEmpty()) return;
            Claim first = contracts.getFirst();
            boolean conflicting = contracts.stream().anyMatch(claim -> !claim.value().equals(first.value()));
            if (conflicting) {
                appendFinding(findings, "DOCUMENT_CONTRADICTION", key, contracts, commit, redact);
                return;
            }
            entries.stream().filter(Claim::observed).filter(claim -> !claim.value().equals(first.value()))
                    .forEach(claim -> appendFinding(findings, "STALE_FEEDBACK", key, List.of(claim, first), commit, redact));
        });
    }

    private static void appendFinding(ArrayNode findings, String kind, String key, List<Claim> claims,
                                      String commit, UnaryOperator<String> redact) {
        var finding = findings.addObject();
        finding.put("kind", kind);
        finding.put("key", key);
        var citations = finding.putArray("citations");
        for (Claim claim : claims) {
            var citation = citations.addObject();
            citation.put("role", claim.observed() ? "observed" : "contract");
            citation.put("value", redact.apply(claim.value()));
            citation.put("path", claim.path());
            citation.put("startLine", claim.line());
            citation.put("endLine", claim.line());
            citation.put("sourceSha256", claim.sourceDigest());
            citation.put("indexedCommit", commit);
            citation.put("excerpt", redact.apply(claim.excerpt()));
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    record Claim(String key, String value, boolean observed, String path, int line, String sourceDigest, String excerpt) { }
}
