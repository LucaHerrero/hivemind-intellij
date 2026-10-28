package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baumansicht der Services nach den Paketen in ihren ids: {@code hivemind.lib.ChainFactory} → hivemind → lib →
 * ChainFactory. Pakete stehen vor den Services; Pakete, die nur ein Unterpaket enthalten, werden wie im Projektfenster
 * zusammengefasst ({@code com.example.app}).
 */
final class ServiceTree {
    /** Pakete zuerst, dann nach Name ohne Groß-/Kleinschreibung. */
    private static final Comparator<DefaultMutableTreeNode> ORDER = Comparator
            .comparing((DefaultMutableTreeNode node) -> !(node.getUserObject() instanceof ServicePackage))
            .thenComparing(ServiceTree::nameOf, String.CASE_INSENSITIVE_ORDER);

    private ServiceTree() {
    }

    /**
     * Paketknoten.
     *
     * @param qualifiedName Präfix der Service-ids in diesem Paket
     * @param services      Anzahl der Services darin, auch in Unterpaketen
     * @param library       alle Services stammen aus Bibliotheken
     */
    record ServicePackage(@NotNull String name, @NotNull String qualifiedName, int services, boolean library) {
        @Override
        public @NotNull String toString() {
            return name;
        }
    }

    /** Baum der Services; die Wurzel selbst wird nicht angezeigt. */
    static @NotNull DefaultMutableTreeNode build(@NotNull List<ServiceRow> rows) {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        final Map<String, DefaultMutableTreeNode> packages = new HashMap<>();
        for (final ServiceRow row : rows) {
            final int dot = row.id().lastIndexOf('.');
            final DefaultMutableTreeNode parent = dot > 0 ? packageNode(row.id().substring(0, dot), root, packages) : root;
            parent.add(new DefaultMutableTreeNode(row, false));
        }
        arrange(root);
        return root;
    }

    /** Service des Knotens oder {@code null} bei Paketen. */
    static @Nullable ServiceRow rowOf(@Nullable Object node) {
        return TreeUtil.getUserObject(ServiceRow.class, node);
    }

    /** Pfad zum Service mit der id oder {@code null}. */
    static @Nullable TreePath findService(@NotNull DefaultMutableTreeNode root, @NotNull String id) {
        final DefaultMutableTreeNode node = TreeUtil.findNode(root, candidate -> {
            final ServiceRow row = rowOf(candidate);
            return row != null && row.id().equals(id);
        });
        return node != null ? new TreePath(node.getPath()) : null;
    }

    private static DefaultMutableTreeNode packageNode(String qualifiedName, DefaultMutableTreeNode root,
                                                      Map<String, DefaultMutableTreeNode> packages) {
        final DefaultMutableTreeNode existing = packages.get(qualifiedName);
        if (existing != null) return existing;
        final int dot = qualifiedName.lastIndexOf('.');
        final DefaultMutableTreeNode parent = dot > 0 ? packageNode(qualifiedName.substring(0, dot), root, packages) : root;
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new ServicePackage(qualifiedName.substring(dot + 1), qualifiedName, 0, false));
        parent.add(node);
        packages.put(qualifiedName, node);
        return node;
    }

    /** Fasst Paketketten zusammen, sortiert die Kinder und trägt Anzahl und Herkunft der Services in die Pakete ein. */
    private static void arrange(DefaultMutableTreeNode node) {
        final List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) children.add(compact((DefaultMutableTreeNode) node.getChildAt(i)));
        children.sort(ORDER);
        node.removeAllChildren();
        int services = 0;
        boolean library = true;
        for (final DefaultMutableTreeNode child : children) {
            node.add(child);
            if (child.getUserObject() instanceof ServicePackage) arrange(child);
            if (child.getUserObject() instanceof final ServicePackage childPackage) {
                services += childPackage.services();
                library &= childPackage.library();
            } else if (child.getUserObject() instanceof final ServiceRow row) {
                services++;
                library &= row.library();
            }
        }
        if (node.getUserObject() instanceof final ServicePackage servicePackage) {
            node.setUserObject(new ServicePackage(servicePackage.name(), servicePackage.qualifiedName(), services, library));
        }
    }

    /** Paket ohne eigene Services mit genau einem Unterpaket → ein Knoten "outer.inner". */
    private static DefaultMutableTreeNode compact(DefaultMutableTreeNode node) {
        while (node.getUserObject() instanceof final ServicePackage outer && node.getChildCount() == 1
                && ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject() instanceof final ServicePackage inner) {
            node = (DefaultMutableTreeNode) node.getFirstChild();
            node.setUserObject(new ServicePackage(outer.name() + "." + inner.name(), inner.qualifiedName(), 0, false));
        }
        return node;
    }

    private static String nameOf(DefaultMutableTreeNode node) {
        final ServiceRow row = rowOf(node);
        return row != null ? StringUtil.getShortName(row.id()) : String.valueOf(node.getUserObject());
    }
}
