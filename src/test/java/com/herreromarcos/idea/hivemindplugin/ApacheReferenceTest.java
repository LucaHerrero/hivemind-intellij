package com.herreromarcos.idea.hivemindplugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Abgleich mit den Originalen von Apache:
 * <ul>
 *   <li>die Deskriptoren aus {@code hivemind-1.1.1.jar}, {@code hivemind-lib-1.1.1.jar} und
 *       {@code tapestry-annotations-4.1.6.jar} sind im Editor fehler- und warnungsfrei</li>
 *   <li>das mitgelieferte Schema stimmt Attribut für Attribut mit {@code DescriptorParser.properties} überein – der
 *       Tabelle, mit der HiveMind selbst Pflichtattribute prüft</li>
 *   <li>der echte {@code DescriptorParser} meldet seine Fehler im Editor</li>
 * </ul>
 */
public class ApacheReferenceTest extends HiveMindTestCase {
    private static final String SCHEMA = "/schema/hivemodule-1.1.xsd";

    // ------------------------------------------------------------------ Original-Deskriptoren

    public void testOriginalHiveMindDescriptorHasNoProblems() throws IOException {
        assertNoProblems(org.apache.hivemind.Registry.class, "copy/hivemind/hivemodule.xml");
    }

    public void testOriginalHiveMindLibDescriptorHasNoProblems() throws IOException {
        // Spring ist eine optionale Fremdbibliothek von hivemind-lib (SpringLookupFactory) und fehlt im Testprojekt
        configure("copy/hivemind-lib/hivemodule.xml", descriptorOf(org.apache.hivemind.lib.BeanFactory.class));
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING).stream()
                .filter(message -> !message.contains("'org.springframework."))
                .toList());
    }

    public void testOriginalTapestryAnnotationsDescriptorHasNoProblems() throws IOException {
        assertNoProblems(org.apache.tapestry.annotations.InjectObject.class, "copy/tapestry-annotations/hivemodule.xml");
    }

    public void testTapestryFrameworkModulesAreFoundViaSubModules() {
        final List<String> modules = HiveMindRegistry.getInstance(getProject()).getModules().stream().map(HiveModule::id).toList();
        // tapestry-framework-4.1.6.jar!/META-INF/hivemodule.xml bindet alle weiteren Module per <sub-module> ein
        assertContainsElements(modules, "tapestry", "tapestry.enhance", "tapestry.props", "tapestry.persist");
    }

    /**
     * Härtetest: alle Deskriptoren aus {@code tapestry-framework-4.1.6.jar} (Hauptdeskriptor und sämtliche Sub-Module)
     * sind fehler- und warnungsfrei. Ausgenommen sind nur Klassen von Fremdbibliotheken, die im Testprojekt fehlen
     * (Servlet-API, OGNL, Javassist, Commons …) – Klassen von Tapestry und HiveMind müssen sich auflösen, ebenso Arrays
     * und primitive Typen ({@code int[]}, {@code java.lang.Object[]} in tapestry.coerce).
     */
    public void testAllTapestryFrameworkDescriptorsHaveNoProblems() {
        final List<HiveModule> modules = HiveMindRegistry.getInstance(getProject()).getModules().stream()
                .filter(module -> module.location().startsWith("tapestry-framework-4.1.6.jar"))
                .toList();
        assertTrue(modules.toString(), modules.size() > 10);
        final List<String> problems = new ArrayList<>();
        for (final HiveModule module : modules) {
            final String path = "copy/tapestry-framework/%s".formatted(module.file().getName());
            configure(path, module.file().getText());
            for (final String message : highlightingMessages(HighlightSeverity.WARNING)) {
                if (isMissingThirdPartyClass(message)) continue;
                problems.add("%s: %s".formatted(module.file().getName(), message));
            }
        }
        assertEmpty(problems);
    }

    /** "Cannot resolve class 'javax.servlet.Servlet' (…)" mit einer Klasse außerhalb von Tapestry/HiveMind. */
    private static boolean isMissingThirdPartyClass(String message) {
        final String prefix = "Cannot resolve class '";
        if (!message.startsWith(prefix)) return false;
        final String className = message.substring(prefix.length(), message.indexOf("'", prefix.length()));
        return !className.startsWith("org.apache.tapestry.") && !className.startsWith("org.apache.hivemind.") && className.contains(".");
    }

    /** Deskriptor aus dem JAR der Klasse als Projektdatei anlegen und alle Meldungen ab Warnung prüfen. */
    private void assertNoProblems(Class<?> member, String path) throws IOException {
        configure(path, descriptorOf(member));
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    private static String descriptorOf(Class<?> member) throws IOException {
        final String jar = new java.io.File(PathManager.getJarPathForClass(member)).toURI().toString();
        try (final InputStream in = new URL("jar:%s!/META-INF/hivemodule.xml".formatted(jar)).openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ Schema ↔ DescriptorParser.properties

    /**
     * Jedes Element, das HiveMind in {@code DescriptorParser.properties} beschreibt, muss im Schema mit genau denselben
     * Attributen und Pflichtangaben vorkommen.
     */
    public void testSchemaMatchesDescriptorParserAttributeTable() throws IOException {
        final Map<String, Map<String, Boolean>> expected = new TreeMap<>();
        final Properties table = new Properties();
        try (final InputStream in = org.apache.hivemind.parse.DescriptorParser.class.getResourceAsStream("DescriptorParser.properties")) {
            table.load(in);
        }
        for (final String key : table.stringPropertyNames()) {
            final String[] parts = key.split("\\.");
            if (parts.length != 3 || !"required".equals(parts[0])) continue;
            expected.computeIfAbsent(parts[1], k -> new TreeMap<>()).put(parts[2], Boolean.parseBoolean(table.getProperty(key)));
        }
        assertFalse(expected.isEmpty());

        final Map<String, List<Map<String, Boolean>>> actual = schemaAttributes();
        final List<String> mismatches = new ArrayList<>();
        for (final var element : expected.entrySet()) {
            final List<Map<String, Boolean>> declarations = actual.getOrDefault(element.getKey(), List.of());
            if (declarations.isEmpty()) {
                mismatches.add("<%s> missing in schema".formatted(element.getKey()));
                continue;
            }
            // mindestens eine Deklaration muss exakt passen (z.B. <schema> im Modul: id Pflicht)
            if (declarations.stream().noneMatch(element.getValue()::equals)) {
                mismatches.add("<%s>: expected %s, schema has %s".formatted(element.getKey(), element.getValue(), declarations));
            }
        }
        assertEmpty(mismatches);
    }

    /** Element → Attribut → Pflicht, je benannter Deklaration im Schema (Elemente können mehrfach deklariert sein). */
    private Map<String, List<Map<String, Boolean>>> schemaAttributes() {
        final URL url = getClass().getResource(SCHEMA);
        assertNotNull(url);
        final VirtualFile vf = VfsUtil.findFileByURL(url);
        assertNotNull(vf);
        final XmlTag root = ((XmlFile) PsiManager.getInstance(getProject()).findFile(vf)).getRootTag();
        final Map<String, XmlTag> namedTypes = new HashMap<>();
        for (final XmlTag type : root.findSubTags("xs:complexType")) namedTypes.put(type.getAttributeValue("name"), type);

        final Map<String, List<Map<String, Boolean>>> result = new HashMap<>();
        collectElements(root, namedTypes, result, Collections.newSetFromMap(new HashMap<>()));
        return result;
    }

    private static void collectElements(XmlTag scope, Map<String, XmlTag> types,
                                        Map<String, List<Map<String, Boolean>>> result, Set<XmlTag> visited) {
        if (!visited.add(scope)) return;
        for (final XmlTag child : scope.getSubTags()) {
            if ("xs:element".equals(child.getName()) && child.getAttributeValue("name") != null) {
                final String typeName = child.getAttributeValue("type");
                final XmlTag type = typeName != null ? types.get(typeName) : child.findFirstSubTag("xs:complexType");
                if (type != null) {
                    final Map<String, Boolean> attributes = new TreeMap<>();
                    for (final XmlTag attribute : type.findSubTags("xs:attribute")) {
                        attributes.put(attribute.getAttributeValue("name"), "required".equals(attribute.getAttributeValue("use")));
                    }
                    result.computeIfAbsent(child.getAttributeValue("name"), k -> new ArrayList<>()).add(attributes);
                    collectElements(type, types, result, visited);
                }
            } else if ("xs:group".equals(child.getName()) && child.getAttributeValue("ref") != null) {
                final XmlTag group = findGroup(scope, child.getAttributeValue("ref"));
                if (group != null) collectElements(group, types, result, visited);
            } else {
                collectElements(child, types, result, visited);
            }
        }
    }

    private static XmlTag findGroup(XmlTag anyTag, String name) {
        XmlTag root = anyTag;
        while (root.getParentTag() != null) root = root.getParentTag();
        for (final XmlTag group : root.findSubTags("xs:group")) {
            if (name.equals(group.getAttributeValue("name"))) return group;
        }
        return null;
    }

    // ------------------------------------------------------------------ echter DescriptorParser im Editor

    public void testDescriptorParserReportsFormatErrors() {
        configure("bad/hivemodule.xml", """
                <module id="bad module" version="1.0">
                  <service-point id="Not.Simple" interface="java.lang.Runnable"><create-instance class="java.lang.Thread"/></service-point>
                  <schema id="S"><element name="e"><attribute name="a" required="maybe"/></element></schema>
                </module>
                """);
        final Set<String> errors = new TreeSet<>(highlightingMessages(HighlightSeverity.ERROR));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.contains("Module identifiers should consist of a period-seperated series of names")));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.contains("Version numbers should be a sequence of three numbers")));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.contains("ids should be simple names with no punctuation")));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.contains("'maybe' is not a valid value for attribute required")));
    }

    public void testInvalidConditionIsReportedByHiveMindParser() {
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad" if="class and or">
                  <load service-id="app.Mailer"/>
                </contribution>
                """);
        assertTrue(highlightingMessages(HighlightSeverity.ERROR).stream().anyMatch(m -> m.startsWith("Invalid condition: ")));
    }
}
