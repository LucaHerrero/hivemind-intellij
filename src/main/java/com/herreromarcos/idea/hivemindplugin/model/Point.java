package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Service-Point, Configuration-Point oder Schema.
 *
 * @param id vollqualifizierte id, z.B. "hivemind.BuilderFactory"
 */
public record Point(@NotNull PointKind kind, @NotNull String id, @NotNull HiveModule module, @NotNull XmlTag tag) {

    /** id ohne Modulpräfix, wie sie im Deskriptor steht. */
    public @NotNull String localId() {
        return HiveMindIds.unqualify(module.id(), id);
    }

    public boolean isPrivate() {
        return VISIBILITY_PRIVATE.equals(tag.getAttributeValue(ATTR_VISIBILITY));
    }

    /**
     * Ist der Punkt vom angegebenen Modul aus sichtbar? Private Punkte nur im eigenen Modul – von außerhalb jedes Moduls
     * ({@code from == null}, z.B. {@code Registry.getService(...)} im Java-Code) überhaupt nicht.
     */
    public boolean isVisibleFrom(@Nullable HiveModule from) {
        return !isPrivate() || from != null && module.id().equals(from.id());
    }
}
