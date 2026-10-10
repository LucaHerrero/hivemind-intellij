package com.herreromarcos.idea.hivemindplugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.herreromarcos.idea.hivemindplugin.references.PointTarget;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Gemeinsames Testprojekt: die echten JARs von Apache HiveMind 1.1.1 ({@code hivemind}, {@code hivemind-lib}) und
 * {@code tapestry-annotations}/{@code tapestry-framework} 4.1.6 als Bibliotheken – samt ihrer Original-Deskriptoren – sowie das Anwendungsmodul
 * "app" mit Mailer (BuilderFactory), privatem Settings-Service, Configuration-Point und Schema.
 */
public abstract class HiveMindTestCase extends LightJavaCodeInsightFixtureTestCase {
    /** Bibliotheken aus dem Test-Classpath (Gradle-Abhängigkeiten), je über eine enthaltene Klasse gefunden. */
    private static final LightProjectDescriptor WITH_APACHE_LIBRARIES = new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21) {
        @Override
        public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model,
                                    @NotNull ContentEntry contentEntry) {
            super.configureModule(module, model, contentEntry);
            addLibrary(model, "hivemind", org.apache.hivemind.Registry.class);
            addLibrary(model, "hivemind-lib", org.apache.hivemind.lib.BeanFactory.class);
            addLibrary(model, "tapestry-annotations", org.apache.tapestry.annotations.InjectObject.class);
            addLibrary(model, "tapestry-framework", org.apache.tapestry.IPage.class);
        }
    };

    private static void addLibrary(ModifiableRootModel model, String name, Class<?> member) {
        final File jar = new File(PathManager.getJarPathForClass(member));
        PsiTestUtil.addLibrary(model, name, jar.getParent(), jar.getName());
    }

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return WITH_APACHE_LIBRARIES;
    }
    protected static final String CARET = "<caret>";
    protected static final String APP_DESCRIPTOR = "app/META-INF/hivemodule.xml";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("com/example/Mailer.java", "package com.example; public interface Mailer { void send(String to); }");
        myFixture.addFileToProject("com/example/MailerImpl.java",
                "package com.example; public class MailerImpl implements Mailer { public void send(String to) {} }");
        myFixture.addFileToProject("com/example/Settings.java", "package com.example; public interface Settings {}");
        myFixture.addFileToProject("com/example/SettingsImpl.java", "package com.example; public class SettingsImpl implements Settings {}");
        myFixture.addFileToProject(APP_DESCRIPTOR, """
                <module id="app" version="1.0.0" package="com.example">
                  <dependency module-id="hivemind"/>
                  <service-point id="Mailer" interface="Mailer">
                    <invoke-factory>
                      <construct class="MailerImpl">
                        <set-service property="settings" service-id="Settings"/>
                        <set-configuration property="hosts" configuration-id="Hosts"/>
                      </construct>
                    </invoke-factory>
                    <interceptor service-id="hivemind.LoggingInterceptor"/>
                  </service-point>
                  <service-point id="Settings" visibility="private">
                    <create-instance class="SettingsImpl" model="threaded"/>
                  </service-point>
                  <configuration-point id="Hosts" schema-id="HostSchema"/>
                  <schema id="HostSchema">
                    <element name="host">
                      <attribute name="name" required="true"/>
                    </element>
                  </schema>
                  <contribution configuration-id="Hosts">
                    <host name="localhost"/>
                  </contribution>
                </module>
                """);
    }

    /** Legt die Datei an und öffnet sie im Editor; {@code <caret>} markiert die Cursorposition. */
    protected PsiFile configure(String path, String textWithCaret) {
        final int caret = textWithCaret.indexOf(CARET);
        final PsiFile file = myFixture.addFileToProject(path, textWithCaret.replace(CARET, ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
        return file;
    }

    protected PsiElement resolveAtCaret() {
        final PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", reference);
        if (reference instanceof final PsiPolyVariantReference poly) {
            final var results = poly.multiResolve(false);
            return results.length > 0 ? results[0].getElement() : null;
        }
        return reference.resolve();
    }

    protected List<String> completeAtCaret() {
        myFixture.completeBasic();
        final List<String> items = myFixture.getLookupElementStrings();
        assertNotNull("completion was inserted directly or nothing was offered", items);
        return items;
    }

    protected List<String> highlightingMessages(HighlightSeverity minimum) {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity().compareTo(minimum) >= 0)
                .map(info -> info.getDescription())
                .toList();
    }

    /** Deklaration ({@code id}-Attributwert), auf die eine Punkt-Referenz auflöst. */
    protected static XmlAttributeValue pointDeclaration(PsiElement target) {
        assertInstanceOf(target, PomTargetPsiElement.class);
        final PomTarget pomTarget = ((PomTargetPsiElement) target).getTarget();
        assertInstanceOf(pomTarget, PointTarget.class);
        final PsiElement navigation = ((PointTarget) pomTarget).getNavigationElement();
        assertInstanceOf(navigation, XmlAttributeValue.class);
        return (XmlAttributeValue) navigation;
    }

    protected static void assertPointId(String expectedId, PsiElement target) {
        assertEquals(expectedId, pointDeclaration(target).getValue());
    }

    /** Ein zweites Anwendungsmodul mit dem angegebenen Inhalt (zwischen {@code <module>} und {@code </module>}). */
    protected PsiFile configureOtherModule(String body) {
        return configure("other/META-INF/hivemodule.xml", """
                <module id="other" version="1.0.0" package="com.example">
                %s
                </module>
                """.formatted(body));
    }
}
