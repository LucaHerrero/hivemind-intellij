package com.herreromarcos.idea.hivemindplugin.model;

import com.herreromarcos.idea.hivemindplugin.HiveMindConstants;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Konstruktion eines Services: {@code <create-instance class="...">} oder {@code <invoke-factory>}.
 *
 * @param tag       das create-instance- bzw. invoke-factory-Tag (direkt im service-point oder in einem {@code <implementation>})
 * @param module    das Modul, in dem die Konstruktion steht (für die Paketauflösung von Klassennamen)
 * @param className vollqualifizierte Implementierungsklasse; bei Factories nur bekannt für {@code <construct class="...">}
 * @param factoryId Service-id der Factory oder {@code null} bei create-instance
 */
public record ServiceImplementation(@NotNull XmlTag tag, @NotNull HiveModule module, @Nullable String className,
                                    @Nullable String factoryId, @NotNull String model) {

    /**
     * Implementierung direkt im {@code <service-point>} = Standardimplementierung; eine aus {@code <implementation>}
     * überschreibt sie (HiveMind 1.1, Doku "Overriding a Service").
     */
    public boolean isDefault() {
        final XmlTag parent = tag.getParentTag();
        return parent != null && HiveMindConstants.TAG_SERVICE_POINT.equals(parent.getName());
    }

    /** Bedingung {@code if="..."} des umgebenden {@code <implementation>}, sonst {@code null}. */
    public @Nullable String condition() {
        final XmlTag parent = tag.getParentTag();
        return parent != null && !isDefault() ? HiveMindRegistry.attr(parent, HiveMindConstants.ATTR_IF) : null;
    }

    /** Kurzbeschreibung für die Anzeige: Klasse, sonst die Factory. */
    public @NotNull String describe() {
        if (className != null) return className;
        return factoryId != null ? "<%s>".formatted(factoryId) : "?";
    }
}
