package com.herreromarcos.idea.hivemindplugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.ServiceInfo;

import java.util.List;


/**
 * Abgleich mit der offiziellen HiveMind-1.1-Dokumentation: die Beispiele daraus (Calculator, Overriding a Service,
 * Panorama, EagerLoad, ChainFactory …) müssen fehlerfrei sein und richtig aufgelöst werden.
 */
public class DocumentationExamplesTest extends HiveMindTestCase {

    // ------------------------------------------------------------------ Doku "Bootstrapping" / Beispiel "Calculator"

    public void testCalculatorExampleFromDocumentation() {
        addClass("org.apache.examples", "interface Adder {}");
        addClass("org.apache.examples", "interface Calculator {}");
        addClass("org.apache.examples.impl", "class AdderImpl implements org.apache.examples.Adder {}");
        addClass("org.apache.examples.impl", "class CalculatorImpl implements org.apache.examples.Calculator {}");
        configure("examples/META-INF/hivemodule.xml", """
                <?xml version="1.0"?>
                <!-- Licensed under the Apache License, Version 2.0 -->
                <module id="examples" version="1.0.0" package="org.apache.examples">
                    <service-point id="Adder">
                        <create-instance class="impl.AdderImpl"/>
                        <interceptor service-id="hivemind.LoggingInterceptor"/>
                    </service-point>
                    <service-point id="Calculator">
                        <invoke-factory>
                            <!-- Most properties are autowired by the BuilderFactory -->
                            <construct class="impl.CalculatorImpl"/>
                        </invoke-factory>
                        <interceptor service-id="hivemind.LoggingInterceptor"/>
                    </service-point>
                </module>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));

        final ServiceInfo calculator = service("examples.Calculator");
        assertEquals("org.apache.examples.Calculator", calculator.interfaceName());
        assertEquals("org.apache.examples.impl.CalculatorImpl", calculator.primaryImplementation().className());
        assertEquals("org.apache.examples.impl.AdderImpl", service("examples.Adder").primaryImplementation().className());
    }

    public void testRelativeClassNameResolvesViaModulePackage() {
        addClass("org.apache.examples.impl", "class AdderImpl {}");
        configure("examples/META-INF/hivemodule.xml", """
                <module id="examples" version="1.0.0" package="org.apache.examples">
                  <service-point id="Adder" interface="java.lang.Runnable"><create-instance class="impl.Adder<caret>Impl"/></service-point>
                </module>
                """);
        assertEquals("org.apache.examples.impl.AdderImpl", ((PsiClass) resolveAtCaret()).getQualifiedName());
    }

    // ------------------------------------------------------------------ Doku "Overriding a Service"

    public void testOverrideExampleFromDocumentation() {
        addClass("ex.override", "interface Provider {}");
        addClass("ex.override", "interface Consumer {}");
        addClass("ex.override.impl", "class ProviderImpl implements ex.override.Provider {}");
        addClass("ex.override.impl", "class Consumer implements ex.override.Consumer {}");
        addClass("ex.override.impl", "class OverrideProviderImpl implements ex.override.Provider {}");
        myFixture.addFileToProject("ex/default/hivemodule.xml", """
                <module id="ex.default" version="1.0.0" package="ex.override">
                  <service-point id="Provider" interface="Provider">
                    <create-instance class="impl.ProviderImpl"/>
                  </service-point>
                  <service-point id="Consumer" interface="Consumer">
                    <invoke-factory>
                      <construct class="impl.Consumer">
                        <set-service property="provider" service-id="Provider"/>
                      </construct>
                    </invoke-factory>
                  </service-point>
                </module>
                """);
        configure("ex/override/hivemodule.xml", """
                <module id="ex.override" version="1.0.0">
                  <implementation service-id="ex.default.Provider">
                    <create-instance class="impl.OverrideProviderImpl"/>
                  </implementation>
                </module>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
        // die Implementierung aus <implementation> ersetzt die Standardimplementierung
        assertEquals("ex.override.impl.OverrideProviderImpl", service("ex.default.Provider").primaryImplementation().className());
    }

    public void testMoreThanOneOverrideIsError() {
        myFixture.addFileToProject("o1/hivemodule.xml", """
                <module id="o1" version="1.0.0"><implementation service-id="app.Mailer"><create-instance class="com.example.MailerImpl"/></implementation></module>
                """);
        myFixture.addFileToProject("o2/hivemodule.xml", """
                <module id="o2" version="1.0.0"><implementation service-id="app.Mailer"><create-instance class="com.example.MailerImpl"/></implementation></module>
                """);
        myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        assertTrue(highlightingMessages(HighlightSeverity.ERROR).stream()
                .anyMatch(m -> m.startsWith("Service point 'app.Mailer' is overridden by 2 <implementation> elements")));
    }

    public void testPrivateServiceCannotBeOverriddenFromOtherModule() {
        configureOtherModule("""
                <implementation service-id="app.Settings"><create-instance class="SettingsImpl"/></implementation>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR), "Service point 'app.Settings' is private to module 'app'");
    }

    // ------------------------------------------------------------------ Doku "HiveDoc": Beschreibungen als Text

    public void testDescriptionsAsEnclosedTextAreValid() {
        configure("doc/hivemodule.xml", """
                <module id="mymodule" version="1.0.0" package="com.example">
                  A module for my application with my services, etc.
                  <service-point id="Mailer2" interface="Mailer">
                    Sends mail.
                    <create-instance class="MailerImpl"/>
                  </service-point>
                  <configuration-point id="Things">
                    The things.
                    <schema>
                      <element name="thing">
                        A thing.
                        <attribute name="name" required="true">The name.</attribute>
                      </element>
                    </schema>
                  </configuration-point>
                  <schema id="Shared">Shared schema.</schema>
                </module>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
        // Die Editor-Hervorhebung meldet Text in reinen Element-Inhalten nicht (erst "Validate") → Inhaltstyp direkt prüfen
        for (final XmlTag tag : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), XmlTag.class)) {
            if (!tag.getValue().getTrimmedText().isEmpty() && tag.getSubTags().length > 0 || "attribute".equals(tag.getName())) {
                final XmlElementDescriptor descriptor = tag.getDescriptor();
                assertNotNull("no descriptor for <%s>".formatted(tag.getName()), descriptor);
                assertEquals("<%s> must allow description text".formatted(tag.getName()),
                        XmlElementDescriptor.CONTENT_TYPE_MIXED, descriptor.getContentType());
            }
        }
    }

