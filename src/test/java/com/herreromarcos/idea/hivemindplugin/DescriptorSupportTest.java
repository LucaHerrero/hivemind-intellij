package com.herreromarcos.idea.hivemindplugin;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.ServiceImplementation;
import com.herreromarcos.idea.hivemindplugin.model.ServiceInfo;

import java.util.Arrays;
import java.util.List;

/** Moduldeskriptoren: Registry, Referenzen, Completion, Prüfungen, Schema, Gutter-Icons. */
public class DescriptorSupportTest extends HiveMindTestCase {

    // ------------------------------------------------------------------ Registry

    public void testRegistryCollectsServicesOfProjectAndLibraries() {
        final List<String> ids = HiveMindRegistry.getInstance(getProject()).getServices().stream().map(ServiceInfo::id).toList();
        assertContainsElements(ids, "app.Mailer", "app.Settings", "hivemind.BuilderFactory", "hivemind.LoggingInterceptor",
                "hivemind.lib.ChainFactory", "hivemind.lib.PipelineFactory");
        // aus den JARs, nicht aus dem Projekt
        assertFalse(service("hivemind.BuilderFactory").point().module().inProject());
        assertTrue(service("app.Mailer").point().module().inProject());
    }

    public void testOriginalDescriptorsAreResolvedCompletely() {
        // Klassen der Original-Deskriptoren (Paket "org.apache.hivemind", relative Namen wie "service.impl.BuilderFactory")
        final ServiceInfo builder = service("hivemind.BuilderFactory");
        assertEquals("org.apache.hivemind.ServiceImplementationFactory", builder.interfaceName());
        assertEquals("org.apache.hivemind.service.impl.BuilderFactory", builder.primaryImplementation().className());
        assertEquals("primitive", builder.model());
        // alle Interfaces der Module "hivemind" und "hivemind.lib" müssen auflösbar sein – außer denen der optionalen
        // Fremdbibliothek Spring (hivemind.lib.SpringLookupFactory)
        final List<String> unresolved = HiveMindRegistry.getInstance(getProject()).getServices().stream()
                .filter(service -> List.of("hivemind", "hivemind.lib").contains(service.point().module().id()))
                .map(ServiceInfo::interfaceName)
                .filter(name -> JavaPsiFacade.getInstance(getProject()).findClass(name, GlobalSearchScope.allScope(getProject())) == null)
                .toList();
        assertTrue(unresolved.toString(), unresolved.stream().allMatch(name -> name.startsWith("org.springframework.")));
    }

    public void testServiceInfoResolvesInterfaceImplementationAndModel() {
        final ServiceInfo mailer = service("app.Mailer");
        assertEquals("com.example.Mailer", mailer.interfaceName());
        final ServiceImplementation implementation = mailer.primaryImplementation();
        assertNotNull(implementation);
        assertEquals("com.example.MailerImpl", implementation.className());
        assertEquals("hivemind.BuilderFactory", implementation.factoryId());
        assertEquals("singleton", mailer.model());
        assertEquals(List.of("hivemind.LoggingInterceptor"), mailer.interceptors());

        // ohne interface-Attribut: id im Paket des Moduls
        final ServiceInfo settings = service("app.Settings");
        assertEquals("com.example.Settings", settings.interfaceName());
        assertEquals("threaded", settings.model());
        assertTrue(settings.point().isPrivate());
    }

    // ------------------------------------------------------------------ neighbor-class

    public void testNeighborClassIsOptional() {
        configure("plain/META-INF/hivemodule.xml", """
                <module id="plain" version="1.0.0" package="com.example">
                  <service-point id="Clock" interface="Settings">
                    <create-instance class="SettingsImpl"/>
                  </service-point>
                </module>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testNeighborClassResolvesRelativeToModulePackage() {
        configure("neighbor/META-INF/hivemodule.xml", """
                <module id="neighbor" version="1.0.0" package="com.example" neighbor-class="Mailer<caret>Impl"/>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiClass.class);
        assertEquals("com.example.MailerImpl", ((PsiClass) target).getQualifiedName());
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testNeighborClassFullyQualified() {
        configure("neighbor/META-INF/hivemodule.xml", """
                <module id="neighbor" version="1.0.0" neighbor-class="com.example.SettingsImpl"/>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testUnknownNeighborClassIsError() {
        configure("neighbor/META-INF/hivemodule.xml", """
                <module id="neighbor" version="1.0.0" package="com.example" neighbor-class="Missing"/>
                """);
        assertTrue(highlightingMessages(HighlightSeverity.ERROR).stream().anyMatch(message -> message.contains("'Missing'")));
    }

    public void testNeighborClassMustNotBeInterface() {
        configure("neighbor/META-INF/hivemodule.xml", """
                <module id="neighbor" version="1.0.0" package="com.example" neighbor-class="Mailer"/>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR),
                "Neighbor class 'com.example.Mailer' is an interface; HiveMind needs a class to load the services with");
    }

