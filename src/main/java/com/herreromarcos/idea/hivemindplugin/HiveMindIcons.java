package com.herreromarcos.idea.hivemindplugin;

import com.intellij.icons.AllIcons;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Icons der IntelliJ-Plattform (keine eigenen Grafiken). */
public class HiveMindIcons {
    public static final Icon MODULE = AllIcons.Nodes.Module;
    public static final Icon SERVICE = AllIcons.Nodes.Plugin;
    public static final Icon CONFIGURATION = AllIcons.FileTypes.Config;
    public static final Icon SCHEMA = AllIcons.FileTypes.XsdFile;

    private HiveMindIcons() {
    }

    public static @NotNull Icon forKind(@NotNull PointKind kind) {
        return switch (kind) {
            case SERVICE -> SERVICE;
            case CONFIGURATION -> CONFIGURATION;
            case SCHEMA -> SCHEMA;
        };
    }
}
