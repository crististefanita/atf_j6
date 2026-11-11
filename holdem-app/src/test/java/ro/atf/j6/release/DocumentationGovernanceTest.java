package ro.atf.j6.release;

import atf.reporting.contract.AtfAssertion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationGovernanceTest {
    private static final Pattern SECTION = Pattern.compile("(?m)^## (.+)$");
    private static final Pattern OWNER_SECTION = Pattern.compile(
            "(?ms)^## Cerințe și documentație[ \\t]*\\R(.*?)(?=^## |\\z)");
    private static final Pattern LINK_BULLET = Pattern.compile("- \\[[^\\[\\]]+]\\(([^()\\s]+)\\)");

    @Test
    void activeReadmesLeadWithOrientationAndPointToTheirOwners() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("release/index.json"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("J6 release catalog not found");
        String version = new ObjectMapper().readTree(root.resolve("release/index.json").toFile())
                .required("currentVersion").asText();
        var checks = new ArrayList<AtfAssertion>();
        for (String path : List.of("README.md", "docs/feedback/README.md", "docs/requirements/README.md",
                "holdem-android/README.md", "lib/README.md", "release/README.md",
                "release/" + version + "/README.md", "release/" + version + "/reports/README.md")) {
            Path document = root.resolve(path);
            String content = Files.isRegularFile(document) ? Files.readString(document) : "";
            String first = firstSection(content);
            checks.add(new AtfAssertion(path + " opens with its scope", "Pe scurt", first, "Pe scurt".equals(first)));
        }
        String main = Files.readString(root.resolve("README.md"));
        List<String> links = ownerLinkTargets(main);
        for (String owner : List.of("docs/requirements/README.md", "docs/architecture/ARCHITECTURE.md",
                "docs/testing/TEST_STRATEGY.md", "docs/release/ATF_Release.md", "docs/feedback/README.md",
                "release/README.md")) {
            int occurrences = Collections.frequency(links, owner);
            boolean exists = Files.isRegularFile(root.resolve(owner));
            checks.add(new AtfAssertion("README documentation links to " + owner, "one link to an existing guide",
                    occurrences + " link(s), target exists: " + exists, occurrences == 1 && exists));
        }
        Path reports = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(reports);
        ReleaseAuditExtent.write(reports.resolve("J6DocumentationGovernance-" + System.currentTimeMillis() + ".html"),
                "J6DocumentationGovernance", "Active README orientation and owner links", checks);
        assertTrue(checks.stream().allMatch(AtfAssertion::passed), () -> checks.toString());
    }

    @Test
    void ownerLinksMustBeStandaloneAndBelongToOneDocumentationSection() {
        String guide = "docs/testing/TEST_STRATEGY.md";
        String link = "- [Strategie](" + guide + ")";
        String section = "## Cerințe și documentație\n\n";
        assertEquals(List.of(guide), ownerLinkTargets(section + link + "\n## Jocul\n" + link));
        assertEquals(List.of(), ownerLinkTargets("## Pe scurt\n" + link + "\n" + section + "Consultați " + link));
        assertEquals(List.of(), ownerLinkTargets(section + "- [Alt ghid](other.md) și " + link.substring(2)));
        assertEquals(List.of(guide, guide), ownerLinkTargets(section + link + "\n" + link));
        assertEquals(List.of(), ownerLinkTargets(section + link + "\n" + section + link));
    }

    private static List<String> ownerLinkTargets(String markdown) {
        var section = OWNER_SECTION.matcher(markdown);
        if (!section.find()) return List.of();
        String body = section.group(1);
        if (section.find()) return List.of();
        return body.lines().map(String::strip).map(LINK_BULLET::matcher).filter(Matcher::matches)
                .map(link -> link.group(1)).toList();
    }

    private static String firstSection(String markdown) {
        var sections = SECTION.matcher(markdown);
        return sections.find() ? sections.group(1).strip() : "missing section";
    }
}
