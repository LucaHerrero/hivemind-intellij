package com.herreromarcos.idea.hivemindplugin.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/** Die drei Arten von Erweiterungspunkten, die über eine (modulweit eindeutige) id referenziert werden. */
public enum PointKind {
    SERVICE(TAG_SERVICE_POINT, "Service point"),
    CONFIGURATION(TAG_CONFIGURATION_POINT, "Configuration point"),
    SCHEMA(TAG_SCHEMA, "Schema");

    private final String tagName;
    private final String displayName;

    PointKind(@NotNull String tagName, @NotNull String displayName) {
        this.tagName = tagName;
        this.displayName = displayName;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    /** Anzeigename im Satz, z.B. "service point". */
    public @NotNull String lowerCaseName() {
        return displayName.toLowerCase(Locale.ROOT);
    }

    /** Art zum Tag ({@code service-point} …) oder {@code null}. */
    public static @Nullable PointKind byTagName(@NotNull String tagName) {
        for (final PointKind kind : values()) {
            if (kind.tagName.equals(tagName)) return kind;
        }
        return null;
    }
}
