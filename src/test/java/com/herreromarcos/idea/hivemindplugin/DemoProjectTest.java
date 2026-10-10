package com.herreromarcos.idea.hivemindplugin;

import com.intellij.lang.annotation.HighlightSeverity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Das Demo-Projekt ({@code demo/}, wird von {@code gradlew runIde} geöffnet) zeigt, was es zeigen soll:
 * {@code hivemodule.xml} ohne Meldungen, {@code fehlerbeispiele.xml} mit genau den beabsichtigten Fehlern.
 */
public class DemoProjectTest extends HiveMindTestCase {
    private static final Path DEMO = Path.of("demo/src/main");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        try (final Stream<Path> files = Files.walk(DEMO)) {
            for (final Path file : files.filter(Files::isRegularFile).toList()) {
                final String relative = DEMO.relativize(file).toString().replace('\\', '/');
                final String path = relative.startsWith("java/") ? relative.substring("java/".length())
                        : "demo/" + relative.substring("resources/".length());
                myFixture.addFileToProject(path, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
    }

    public void testDemoDescriptorHasNoProblems() {
        myFixture.configureFromTempProjectFile("demo/META-INF/hivemodule.xml");
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testDemoJavaHasNoHiveMindProblems() {
        for (final String file : List.of("com/example/demo/DemoClient.java", "com/example/demo/HomePage.java")) {
            myFixture.configureFromTempProjectFile(file);
            assertEmpty(file, highlightingMessages(HighlightSeverity.WARNING).stream()
                    .filter(message -> message.startsWith("Unknown") || message.contains("private to module"))
                    .toList());
        }
    }

    public void testErrorExamplesShowIntendedProblems() throws IOException {
        myFixture.configureFromTempProjectFile("demo/META-INF/fehlerbeispiele.xml");
        final List<String> messages = highlightingMessages(HighlightSeverity.WARNING);
        final String all = String.join("\n", messages);
        for (final String expected : List.of(
                "Version numbers should be a sequence of three numbers",
                "Cannot resolve class 'impl.GibtEsNicht'",
                "Unknown service point 'demo.Unbekannt'",
                "Service point 'demo.Settings' is private to module 'demo'",
                "Service point 'demo.fehler.Leer' has no implementation",
                "Invalid condition: ")) {
            assertTrue("missing: %s\nin:\n%s".formatted(expected, all), all.contains(expected));
        }
    }
}
