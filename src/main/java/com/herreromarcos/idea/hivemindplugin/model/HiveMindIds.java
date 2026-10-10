package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.hivemind.util.IdUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Regeln für ids und Klassennamen in HiveMind; die Qualifizierung übernimmt {@link IdUtils} aus HiveMind selbst. */
public class HiveMindIds {
    private HiveMindIds() {
    }

    /**
     * Unqualifizierte ids ("Mailer") gehören zum eigenen Modul ("app.Mailer"); ids mit Punkt sind bereits qualifiziert.
     * Regel von HiveMind selbst ({@link IdUtils#qualify}); ohne Modul (Java, Tapestry) bleibt die id unverändert.
     */
    public static @NotNull String qualify(@Nullable String moduleId, @NotNull String id) {
        final String trimmed = id.trim();
        return moduleId == null ? trimmed : IdUtils.qualify(moduleId, trimmed);
    }

    /** Wie {@link #qualify(String, String)} im Kontext eines Moduls; {@code null} = außerhalb der Deskriptoren. */
    public static @NotNull String qualify(@Nullable HiveModule module, @NotNull String id) {
        return qualify(module != null ? module.id() : null, id);
    }

    public static @NotNull String unqualify(@NotNull String moduleId, @NotNull String qualifiedId) {
        final String prefix = moduleId + ".";
        return qualifiedId.startsWith(prefix) ? qualifiedId.substring(prefix.length()) : qualifiedId;
    }

    /** HiveMind 1.1 erlaubt Initialisierer hinter dem Klassennamen: {@code "MailerImpl,host=localhost"} → "MailerImpl". */
    public static @NotNull String stripInitializer(@NotNull String className) {
        final int comma = className.indexOf(',');
        return comma < 0 ? className : className.substring(0, comma);
    }

    /** {@code "java.lang.Object[][]"} → {@code "java.lang.Object"} (Leerraum bleibt erhalten, damit Offsets stimmen). */
    public static @NotNull String stripArrayBrackets(@NotNull String className) {
        String result = className.stripTrailing();
        while (result.endsWith("[]")) result = result.substring(0, result.length() - 2).stripTrailing();
        return result;
    }

    /**
     * Regel für {@code service-point@interface}: Namen ohne Punkt bekommen das Modulpaket vorangestellt, Namen mit Punkt
     * bleiben unverändert. Alle anderen Klassennamen löst {@link HiveMindClasses} auf.
     */
    public static @NotNull String qualifyClass(@Nullable String packageName, @NotNull String className) {
        final String trimmed = stripInitializer(className).trim();
        return StringUtil.isEmpty(packageName) || trimmed.contains(".") ? trimmed : "%s.%s".formatted(packageName, trimmed);
    }
}