    // ------------------------------------------------------------------ Doku "Contribution Processing Rules": Translatoren

    public void testContributionAttributeWithServiceTranslator() {
        // Doku "hivemind.EagerLoad": <load service-id="..."/>
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad">
                  <load service-id="app.Mai<caret>ler"/>
                </contribution>
                """);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testUnknownServiceInContributionIsError() {
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad">
                  <load service-id="app.Nope"/>
                </contribution>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR), "Unknown service point 'app.Nope'");
    }

    public void testPanoramaObjectTranslator() {
        // Doku Beispiel "Panorama Startup": translator="object", executable="service:MailStartup"
        configure("panorama/hivemodule.xml", """
                <module id="panorama.startup" version="1.0.0" package="com.example">
                  <schema id="Tasks">
                    <element name="task">
                      <attribute name="title" required="true"/>
                      <attribute name="id" required="true"/>
                      <attribute name="executable" required="true" translator="object"/>
                      <conversion class="MailerImpl"/>
                    </element>
                  </schema>
                  <configuration-point id="Startup" schema-id="Tasks"/>
                  <service-point id="MailStartup" interface="java.lang.Runnable"><create-instance class="MailerImpl"/></service-point>
                  <contribution configuration-id="Startup">
                    <task id="mail" title="Mail" executable="service:Mail<caret>Startup"/>
                  </contribution>
                </module>
                """);
        assertPointId("MailStartup", resolveAtCaret());
    }

    public void testFactoryParametersUseFactorySchema() {
        // Doku "hivemind.lib.ChainFactory": <construct configuration-id="..."/> – kein class, keine Schemafehler;
        // die Bedeutung kommt aus dem parameters-schema im Original-Deskriptor von hivemind-lib
        configureOtherModule("""
                <configuration-point id="Commands"/>
                <service-point id="Chain" interface="Mailer">
                  <invoke-factory service-id="hivemind.lib.ChainFactory">
                    <construct configuration-id="Comm<caret>ands"/>
                  </invoke-factory>
                </service-point>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
        assertPointId("Commands", resolveAtCaret());
    }

