package com.herreromarcos.idea.hivemindplugin;

import java.util.List;

/**
 * Setter, die die BuilderFactory per Autowiring mit einem Service belegt, sind nicht unbenutzt – aber nur, wenn HiveMind
 * sie wirklich aufruft (BuilderFactory, genau ein sichtbarer Service, {@code autowire-services} nicht {@code false}).
 */
public class AutowiringTest extends HiveMindTestCase {
    private static final String REPORTER_IMPL = """
            package com.example;
            public class ReporterImpl implements Reporter {
                public void setMailer(Mailer mailer) {}
                public void setPrinter(Printer printer) {}
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("com/example/Reporter.java", "package com.example; public interface Reporter {}");
        // kein Service
        myFixture.addFileToProject("com/example/Printer.java", "package com.example; public interface Printer {}");
        myFixture.addFileToProject("com/example/Secret.java", "package com.example; public interface Secret {}");
        myFixture.addFileToProject("com/example/SecretImpl.java", "package com.example; public class SecretImpl implements Secret {}");
    }

    public void testSettersForVisibleServicesAreUsed() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <invoke-factory>
                    <construct class="ReporterImpl"/>
                  </invoke-factory>
                </service-point>
                <service-point id="Secret" interface="Secret" visibility="private">
                  <create-instance class="SecretImpl"/>
                </service-point>
                """);
        final List<String> unused = unusedMethods("com/example/ReporterImpl.java", """
                package com.example;
                import org.apache.hivemind.service.ClassFactory;
                import org.apache.hivemind.service.ThreadEventNotifier;
                public class ReporterImpl implements Reporter {
                    public void setMailer(Mailer mailer) {}
                    public void setSecret(Secret secret) {}
                    public void setNotifier(ThreadEventNotifier notifier) {}
                    public void setSettings(Settings settings) {}
                    public void setClassFactory(ClassFactory factory) {}
                    public void setPrinter(Printer printer) {}
                    public void setName(String name) {}
                    public static void setDefaultMailer(Mailer mailer) {}
                    public Mailer setFallback(Mailer mailer) { return mailer; }
                }
                """);
        // app.Mailer (öffentlich), other.Secret (privat, aber im eigenen Modul), hivemind.ThreadEventNotifier (aus dem JAR)
        assertDoesntContain(unused, "setMailer", "setSecret", "setNotifier");
        // app.Settings ist privat in "app"; ClassFactory gibt es zweimal (hivemind, tapestry); Printer und String sind
        // keine Services; die beiden letzten sind keine Bean-Setter
        assertContainsElements(unused, "setSettings", "setClassFactory", "setPrinter", "setName", "setDefaultMailer", "setFallback");
    }

    public void testAmbiguousServiceIsNotAutowired() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <invoke-factory>
                    <construct class="ReporterImpl"/>
                  </invoke-factory>
                </service-point>
                <service-point id="BackupMailer" interface="Mailer">
                  <create-instance class="MailerImpl"/>
                </service-point>
                """);
        assertContainsElements(unusedMethods("com/example/ReporterImpl.java", REPORTER_IMPL), "setMailer");
    }

    public void testAutowiringSwitchedOff() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <invoke-factory>
                    <construct class="ReporterImpl" autowire-services="false"/>
                  </invoke-factory>
                </service-point>
                """);
        assertContainsElements(unusedMethods("com/example/ReporterImpl.java", REPORTER_IMPL), "setMailer");
    }

    public void testCreateInstanceDoesNotAutowire() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <create-instance class="ReporterImpl"/>
                </service-point>
                """);
        assertContainsElements(unusedMethods("com/example/ReporterImpl.java", REPORTER_IMPL), "setMailer");
    }

    public void testOverridingImplementationAutowires() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <create-instance class="ReporterImpl"/>
                </service-point>
                <implementation service-id="Reporter">
                  <invoke-factory>
                    <construct class="ReporterImpl"/>
                  </invoke-factory>
                </implementation>
                """);
        final List<String> unused = unusedMethods("com/example/ReporterImpl.java", REPORTER_IMPL);
        assertDoesntContain(unused, "setMailer");
        assertContainsElements(unused, "setPrinter");
    }

    public void testInheritedSetterIsUsed() {
        configureOtherModule("""
                <service-point id="Reporter" interface="Reporter">
                  <invoke-factory>
                    <construct class="ReporterImpl"/>
                  </invoke-factory>
                </service-point>
                """);
        myFixture.addFileToProject("com/example/ReporterImpl.java",
                "package com.example; public class ReporterImpl extends AbstractReporter implements Reporter {}");
        final List<String> unused = unusedMethods("com/example/AbstractReporter.java", """
                package com.example;
                public abstract class AbstractReporter {
                    public void setMailer(Mailer mailer) {}
                    public void setPrinter(Printer printer) {}
                }
                """);
        assertDoesntContain(unused, "setMailer");
        assertContainsElements(unused, "setPrinter");
    }

    public void testClassWithoutServiceIsNotAutowired() {
        assertContainsElements(unusedMethods("com/example/Helper.java", """
                package com.example;
                public class Helper {
                    public void setMailer(Mailer mailer) {}
                }
                """), "setMailer");
    }

    private List<String> unusedMethods(String path, String text) {
        configure(path, text);
        return unusedMethods();
    }
}
