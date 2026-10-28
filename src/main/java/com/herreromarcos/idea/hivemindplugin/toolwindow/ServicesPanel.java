package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.Alarm;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.toolwindow.ServiceTree.ServicePackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Inhalt des Toolwindows "HiveMind": alle Services ("Beans") aller Module als sortier- und filterbare Tabelle oder als
 * Baum nach den Paketen in ihren ids, darunter die Details des ausgewählten Services. Aktualisiert sich nach PSI- und
 * Classpath-Änderungen selbst.
 */
public class ServicesPanel extends SimpleToolWindowPanel implements Disposable {
    static final String PLACE = "HiveMindServices";
    private static final String SHOW_LIBRARIES_KEY = "hivemind.toolwindow.showLibraries";
    private static final String TREE_MODE_KEY = "hivemind.toolwindow.treeMode";
    private static final int REFRESH_DELAY_MS = 400;
    /** Links im Detailbereich. */
    private static final String LINK_DECLARATION = "decl";
    private static final String LINK_INTERFACE = "iface";
    private static final String LINK_IMPLEMENTATION = "impl:";

    private final Project project;
    private final ListTableModel<ServiceRow> model = new ListTableModel<>(createColumns());
    private final TableView<ServiceRow> table;
    private final ListTreeTableModelOnColumns treeModel = new ListTreeTableModelOnColumns(new DefaultMutableTreeNode(), createTreeColumns());
    private final TreeTableView treeTable;
    private final JScrollPane tableScrollPane;
    private final JScrollPane treeScrollPane;
    private final JBSplitter splitter = new JBSplitter(true, "hivemind.toolwindow.splitter", 0.7f);
    private final SearchTextField filterField = new SearchTextField(false);
    private final JEditorPane details = new JEditorPane();
    private final JBLabel status = new JBLabel();
    private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private List<ServiceRow> allRows = List.of();
    private List<ServiceRow> visibleRows = List.of();
    /** Aufgeklappte Pakete des ungefilterten Baums – nach dem Filtern wird dieser Zustand wiederhergestellt. */
    private Set<String> expandedPackages = Set.of();
    private boolean treeFiltered;

    public ServicesPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;
        final ListSelectionListener selectionListener = e -> {
            if (!e.getValueIsAdjusting()) showDetails(getSelectedRow());
        };

        model.setSortable(true);
        table = new TableView<>(model);
        table.setShowGrid(false);
        setEmptyText(table.getEmptyText());
        table.getSelectionModel().addListSelectionListener(selectionListener);
        tableScrollPane = ScrollPaneFactory.createScrollPane(table, true);

        treeTable = new TreeTableView(treeModel);
        treeTable.setRootVisible(false);
        treeTable.getTree().setShowsRootHandles(true);
        treeTable.setShowGrid(false);
        treeTable.setTreeCellRenderer(new ServiceTreeCellRenderer());
        setEmptyText(treeTable.getEmptyText());
        treeTable.getSelectionModel().addListSelectionListener(selectionListener);
        treeScrollPane = ScrollPaneFactory.createScrollPane(treeTable, true);