    // ------------------------------------------------------------------ Implementierungen

    public void testImplementationElementOfOtherModuleIsCollected() {
        myFixture.addFileToProject("other/META-INF/hivemodule.xml", """
                <module id="other" version="1.0.0">
                  <service-point id="Clock" interface="com.example.Settings"/>
                  <implementation service-id="Clock">
                    <create-instance class="com.example.SettingsImpl" model="pooled"/>
                  </implementation>
                </module>
                """);
        final ServiceInfo clock = service("other.Clock");
        assertEquals("com.example.SettingsImpl", clock.primaryImplementation().className());
        assertEquals("pooled", clock.model());
    }

    public void testSubModuleIsFollowed() {
        myFixture.addFileToProject("app/META-INF/extra/services.xml", """
                <module id="app.extra" version="1.0.0" package="com.example">
                  <service-point id="Audit" interface="Mailer"><create-instance class="MailerImpl"/></service-point>
                </module>
                """);
        configure("main/META-INF/hivemodule.xml", """
                <module id="main" version="1.0.0">
                  <sub-module descriptor="../../app/META-INF/extra/serv<caret>ices.xml"/>
                </module>
                """);
        assertNotNull(service("app.extra.Audit"));
        assertInstanceOf(resolveAtCaret(), XmlFile.class);
    }

    // ------------------------------------------------------------------ Referenzen und Completion

    public void testUnqualifiedServiceIdResolvesInOwnModule() {
        configure("app2/hivemodule.xml", """
                <module id="app" version="1.0.0" package="com.example">
                  <service-point id="Other" interface="Mailer">
                    <invoke-factory>
                      <construct class="MailerImpl"><set-service property="mailer" service-id="Mai<caret>ler"/></construct>
                    </invoke-factory>
                  </service-point>
                </module>
                """);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testQualifiedServiceIdResolvesInOtherModule() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer"><invoke-factory service-id="hivemind.Builder<caret>Factory"/></service-point>
                """);
        final PsiElement target = resolveAtCaret();
        assertPointId("BuilderFactory", target);
        assertTrue(pointDeclaration(target).getContainingFile().getVirtualFile().getPath().contains("hivemind-1.1.1.jar!/META-INF"));
    }

    public void testConfigurationAndSchemaIdsResolve() {
        configure("app2/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="Ho<caret>sts"/>
                </module>
                """);
        assertPointId("Hosts", resolveAtCaret());

