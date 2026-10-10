package com.herreromarcos.idea.hivemindplugin;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;

/** Verweise auf Services aus Java-Code und Tapestry-4-Spezifikationen. */
public class InjectionSupportTest extends HiveMindTestCase {

    public void testRegistryGetServiceResolves() {
        configure("com/example/Client.java", """
                package com.example;
                class Client {
                    void run(org.apache.hivemind.Registry registry) {
                        registry.getService("app.Mai<caret>ler", Mailer.class);
                    }
                }
                """);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testUnknownServiceInJavaIsWarning() {
        configure("com/example/Client.java", """
                package com.example;
                class Client {
                    void run(org.apache.hivemind.Registry registry) {
                        registry.getService("app.Nope", Mailer.class);
                        registry.getConfiguration("app.Hosts");
                    }
                }
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.WARNING), "Unknown service point 'app.Nope'");
        assertDoesntContain(highlightingMessages(HighlightSeverity.WARNING), "Unknown configuration point 'app.Hosts'");
    }

    public void testOtherStringLiteralsHaveNoReferences() {
        configure("com/example/Client.java", """
                package com.example;
                class Client {
                    String s = "app.Mai<caret>ler";
                }
                """);
        final var literal = myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getParent();
        assertFalse(java.util.Arrays.stream(literal.getReferences())
                .anyMatch(r -> r instanceof com.herreromarcos.idea.hivemindplugin.references.HiveMindReference));
    }

    public void testInjectObjectAnnotationResolves() {
        configure("com/example/HomePage.java", """
                package com.example;
                import org.apache.tapestry.annotations.InjectObject;
                public abstract class HomePage {
                    @InjectObject("service:app.Mai<caret>ler")
                    public abstract Mailer getMailer();
                }
                """);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testInjectObjectCompletionOffersPublicServices() {
        configure("com/example/HomePage.java", """
                package com.example;
                import org.apache.tapestry.annotations.InjectObject;
                public abstract class HomePage {
                    @InjectObject("service:<caret>")
                    public abstract Mailer getMailer();
                }
                """);
        final var items = completeAtCaret();
        assertContainsElements(items, "app.Mailer", "hivemind.BuilderFactory");
        assertDoesntContain(items, "app.Settings");
    }

    public void testTapestryPageInjectResolves() {
        WriteAction.run(() -> FileTypeManager.getInstance().associateExtension(XmlFileType.INSTANCE, "page"));
        try {
            configure("WEB-INF/Home.page", """
                    <page-specification class="com.example.HomePage">
                      <inject property="mailer" object="service:app.Mai<caret>ler"/>
                    </page-specification>
                    """);
            assertPointId("Mailer", resolveAtCaret());
        } finally {
            WriteAction.run(() -> FileTypeManager.getInstance().removeAssociatedExtension(XmlFileType.INSTANCE, "page"));
        }
    }

    public void testHivemindBindingPrefixInTemplate() {
        configure("Home.html", """
                <html><body><span jwcid="@Insert" value="hivemind:service:app.Mai<caret>ler"/></body></html>
                """);
        assertPointId("Mailer", resolveAtCaret());
    }

    public void testHivemindBindingPrefixWithUnknownServiceIsWarning() {
        configure("Home.html", """
                <html><body><span jwcid="@Insert" value="hivemind:service:app.Nope"/></body></html>
                """);
        assertContainsElements(highlightingMessages(HighlightSeverity.WARNING), "Unknown service point 'app.Nope'");
    }

    public void testOtherProvidersAreIgnored() {
        configure("Home.html", """
                <html><body><span jwcid="@Insert" value="hivemind:infrastructure:app.Mai<caret>ler"/></body></html>
                """);
        assertEmpty(highlightingMessages(HighlightSeverity.WARNING));
    }

}