        details.setEditable(false);
        details.setEditorKit(HTMLEditorKitBuilder.simple());
        details.setBorder(JBUI.Borders.empty(6, 8));
        details.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
                openLink(e.getDescription());
            }
        });

        splitter.setFirstComponent(isTreeMode() ? treeScrollPane : tableScrollPane);
        splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(details, SideBorder.TOP));

        final JPanel content = new JPanel(new BorderLayout());
        content.add(splitter, BorderLayout.CENTER);
        status.setBorder(JBUI.Borders.empty(2, 8));
        status.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        content.add(status, BorderLayout.SOUTH);
        setContent(content);
        setToolbar(createToolbar());
        installNavigation();
        subscribeToChanges();
        refresh();
    }

    // ------------------------------------------------------------------ Aufbau der Oberfläche

    private static void setEmptyText(StatusText emptyText) {
        emptyText.setText("No HiveMind services found");
        emptyText.appendSecondaryText("Services are read from hivemodule.xml in the project and on the classpath",
                SimpleTextAttributes.GRAYED_ATTRIBUTES, null);
    }

    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new DumbAwareAction("Refresh", "Reload services from all module descriptors", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refresh();
            }
        });
        group.add(new DumbAwareToggleAction("Show Library Modules", "Also list services from JARs (e.g. hivemind, tapestry)",
                AllIcons.Nodes.PpLibFolder) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return isShowLibraries();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                PropertiesComponent.getInstance(project).setValue(SHOW_LIBRARIES_KEY, state, true);
                applyFilter();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        group.add(new DumbAwareToggleAction("Show as Tree", "Group services by the packages in their ids",
                AllIcons.Actions.ShowAsTree) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return isTreeMode();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                setTreeMode(state);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(PLACE, group, true);
        toolbar.setTargetComponent(this);

        filterField.getTextEditor().getEmptyText().setText("Filter by id, interface, class or module");
        filterField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                applyFilter();
            }
        });

        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);
        panel.add(filterField, BorderLayout.CENTER);
        panel.setBorder(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.borderColor()));
        return panel;
    }

    /** Doppelklick, Enter und F4 → Deklaration; Kontextmenü mit Interface/Implementierung/id kopieren. */
    private void installNavigation() {
        final AnAction declaration = new RowAction("Jump to Declaration", "Open the <service-point> in the module descriptor",
                AllIcons.Actions.EditSource, row -> navigate(row.declaration()));
        final AnAction serviceInterface = new RowAction("Go to Interface", null, AllIcons.Nodes.Interface,
                row -> navigateToClass(row.interfaceName()));
        final AnAction implementation = new RowAction("Go to Implementation", null, AllIcons.Nodes.Class, row -> {
            if (row.implementationClass() != null && navigateToClass(row.implementationClass())) return true;
            return !row.implementationTags().isEmpty() && navigate(row.implementationTags().getFirst());
        });
        final AnAction copyId = new RowAction("Copy Service ID", null, AllIcons.Actions.Copy, row -> {
            CopyPasteManager.getInstance().setContents(new StringSelection(row.id()));
            return true;
        });

        final ShortcutSet shortcuts = new CustomShortcutSet(
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), null));
        final ActionGroup popup = new DefaultActionGroup(declaration, serviceInterface, implementation, Separator.getInstance(), copyId);
        // im Baum sind Pakete keine Services: Enter und Doppelklick bleiben dort beim Auf-/Zuklappen
        for (final JComponent view : List.of(table, treeTable)) {
            declaration.registerCustomShortcutSet(shortcuts, view);
            new DoubleClickListener() {
                @Override
                protected boolean onDoubleClick(@NotNull MouseEvent event) {
                    final ServiceRow row = getSelectedRow();
                    return row != null && navigate(row.declaration());
                }
            }.installOn(view);
            PopupHandler.installPopupMenu(view, popup, PLACE);
        }
    }

    private void subscribeToChanges() {
        final var connection = project.getMessageBus().connect(this);
        connection.subscribe(PsiModificationTracker.TOPIC, (PsiModificationTracker.Listener) this::scheduleRefresh);
        connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
            @Override
            public void rootsChanged(@NotNull ModuleRootEvent event) {
                scheduleRefresh();
            }
        });
        connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
            @Override
            public void exitDumbMode() {
                scheduleRefresh();
            }
        });
    }

    // ------------------------------------------------------------------ Daten laden und anzeigen

    private void scheduleRefresh() {
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(this::refresh, REFRESH_DELAY_MS);
    }

    /** Services im Hintergrund einsammeln (nach der Indizierung), dann auf dem EDT anzeigen. */
    void refresh() {
        ReadAction.nonBlocking(this::collectRows)
                .inSmartMode(project)
                .expireWith(this)
                .coalesceBy(this)
                .finishOnUiThread(ModalityState.any(), rows -> {
                    allRows = rows;
                    applyFilter();
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private List<ServiceRow> collectRows() {
        return HiveMindRegistry.getInstance(project).getServices().stream().map(ServiceRow::create).toList();
    }

    private void applyFilter() {
        applyFilter(getSelectedRow());
    }

    /** @param selected bleibt ausgewählt, wenn er weiterhin angezeigt wird */
    private void applyFilter(@Nullable ServiceRow selected) {
        final boolean showLibraries = isShowLibraries();
        final String filter = filterField.getText();
        final List<ServiceRow> visible = new ArrayList<>();
        for (final ServiceRow row : allRows) {
            if ((showLibraries || !row.library()) && row.matches(filter)) visible.add(row);
        }
        visibleRows = visible;
        if (isTreeMode()) showTree(visible, selected, filter);
        else showTable(visible, selected);
        showDetails(getSelectedRow());
        updateStatus(visible);
    }

    private void showTable(List<ServiceRow> visible, @Nullable ServiceRow selected) {
        model.setItems(visible);
        if (selected != null) {
            visible.stream().filter(row -> row.id().equals(selected.id())).findFirst()
                    .ifPresent(row -> table.setSelection(List.of(row)));
        }
    }

    /**
     * Baut den Baum neu auf. Beim Filtern ist alles aufgeklappt; ohne Filter gelten die zuletzt ungefiltert
     * aufgeklappten Pakete – auch wenn ein Paket inzwischen anders zusammengefasst ist ("tapestry" ↔ "tapestry.globals").
     */
    private void showTree(List<ServiceRow> visible, @Nullable ServiceRow selected, String filter) {
        final JTree tree = treeTable.getTree();
        if (!treeFiltered) {
            expandedPackages = new HashSet<>(TreeUtil.collectExpandedObjects(tree, path -> {
                final ServicePackage servicePackage = TreeUtil.getLastUserObject(ServicePackage.class, path);
                return servicePackage != null ? servicePackage.qualifiedName() : null;
            }));
            expandedPackages.remove(null);
        }
        treeFiltered = !filter.isBlank();

        final DefaultMutableTreeNode root = ServiceTree.build(visible);
        treeModel.setRoot(root);
        tree.expandPath(new TreePath(root));
        for (final TreeNode node : Collections.list(root.breadthFirstEnumeration())) {
            final ServicePackage servicePackage = TreeUtil.getUserObject(ServicePackage.class, node);
            if (servicePackage != null && (treeFiltered || wasExpanded(servicePackage))) {
                tree.expandPath(new TreePath(((DefaultMutableTreeNode) node).getPath()));
            }
        }
        final TreePath path = selected != null ? ServiceTree.findService(root, selected.id()) : null;
        if (path != null) {
            tree.expandPath(path.getParentPath());
            treeTable.addSelectedPath(path);
        }
    }

    private boolean wasExpanded(ServicePackage servicePackage) {
        final String prefix = servicePackage.qualifiedName() + ".";
        return expandedPackages.stream().anyMatch(name -> name.equals(servicePackage.qualifiedName()) || name.startsWith(prefix));
    }

    private void updateStatus(List<ServiceRow> visible) {
        final long modules = visible.stream().map(ServiceRow::moduleId).distinct().count();
        final long hiddenLibraryServices = isShowLibraries() ? 0 : allRows.stream().filter(ServiceRow::library).count();
        String text = "%d %s in %d %s".formatted(visible.size(), StringUtil.pluralize("service", visible.size()),
                modules, StringUtil.pluralize("module", (int) modules));
        if (hiddenLibraryServices > 0) text += " · %d library services hidden".formatted(hiddenLibraryServices);
        status.setText(text);
    }

    private boolean isShowLibraries() {
        return PropertiesComponent.getInstance(project).getBoolean(SHOW_LIBRARIES_KEY, true);
    }

    private boolean isTreeMode() {
        return PropertiesComponent.getInstance(project).getBoolean(TREE_MODE_KEY, false);
    }

    /** Wechselt zwischen Tabelle und Baum; die Auswahl wird übernommen. */
    void setTreeMode(boolean treeMode) {
        if (treeMode == isTreeMode()) return;
        final ServiceRow selected = getSelectedRow();
        PropertiesComponent.getInstance(project).setValue(TREE_MODE_KEY, treeMode, false);
        splitter.setFirstComponent(treeMode ? treeScrollPane : tableScrollPane);
        applyFilter(selected);
    }

    /** Ausgewählter Service der aktiven Ansicht; im Baum {@code null}, wenn ein Paket ausgewählt ist. */
    private @Nullable ServiceRow getSelectedRow() {
        if (!isTreeMode()) return table.getSelectedObject();
        final List<?> selection = treeTable.getSelection();
        return selection.isEmpty() ? null : ServiceTree.rowOf(selection.getFirst());
    }

    private void showDetails(@Nullable ServiceRow row) {
        if (row == null) {
            details.setText("<html><body><i>Select a service to see its details.</i></body></html>");
            return;
        }
        final StringBuilder html = new StringBuilder("<html><body>");
        html.append("<b>%s</b>".formatted(escape(row.id())));
        if (row.privateVisibility()) html.append(" &nbsp;<i>(private)</i>");
        html.append(" &nbsp;<a href='%s'>Jump to declaration</a><br><br><table cellpadding='1'>".formatted(LINK_DECLARATION));
        row("Interface", "<a href='%s'>%s</a>".formatted(LINK_INTERFACE, escape(row.interfaceName())), html);
        if (row.implementations().isEmpty()) {
            row("Implementation", "<i>none – the service cannot be constructed</i>", html);
        }
        for (int i = 0; i < row.implementations().size(); i++) {
            row(i == 0 ? "Implementation" : "", "<a href='%s%d'>%s</a>".formatted(LINK_IMPLEMENTATION, i, escape(row.implementations().get(i))), html);
        }
        row("Model", escape(row.model()), html);
        if (!row.interceptors().isEmpty()) row("Interceptors", escape(String.join(", ", row.interceptors())), html);
        row("Module", escape(row.moduleId()), html);
        row("Defined in", escape(row.location()), html);
        html.append("</table></body></html>");
        details.setText(html.toString());
        details.setCaretPosition(0);
    }

    private static void row(String label, String valueHtml, StringBuilder html) {
        html.append("<tr><td valign='top'><font color='gray'>%s</font></td><td>%s</td></tr>".formatted(label, valueHtml));
    }

    private static String escape(String text) {
        return StringUtil.escapeXmlEntities(text);
    }

    private void openLink(String link) {
        final ServiceRow row = getSelectedRow();
        if (row == null) return;
        if (LINK_DECLARATION.equals(link)) navigate(row.declaration());
        else if (LINK_INTERFACE.equals(link)) navigateToClass(row.interfaceName());
        else if (link.startsWith(LINK_IMPLEMENTATION)) {
            final int index = Integer.parseInt(link.substring(LINK_IMPLEMENTATION.length()));
            if (index < row.implementationTags().size()) navigate(row.implementationTags().get(index));
        }
    }

    // ------------------------------------------------------------------ Navigation

    private static boolean navigate(SmartPsiElementPointer<XmlTag> pointer) {
        return navigate(pointer.getElement());
    }

    private static boolean navigate(@Nullable PsiElement element) {
        if (element == null || !element.isValid()) return false;
        PsiNavigateUtil.navigate(element);
        return true;
    }

    private boolean navigateToClass(String qualifiedName) {
        final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
        return navigate(psiClass);
    }

    @Override
    public void dispose() {
    }

    // ------------------------------------------------------------------ für Tests

    /** Angezeigte Services der aktiven Ansicht (im Baum in der Reihenfolge des Filters, nicht des Baums). */
    @NotNull List<ServiceRow> getVisibleRows() {
        return visibleRows;
    }

    void setFilter(@NotNull String filter) {
        filterField.setText(filter);
    }

    @NotNull JTree getTree() {
        return treeTable.getTree();
    }

    @NotNull DefaultMutableTreeNode getTreeRoot() {
        return (DefaultMutableTreeNode) treeModel.getRoot();
    }

    /** Aktion auf der ausgewählten Zeile; das Prädikat liefert, ob etwas passiert ist. */
    private class RowAction extends DumbAwareAction {
        private final Predicate<ServiceRow> perform;

        RowAction(String text, @Nullable String description, Icon icon, Predicate<ServiceRow> perform) {
            super(text, description, icon);
            this.perform = perform;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(getSelectedRow() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            final ServiceRow row = getSelectedRow();
            if (row != null) perform.test(row);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    // ------------------------------------------------------------------ Spalten

    private static TextColumn[] createColumns() {
        return new TextColumn[]{
                new TextColumn("Service ID", ServiceRow::id) {
                    @Override
                    void render(ColoredTableCellRenderer renderer, ServiceRow row, SimpleTextAttributes attributes) {
                        renderer.setIcon(HiveMindIcons.SERVICE);
                        renderer.append(row.id(), attributes);
                        if (row.privateVisibility()) renderer.append("  private", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                    }
                },
                new TextColumn("Interface", row -> StringUtil.getShortName(row.interfaceName())) {
                    @Override
                    @Nullable String cellTooltip(ServiceRow row) {
                        return row.interfaceName();
                    }
                },
                new TextColumn("Implementation", ServiceRow::implementationText) {
                    @Override
                    @Nullable String cellTooltip(ServiceRow row) {
                        return row.implementationClass();
                    }

                    @Override
                    void render(ColoredTableCellRenderer renderer, ServiceRow row, SimpleTextAttributes attributes) {
                        if (row.hasImplementation()) renderer.append(row.implementationText(), attributes);
                        else renderer.append("missing", SimpleTextAttributes.ERROR_ATTRIBUTES);
                    }
                },
                new TextColumn("Model", ServiceRow::model),
                new TextColumn("Module", ServiceRow::moduleId),
                new TextColumn("Source", row -> row.library() ? StringUtil.substringBefore(row.location(), "!/") : "project") {
                    @Override
                    void render(ColoredTableCellRenderer renderer, ServiceRow row, SimpleTextAttributes attributes) {
                        renderer.setIcon(row.library() ? AllIcons.Nodes.PpJar : AllIcons.Nodes.Module);
                        renderer.append(StringUtil.notNullize(valueOf(row)), attributes);
                    }
                },
        };
    }

    /** Spalten des Baums: erst der Baum selbst, dann dieselben Spalten wie in der Tabelle. */
    private static ColumnInfo<?, ?>[] createTreeColumns() {
        final TextColumn[] columns = createColumns();
        final ColumnInfo<?, ?>[] result = new ColumnInfo<?, ?>[columns.length];
        result[0] = new TreeColumnInfo(columns[0].getName());
        for (int i = 1; i < columns.length; i++) result[i] = new NodeColumn(columns[i]);
        return result;
    }

    /** Spalte des Baums: Wert und Darstellung der Tabellenspalte bei Services, leer bei Paketen. */
    private static class NodeColumn extends ColumnInfo<Object, String> {
        private final TextColumn column;

        NodeColumn(TextColumn column) {
            super(column.getName());
            this.column = column;
        }

        @Override
        public @Nullable String valueOf(Object node) {
            final ServiceRow row = ServiceTree.rowOf(node);
            return row != null ? column.valueOf(row) : null;
        }

        @Override
        public @Nullable TableCellRenderer getRenderer(Object node) {
            final ServiceRow row = ServiceTree.rowOf(node);
            return row != null ? column.getRenderer(row) : null;
        }

        @Override
        public @Nullable String getTooltipText() {
            return column.getTooltipText();
        }
    }

    /** Erste Spalte des Baums: Pakete mit der Anzahl ihrer Services, Services mit einfachem Namen. */
    private static class ServiceTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int rowIndex, boolean hasFocus) {
            final ServicePackage servicePackage = TreeUtil.getUserObject(ServicePackage.class, value);
            if (servicePackage != null) {
                setIcon(AllIcons.Nodes.Package);
                append(servicePackage.name(), attributes(servicePackage.library()));
                append("  " + servicePackage.services(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                return;
            }
            final ServiceRow row = ServiceTree.rowOf(value);
            if (row == null) return;
            setIcon(HiveMindIcons.SERVICE);
            append(StringUtil.getShortName(row.id()), attributes(row.library()));
            if (row.privateVisibility()) append("  private", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            setToolTipText(row.id());
        }

        private static SimpleTextAttributes attributes(boolean library) {
            return library ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        }
    }

    /** Textspalte mit Sortierung; Services aus Bibliotheken werden grau dargestellt. */
    private static class TextColumn extends ColumnInfo<ServiceRow, String> {
        private final Function<ServiceRow, String> value;

        TextColumn(String name, Function<ServiceRow, String> value) {
            super(name);
            this.value = value;
        }

        @Override
        public @Nullable String valueOf(ServiceRow row) {
            return value.apply(row);
        }

        @Override
        public @Nullable Comparator<ServiceRow> getComparator() {
            return Comparator.comparing(row -> StringUtil.notNullize(valueOf(row)), String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public @Nullable String getTooltipText() {
            return getName();
        }

        @Override
        public @NotNull TableCellRenderer getRenderer(ServiceRow row) {
            return new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected,
                                                     boolean hasFocus, int rowIndex, int column) {
                    render(this, row, row.library() ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    setToolTipText(cellTooltip(row));
                }
            };
        }

        void render(ColoredTableCellRenderer renderer, ServiceRow row, SimpleTextAttributes attributes) {
            renderer.append(StringUtil.notNullize(valueOf(row)), attributes);
        }

        @Nullable String cellTooltip(ServiceRow row) {
            return null;
        }
    }
}
