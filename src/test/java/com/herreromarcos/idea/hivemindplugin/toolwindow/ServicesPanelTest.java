package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.herreromarcos.idea.hivemindplugin.HiveMindTestCase;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;

import java.util.List;

/** Die Maske: Tabelle aller Services, Filter, Aktualisierung nach Änderungen. */
public class ServicesPanelTest extends HiveMindTestCase {

    public void testListsAllServices() {
        final ServicesPanel panel = createPanel();
        assertContainsElements(ids(panel), "app.Mailer", "app.Settings", "hivemind.BuilderFactory", "hivemind.lib.ChainFactory");
        assertEquals(HiveMindRegistry.getInstance(getProject()).getServices().size(), panel.getVisibleRows().size());
        assertTrue(panel.getVisibleRows().stream().filter(row -> row.id().startsWith("hivemind.")).allMatch(ServiceRow::library));

        final ServiceRow mailer = panel.getVisibleRows().getFirst();
        assertEquals("com.example.Mailer", mailer.interfaceName());
        assertEquals("MailerImpl", mailer.implementationText());
        assertEquals("app", mailer.moduleId());
        assertEquals("singleton", mailer.model());
        assertEquals(List.of("hivemind.LoggingInterceptor"), mailer.interceptors());
        assertFalse(mailer.library());
        assertNotNull(mailer.declaration().getElement());
    }

    public void testFilterMatchesIdInterfaceClassAndModule() {
        final ServicesPanel panel = createPanel();
        panel.setFilter("mailerimpl");
        assertEquals(List.of("app.Mailer"), ids(panel));
        // app.Mailer passt über seine Factory hivemind.BuilderFactory
        panel.setFilter("hivemind");
        assertTrue(ids(panel).contains("app.Mailer"));
        assertFalse(ids(panel).contains("app.Settings"));
        panel.setFilter("app.Settings");
        assertEquals(List.of("app.Settings"), ids(panel));
        panel.setFilter("");
        assertEquals(HiveMindRegistry.getInstance(getProject()).getServices().size(), panel.getVisibleRows().size());
    }

    public void testRefreshPicksUpNewServices() {
        final ServicesPanel panel = createPanel();
        myFixture.addFileToProject("more/hivemodule.xml", """
                <module id="more" version="1.0.0" package="com.example">
                  <service-point id="Extra" interface="Mailer"><create-instance class="MailerImpl"/></service-point>
                </module>
                """);
        panel.refresh();
        waitForPanel();
        assertTrue(ids(panel).contains("more.Extra"));
    }

    private ServicesPanel createPanel() {
        final ServicesPanel panel = new ServicesPanel(getProject());
        Disposer.register(getTestRootDisposable(), panel);
        waitForPanel();
        return panel;
    }

    private static void waitForPanel() {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static List<String> ids(ServicesPanel panel) {
        return panel.getVisibleRows().stream().map(ServiceRow::id).toList();
    }
}
