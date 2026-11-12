package ro.atf.j6.release;

import atf.reporting.contract.AtfAssertion;
import atf.testkit.AtfIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfIntegrationTest
class TestInventoryGovernanceTest {
    private static final String KIT = "atf.testkit.";
    private static final Map<String, String> LEVELS = Map.of("AtfUnitTest", "unit", "AtfComponentTest", "component",
            "AtfIntegrationTest", "integration", "AtfE2eTest", "e2e");
    private static final Set<String> MAVEN_LEVELS = Set.of("unit", "component", "integration");
    private static final Set<String> TESTS = Set.of("Test", "ParameterizedTest", "RepeatedTest",
            "TestFactory", "TestTemplate");

    @Test
    void everyExecutableJvmSuiteBelongsToOneInventoryAndEveryMavenSuiteHasOneLevel() throws Exception {
        Path root = DocumentationGovernanceTest.projectRoot();
        var inventory = new ObjectMapper().readTree(root.resolve("config/release-test-suites.json").toFile());
        assertEquals(1, inventory.required("schemaVersion").asInt());
        var maven = new ArrayList<Suite>();
        try (var modules = Files.list(root)) {
            for (Path module : modules.filter(path -> Files.isRegularFile(path.resolve("pom.xml"))).sorted().toList()) {
                maven.addAll(inspect(module.getFileName().toString(), module.resolve("src/test/java")));
            }
        }
        var android = inspect("holdem-android", root.resolve("holdem-android/app/src/test/java"));
        var checks = new ArrayList<AtfAssertion>();
        var expectedLevels = new HashMap<String, String>();
        for (String platform : List.of("maven", "android")) {
            var declared = new ArrayList<String>();
            for (var entry : inventory.required(platform)) {
                String identity = entry.required("module").asText() + "/" + entry.required("testClass").asText();
                String level = entry.path("level").asText("component");
                declared.add(identity);
                expectedLevels.put(identity, level);
            }
            var suites = platform.equals("maven") ? maven : android;
            checks.add(inventoryCheck(platform, suites, declared));
        }
        for (var suite : maven) {
            String expected = expectedLevels.getOrDefault(suite.identity(), "declared JVM level");
            var actual = suite.levels().stream().map(LEVELS::get).toList();
            checks.add(new AtfAssertion(suite.identity(), expected, actual.toString(),
                    MAVEN_LEVELS.contains(expected) && actual.equals(List.of(expected))));
        }
        Path reports = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(reports);
        ReleaseAuditExtent.write(reports.resolve("J6TestInventoryGovernance-" + System.currentTimeMillis() + ".html"),
                "J6TestInventoryGovernance", "Executable JVM suite inventory and canonical Maven test levels", checks);
        assertTrue(checks.stream().allMatch(AtfAssertion::passed), () -> checks.stream()
                .filter(check -> !check.passed()).toList().toString());
    }

    @Test
    void sourceInspectionIgnoresCommentsAndCountsNestedTestsUnderTheirOwningSuite(@TempDir Path sources) throws Exception {
        Files.writeString(sources.resolve("Fixture.java"), """
                package fixture;
                import atf.testkit.AtfComponentTest;
                import org.junit.jupiter.api.Test;
                // @AtfUnitTest is only a comment.
                @AtfComponentTest
                class Fixture {
                    @org.junit.jupiter.api.Nested class NestedFixture {
                        @Test void verifiesSomething() {}
                    }
                }
                class Helper {
                    String text = "@Test";
                }
                """);
        assertEquals(List.of(new Suite("sample", "fixture.Fixture", List.of("AtfComponentTest"))),
                inspect("sample", sources));
    }

    @Test
    void rejectsDriftAndDoesNotAcceptAnUnrelatedAnnotationWithTheSameName(@TempDir Path sources) throws Exception {
        Files.writeString(sources.resolve("Drift.java"), """
                package fixture;
                import other.AtfComponentTest;
                import org.junit.jupiter.api.Test;
                @AtfComponentTest
                class Drift { @Test void observed() {} }
                @atf.testkit.AtfUnitTest @atf.testkit.AtfComponentTest
                class Ambiguous { @Test void observed() {} }
                """);
        var suites = inspect("sample", sources);
        assertEquals(List.of(), suites.get(0).levels());
        assertEquals(List.of("AtfUnitTest", "AtfComponentTest"), suites.get(1).levels());
        assertFalse(inventoryCheck("sample", suites, List.of("sample/fixture.Drift", "sample/fixture.Drift")).passed());
        assertTrue(inventoryCheck("sample", suites, List.of("sample/fixture.Drift", "sample/fixture.Ambiguous")).passed());
    }

    private static AtfAssertion inventoryCheck(String platform, List<Suite> suites, List<String> declared) {
        var discovered = suites.stream().map(Suite::identity).sorted().toList();
        var configured = declared.stream().sorted().toList();
        return new AtfAssertion(platform + " suite inventory matches source", discovered.toString(),
                configured.toString(), discovered.equals(configured));
    }

    private static List<Suite> inspect(String module, Path sources) throws Exception {
        if (!Files.isDirectory(sources)) return List.of();
        List<Path> java;
        try (var tree = Files.walk(sources)) {
            java = tree.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        if (java.isEmpty()) return List.of();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var suites = new ArrayList<Suite>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null,
                    files.getJavaFileObjectsFromPaths(java));
            for (var unit : task.parse()) {
                var imports = unit.getImports().stream().map(line -> line.getQualifiedIdentifier().toString()).toList();
                for (var declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type
                            && !type.getModifiers().getFlags().contains(Modifier.ABSTRACT) && executable(type)) {
                        var levels = type.getModifiers().getAnnotations().stream()
                                .map(annotation -> annotation.getAnnotationType().toString())
                                .filter(name -> name.startsWith(KIT) || imports.contains(KIT + name)
                                        || imports.contains(KIT + "*"))
                                .map(TestInventoryGovernanceTest::simpleName).filter(LEVELS::containsKey).toList();
                        String packagePrefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
                        suites.add(new Suite(module, packagePrefix + type.getSimpleName(), levels));
                    }
                }
            }
        }
        assertTrue(diagnostics.getDiagnostics().stream().noneMatch(item -> item.getKind() == Diagnostic.Kind.ERROR),
                () -> diagnostics.getDiagnostics().toString());
        return List.copyOf(suites);
    }

    private static boolean executable(ClassTree type) {
        var scanner = new TreeScanner<Boolean, Void>() {
            @Override public Boolean visitMethod(MethodTree method, Void unused) {
                return method.getModifiers().getAnnotations().stream()
                        .anyMatch(annotation -> TESTS.contains(simpleName(annotation.getAnnotationType().toString())));
            }

            @Override public Boolean reduce(Boolean first, Boolean second) {
                return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
            }
        };
        return Boolean.TRUE.equals(scanner.scan(type, null));
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private record Suite(String module, String className, List<String> levels) {
        String identity() {
            return module + "/" + className;
        }
    }
}
