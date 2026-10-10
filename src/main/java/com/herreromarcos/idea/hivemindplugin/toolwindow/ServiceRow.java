package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.herreromarcos.idea.hivemindplugin.model.ServiceImplementation;
import com.herreromarcos.idea.hivemindplugin.model.ServiceInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Unveränderliche Momentaufnahme eines Services für die Tabelle – wird im Hintergrund unter Read-Lock erzeugt,
 * die PSI-Elemente nur als Smart-Pointer gehalten.
 *
 * @param implementationClass erste Implementierungsklasse (vollqualifiziert) oder {@code null} bei unbekannter Factory-Ausgabe
 * @param implementations     Beschreibung jeder Konstruktion (create-instance/invoke-factory)
 */
record ServiceRow(@NotNull String id, @NotNull String moduleId, @NotNull String interfaceName,
                  @Nullable String implementationClass, @Nullable String factoryId, @NotNull String model,
                  boolean privateVisibility, boolean library, @NotNull String location,
                  @NotNull List<String> implementations, @NotNull List<String> interceptors,
                  @NotNull SmartPsiElementPointer<XmlTag> declaration,
                  @NotNull List<SmartPsiElementPointer<XmlTag>> implementationTags) {

    static @NotNull ServiceRow create(@NotNull ServiceInfo service) {
        final SmartPointerManager pointers = SmartPointerManager.getInstance(service.point().tag().getProject());
        final ServiceImplementation primary = service.primaryImplementation();
        return new ServiceRow(
                service.id(),
                service.point().module().id(),
                service.interfaceName(),
                primary != null ? primary.className() : null,
                primary != null ? primary.factoryId() : null,
                service.model(),
                service.point().isPrivate(),
                !service.point().module().inProject(),
                service.point().module().location(),
                service.implementations().stream().map(implementation -> describe(implementation, primary)).toList(),
                service.interceptors(),
                pointers.createSmartPsiElementPointer(service.point().tag()),
                service.implementations().stream().map(implementation -> pointers.createSmartPsiElementPointer(implementation.tag())).toList());
    }

    private static String describe(ServiceImplementation implementation, @Nullable ServiceImplementation effective) {
        final String how = implementation.factoryId() != null ? "via %s".formatted(implementation.factoryId()) : "create-instance";
        final String role = implementation.isDefault() ? (implementation == effective ? "default" : "default, overridden") : "override";
        final String condition = implementation.condition() != null ? ", if %s".formatted(implementation.condition()) : "";
        return "%s (%s, %s, %s%s) – module %s".formatted(implementation.describe(), how, implementation.model(), role, condition,
                implementation.module().id());
    }

    /** Text für die Spalte "Implementation": Klassenname, sonst die Factory, sonst Hinweis auf die fehlende Implementierung. */
    @NotNull String implementationText() {
        if (implementationClass != null) return StringUtil.getShortName(implementationClass);
        if (factoryId != null) return "<%s>".formatted(factoryId);
        return "";
    }

    boolean hasImplementation() {
        return !implementations.isEmpty();
    }

    /** Filter: Teilstring (ohne Groß-/Kleinschreibung) in id, Interface, Implementierung oder Modul. */
    boolean matches(@NotNull String filter) {
        if (filter.isBlank()) return true;
        final String needle = filter.trim().toLowerCase(Locale.ROOT);
        return contains(id, needle) || contains(interfaceName, needle) || contains(implementationClass, needle)
                || contains(factoryId, needle) || contains(moduleId, needle);
    }

    private static boolean contains(@Nullable String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
