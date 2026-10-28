package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.herreromarcos.idea.hivemindplugin.HiveMindTestCase;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.toolwindow.ServiceTree.ServicePackage;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/** Die Maske: Tabelle und Baum aller Services, Filter, Aktualisierung nach Änderungen. */
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

    public void testTreeGroupsServicesByPackage() {
        final ServicesPanel panel = createTreePanel();
        final DefaultMutableTreeNode root = panel.getTreeRoot();
        assertEquals(List.of("Mailer", "Settings"), names(child(root, "app")));

        // Unterpakete vor den Services, Anzahl inklusive Unterpaketen
        final DefaultMutableTreeNode hivemind = child(root, "hivemind");
        assertInstanceOf(((DefaultMutableTreeNode) hivemind.getFirstChild()).getUserObject(), ServicePackage.class);
        assertContainsElements(names(child(hivemind, "lib")), "ChainFactory");
        assertContainsElements(names(hivemind), "lib", "BuilderFactory");
        final ServicePackage hivemindPackage = (ServicePackage) hivemind.getUserObject();
        assertEquals(hivemind.getLeafCount(), hivemindPackage.services());
        assertTrue(hivemindPackage.library());
        assertFalse(((ServicePackage) child(root, "app").getUserObject()).library());
        assertEquals(panel.getVisibleRows().size(), root.getLeafCount());
    }

    public void testTreeCompactsPackagesWithoutServices() {
        final ServicesPanel panel = createTreePanel();
        myFixture.addFileToProject("deep/hivemodule.xml", """
                <module id="com.example.deep" version="1.0.0" package="com.example">
                  <service-point id="Deep" interface="Mailer"><create-instance class="MailerImpl"/></service-point>
                </module>
                """);
        panel.refresh();
        waitForPanel();
        final DefaultMutableTreeNode deep = child(panel.getTreeRoot(), "com.example.deep");
        assertEquals("com.example.deep", ((ServicePackage) deep.getUserObject()).qualifiedName());
        assertEquals(List.of("Deep"), names(deep));
    }

    public void testTreeFilterExpandsMatchesAndRestoresExpansion() {
        final ServicesPanel panel = createTreePanel();
        final JTree tree = panel.getTree();
        final DefaultMutableTreeNode app = child(panel.getTreeRoot(), "app");
        assertFalse(tree.isExpanded(new TreePath(app.getPath())));
        tree.expandPath(new TreePath(app.getPath()));

        panel.setFilter("mailerimpl");
        assertEquals(List.of("app.Mailer"), ids(panel));
        final DefaultMutableTreeNode filteredApp = child(panel.getTreeRoot(), "app");
        assertEquals(List.of("Mailer"), names(filteredApp));
        assertTrue(tree.isExpanded(new TreePath(filteredApp.getPath())));
        panel.setFilter("hivemind");
        assertTrue(tree.isExpanded(new TreePath(child(panel.getTreeRoot(), "hivemind").getPath())));

        // ohne Filter wieder der vorherige Zustand: app aufgeklappt, hivemind nicht (war nur beim Filtern aufgeklappt)
        panel.setFilter("");
        assertTrue(tree.isExpanded(new TreePath(child(panel.getTreeRoot(), "app").getPath())));
        assertFalse(tree.isExpanded(new TreePath(child(panel.getTreeRoot(), "hivemind").getPath())));
    }

    public void testSwitchingViewsKeepsServices() {
        final ServicesPanel panel = createTreePanel();
        panel.setFilter("app.");
        assertEquals(List.of("Mailer", "Settings"), names(child(panel.getTreeRoot(), "app")));
        panel.setTreeMode(false);
        assertEquals(List.of("app.Mailer", "app.Settings"), ids(panel));
    }

    /** Panel im Baummodus; der Modus wird nach dem Test zurückgesetzt, weil das Light-Projekt wiederverwendet wird. */
    private ServicesPanel createTreePanel() {
        final ServicesPanel panel = createPanel();
        panel.setTreeMode(true);
        final PropertiesComponent properties = PropertiesComponent.getInstance(getProject());
        Disposer.register(getTestRootDisposable(), () -> properties.unsetValue("hivemind.toolwindow.treeMode"));
        waitForPanel();
        return panel;
    }

    private static DefaultMutableTreeNode child(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof final ServicePackage servicePackage && servicePackage.name().equals(name)) return child;
        }
        throw new AssertionError("No package '%s' in %s".formatted(name, names(parent)));
    }

    /** Namen der Kinder: Pakete mit ihrem (zusammengefassten) Namen, Services mit einfachem Namen. */
    private static List<String> names(DefaultMutableTreeNode parent) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            final Object userObject = ((DefaultMutableTreeNode) parent.getChildAt(i)).getUserObject();
            names.add(userObject instanceof final ServiceRow row ? StringUtil.getShortName(row.id()) : userObject.toString());
        }
        return names;
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