    public void testBuilderFactoryConstructWithoutClassIsError() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer"><invoke-factory><construct/></invoke-factory></service-point>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR), "hivemind.BuilderFactory requires the class attribute of <construct>");
    }

    // ------------------------------------------------------------------ Symbole, Nachrichten, Bedingungen, Provider

    public void testSymbolsAndMessagesAreNotReportedAsUnknownIds() {
        myFixture.addFileToProject("other/META-INF/hivemodule.properties", "eager.service=app.Mailer\n");
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory service-id="${factory.id}">
                    <construct class="${impl.class}"/>
                  </invoke-factory>
                </service-point>
                <contribution configuration-id="hivemind.EagerLoad">
                  <load service-id="%eager.service"/>
                </contribution>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    // ------------------------------------------------------------------ Doku "Configurations": Substitutionssymbole

    public void testSymbolResolvesToApplicationDefaultBeforeFactoryDefault() {
        // Doku "hivemind.ApplicationDefaults": überschreibt hivemind.FactoryDefaults
        myFixture.addFileToProject("defaults/hivemodule.xml", """
                <module id="defaults" version="1.0.0">
                  <contribution configuration-id="hivemind.FactoryDefaults">
                    <default symbol="mail.host" value="factory-host"/>
                  </contribution>
                  <contribution configuration-id="hivemind.ApplicationDefaults">
                    <default symbol="mail.host" value="app-host"/>
                  </contribution>
                </module>
                """);
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory><construct class="MailerImpl"><set property="host" value="${mail.ho<caret>st}:25"/></construct></invoke-factory>
                </service-point>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("app-host", ((XmlTag) target.getParent().getParent()).getAttributeValue("value"));
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testEscapedSymbolIsNoReference() {
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad"><load service-id="app.Mailer" note="$${not.a.sym<caret>bol}"/></contribution>
                """);
        assertNull(myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()));
    }

    // ------------------------------------------------------------------ Doku "Localization": %message.key

    public void testMessageKeyResolvesToPropertiesNextToDescriptor() {
        myFixture.addFileToProject("other/META-INF/hivemodule.properties", "eager.service=app.Mailer\n");
        myFixture.addFileToProject("other/META-INF/hivemodule_fr.properties", "eager.service=app.Mailer\n");
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad">
                  <load service-id="%eager.ser<caret>vice"/>
                </contribution>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, Property.class);
        assertEquals("hivemodule.properties", target.getContainingFile().getName());
        final PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertEquals(2, ((PsiPolyVariantReference) reference).multiResolve(false).length);
    }

    public void testUnknownMessageKeyIsWarning() {
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad">
                  <load service-id="%missing.key"/>
                </contribution>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.WARNING), "Unknown message key 'missing.key' (HiveMind shows [MISSING.KEY])");
    }

    // ------------------------------------------------------------------ Doku "interceptor": before/after

    public void testInterceptorOrderNamesResolve() {
        configureOtherModule("""
                <service-point id="Ordered" interface="Mailer">
                  <create-instance class="MailerImpl"/>
                  <interceptor service-id="hivemind.LoggingInterceptor" before="*"/>
                  <interceptor service-id="app.Mailer" name="Security" after="hivemind.Logging<caret>Interceptor, Perf"/>
                  <interceptor service-id="hivemind.LoggingInterceptor" name="Perf"/>
                </service-point>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("hivemind.LoggingInterceptor", ((XmlAttributeValue) target).getValue());
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testUnknownInterceptorOrderNameIsWarning() {
        configureOtherModule("""
                <service-point id="Ordered" interface="Mailer">
                  <create-instance class="MailerImpl"/>
                  <interceptor service-id="hivemind.LoggingInterceptor" before="Nope"/>
                </service-point>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.WARNING), "No interceptor named 'other.Nope' for this service");
    }

    // ------------------------------------------------------------------ Find Usages und Rename

    public void testFindUsagesOfServicePoint() {
        myFixture.addFileToProject("com/example/Client.java", """
                package com.example;
                class Client { Object m(org.apache.hivemind.Registry r) { return r.getService("app.Mailer", Mailer.class); } }
                """);
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory><construct class="MailerImpl"><set-service property="m" service-id="app.Mailer"/></construct></invoke-factory>
                </service-point>
                """);
        myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("id=\"Mailer\"") + 5);
        final var usages = myFixture.findUsages(myFixture.getElementAtCaret());
        final var files = usages.stream().map(usage -> usage.getFile().getName()).sorted().toList();
        assertEquals(List.of("Client.java", "hivemodule.xml"), files);
    }

    public void testRenameServicePointUpdatesQualifiedAndLocalReferences() {
        myFixture.addFileToProject("com/example/Client.java", """
                package com.example;
                class Client { Object m(org.apache.hivemind.Registry r) { return r.getService("app.Mailer", Mailer.class); } }
                """);
        myFixture.addFileToProject("other/META-INF/hivemodule.xml", """
                <module id="other" version="1.0.0" package="com.example">
                  <service-point id="X" interface="Mailer">
                    <invoke-factory><construct class="MailerImpl"><set-service property="m" service-id="app.Mailer"/></construct></invoke-factory>
                  </service-point>
                </module>
                """);
        myFixture.configureFromTempProjectFile(APP_DESCRIPTOR);
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("id=\"Mailer\"") + 5);
        myFixture.renameElementAtCaret("Sender");

        assertTrue(myFixture.getFile().getText().contains("<service-point id=\"Sender\" interface=\"Mailer\">"));
        final String other = myFixture.getPsiManager().findFile(myFixture.findFileInTempDir("other/META-INF/hivemodule.xml")).getText();
        assertTrue(other, other.contains("service-id=\"app.Sender\""));
        final String client = myFixture.getPsiManager().findFile(myFixture.findFileInTempDir("com/example/Client.java")).getText();
        assertTrue(client, client.contains("\"app.Sender\""));
    }

    public void testConditionClassesAreNavigableButNotReported() {
        configureOtherModule("""
                <contribution configuration-id="hivemind.EagerLoad" if="class com.example.Mai<caret>ler and not (class org.Missing)">
                  <load service-id="app.Mailer"/>
                </contribution>
                """);
        assertEquals("com.example.Mailer", ((PsiClass) resolveAtCaret()).getQualifiedName());
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    public void testClassAndBeanObjectProviders() {
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory>
                    <construct class="MailerImpl">
                      <set-object property="type" value="class:Mail<caret>er"/>
                      <set-object property="validator" value="bean:app.Mailer:string,required"/>
                    </construct>
                  </invoke-factory>
                </service-point>
                """);
        assertEquals("com.example.Mailer", ((PsiClass) resolveAtCaret()).getQualifiedName());
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("app.Mailer:") + 4);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testSetResourcePathIsRelativeToDescriptor() {
        myFixture.addFileToProject("other/META-INF/mail.properties", "host=localhost\n");
        configureOtherModule("""
                <service-point id="X" interface="Mailer">
                  <invoke-factory><construct class="MailerImpl"><set-resource property="config" path="mail.prop<caret>erties"/></construct></invoke-factory>
                </service-point>
                """);
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiFile.class);
        assertEquals("mail.properties", ((PsiFile) target).getName());
    }

    // ------------------------------------------------------------------ Schemaregeln aus der Doku

    public void testParametersSchemaAndIdAreMutuallyExclusive() {
        configureOtherModule("""
                <schema id="P"/>
                <service-point id="F" interface="Mailer" parameters-schema-id="P">
                  <parameters-schema/>
                  <create-instance class="MailerImpl"/>
                </service-point>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.ERROR),
                "<parameters-schema> and the parameters-schema-id attribute are mutually exclusive");
    }

    public void testInlineSchemaMustNotHaveId() {
        configureOtherModule("""
                <configuration-point id="C"><schema id="Nope"/></configuration-point>
                """);
        assertFalse(highlightingMessages(HighlightSeverity.ERROR).isEmpty());
    }

    public void testRulesAreValidated() {
        configureOtherModule("""
                <schema id="S">
                  <element name="e">
                    <attribute name="a" translator="int,min=0"/>
                    <rules>
                      <create-object class="MailerImpl"/>
                      <read-attribute attribute="a" property="a"/>
                      <invoke-parent method="addElement"/>
                    </rules>
                  </element>
                </schema>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

    // ------------------------------------------------------------------ Hilfen

    private void addClass(String packageName, String declaration) {
        final String name = declaration.replaceAll("^(interface|class)\\s+(\\w+).*$", "$2");
        myFixture.addFileToProject("%s/%s.java".formatted(packageName.replace('.', '/'), name),
                "package %s; public %s".formatted(packageName, declaration));
    }

    private ServiceInfo service(String id) {
        return HiveMindRegistry.getInstance(getProject()).getServices().stream()
                .filter(service -> service.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no service %s in %s".formatted(id,
                        HiveMindRegistry.getInstance(getProject()).getServices().stream().map(ServiceInfo::id).toList())));
    }

}
