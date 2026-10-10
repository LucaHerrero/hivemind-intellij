package com.herreromarcos.idea.hivemindplugin.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ausdruck eines HiveMind-Object-Providers, z.B. {@code service:app.Mailer}, {@code configuration:app.Settings},
 * {@code instance:MailerImpl} oder {@code service-property:app.Mailer:host}.
 *
 * @param prefix      Provider-Präfix ohne Doppelpunkt
 * @param targetStart Offset des referenzierten Namens im Ausdruck
 * @param target      referenzierter Name (Service-/Configuration-id bzw. Klassenname)
 */
public record ObjectReference(@NotNull String prefix, int targetStart, @NotNull String target) {

    public static @Nullable ObjectReference parse(@Nullable String expression) {
        if (expression == null) return null;
        final int colon = expression.indexOf(':');
        if (colon <= 0) return null;
        final String prefix = expression.substring(0, colon).trim();
        String target = expression.substring(colon + 1);
        // service-property:<service-id>:<property>
        final int second = target.indexOf(':');
        if (second >= 0) target = target.substring(0, second);
        return new ObjectReference(prefix, colon + 1, target);
    }
}