        configure("app3/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <configuration-point id="More" schema-id="Host<caret>Schema"/>
                </module>
                """);
        assertPointId("HostSchema", resolveAtCaret());
    }

    public void testServiceIdInConstructorParameterAndObjectProvider() {
        configure("app2/hivemodule.xml", """
                <module id="app" version="1.0.0" package="com.example">
                  <service-point id="Other" interface="Mailer">
                    <invoke-factory>
                      <construct class="MailerImpl">
                        <service>Mai<caret>ler</service>
                        <set-object property="x" value="service:app.Settings"/>
                      </construct>
                    </invoke-factory>
                  </service-point>
                </module>
                """);
        assertPointId("Mailer", resolveAtCaret());

        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("app.Settings") + 5);
        assertPointId("Settings", resolveAtCaret());
    }

    public void testModuleIdResolves() {
        configureOtherModule("<dependency module-id=\"hive<caret>mind\"/>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("module", ((XmlTag) target.getParent().getParent()).getName());
    }

    public void testClassNamesResolveRelativeToModulePackage() {
        configure("app2/hivemodule.xml", """
                <module id="app2" version="1.0.0" package="com.example">
                  <service-point id="X" interface="Mailer"><create-instance class="Mailer<caret>Impl,foo=bar"/></service-point>
                </module>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiClass.class);
        assertEquals("com.example.MailerImpl", ((PsiClass) target).getQualifiedName());
    }

    public void testModuleIdIsDefaultPackage() {
        configure("com/example/hivemodule.xml", """
                <module id="com.example" version="1.0.0">
                  <service-point id="Y" interface="Mailer"><create-instance class="Mailer<caret>Impl"/></service-point>
                </module>
                """);
        assertEquals("com.example.MailerImpl", ((PsiClass) resolveAtCaret()).getQualifiedName());
        assertEquals("com.example.Mailer", service("com.example.Y").interfaceName());
    }

    public void testSchemaRequiresModuleVersion() {
        // fehlende Pflichtattribute meldet die (standardmäßig aktive) Inspektion "RequiredAttributes"
        myFixture.enableInspections(new RequiredAttributesInspection());
        configure("x/hivemodule.xml", "<module id=\"x\"/>");
        assertTrue(highlightingMessages(HighlightSeverity.WARNING).stream().anyMatch(m -> m.contains("version")));
    }

    public void testServiceIdCompletion() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer"><invoke-factory service-id="<caret>"/></service-point>
                """);
        final List<String> items = completeAtCaret();
        assertContainsElements(items, "app.Mailer", "hivemind.BuilderFactory", "X");
        // private Services anderer Module werden nicht angeboten
        assertDoesntContain(items, "app.Settings", "hivemind.ThreadLocaleFactory");
    }

    public void testUnqualifiedIdsAreOfferedForOwnModule() {
        configure("app2/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="<caret>"/>
                </module>
                """);
        assertContainsElements(completeAtCaret(), "Hosts");
    }

    // ------------------------------------------------------------------ Prüfungen

    public void testAppDescriptorHasNoErrorsOrWarnings() {
        myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testUnknownIdsAreErrors() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory service-id="Nope"/>
                  <interceptor service-id="hivemind.Missing"/>
                </service-point>
                <contribution configuration-id="app.Nothing"/>
                """);
        final List<String> errors = highlightingMessages(HighlightSeverity.ERROR);
        assertContainsElements(errors, "Unknown service point 'other.Nope'", "Unknown service point 'hivemind.Missing'",
                "Unknown configuration point 'app.Nothing'");
    }

    public void testUnknownClassInModulePackageIsError() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer"><create-instance class="NoSuchImpl"/></service-point>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR),
                "Cannot resolve class 'NoSuchImpl' (also searched in module package 'com.example')");
    }

    public void testPrivateServiceOfOtherModuleIsWarning() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory><construct class="MailerImpl"><set-service property="s" service-id="app.Settings"/></construct></invoke-factory>
                </service-point>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.WARNING), "Service point 'app.Settings' is private to module 'app'");
    }

    public void testDuplicateServicePointAndMissingImplementation() {
        configure("app2/hivemodule.xml", """
                <module id="app" version="1.0.0" package="com.example">
                  <service-point id="Mailer" interface="Mailer"/>
                  <service-point id="Lonely" interface="Mailer"/>
                </module>
                """);
        final List<String> messages = highlightingMessages(HighlightSeverity.WARNING);
        assertTrue(messages.toString(), messages.stream().anyMatch(m -> m.startsWith("Duplicate service point 'app.Mailer'")));
        assertContainsElements(messages, "Service point 'app.Lonely' has no implementation (create-instance or invoke-factory)");
    }

    // ------------------------------------------------------------------ Schema

    public void testSchemaOffersModuleChildren() {
        final PsiFile file = myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        final XmlTag root = ((XmlFile) file).getRootTag();
        final XmlElementDescriptor descriptor = root.getDescriptor();
        assertNotNull("no schema descriptor for <module>", descriptor);
        final List<String> children = Arrays.stream(descriptor.getElementsDescriptors(root)).map(XmlElementDescriptor::getName).toList();
        assertContainsElements(children, "service-point", "configuration-point", "contribution", "implementation", "schema", "sub-module", "dependency");
    }

    public void testSchemaReportsUnknownElement() {
        configureOtherModule("<servce-point id=\"X\"/>");
        assertFalse(highlightingMessages(HighlightSeverity.ERROR).isEmpty());
    }

    public void testSchemaTagCompletion() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer"><<caret></service-point>
                """);
        assertContainsElements(completeAtCaret(), "create-instance", "invoke-factory", "interceptor");
    }

    // ------------------------------------------------------------------ Gutter

    public void testGutterOnImplementationClass() {
        myFixture.configureFromTempProjectFile("com/example/MailerImpl.java");
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("MailerImpl"));
        final List<GutterMark> gutters = myFixture.findGuttersAtCaret();
        assertTrue(gutters.stream().anyMatch(g -> "HiveMind service: app.Mailer".equals(g.getTooltipText())));
    }

    public void testGutterOnServicePoint() {
        myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("service-point id=\"Mailer\"") + 2);
        final List<GutterMark> gutters = myFixture.findGuttersAtCaret();
        assertTrue(gutters.stream().anyMatch(g -> "Navigate to interface / implementation of 'app.Mailer'".equals(g.getTooltipText())));
    }

    // ------------------------------------------------------------------ Hilfen

    private ServiceInfo service(String id) {
        return HiveMindRegistry.getInstance(getProject()).getServices().stream()
                .filter(service -> service.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no service " + id));
    }

}
