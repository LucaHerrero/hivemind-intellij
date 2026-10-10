package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.util.TypeConversionUtil;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindIds;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.herreromarcos.idea.hivemindplugin.model.ObjectReference;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/** Erzeugt Referenzen für Klassennamen, Punkt-ids und Object-Provider-Ausdrücke an beliebigen Stellen (XML, Java). */
class HiveMindReferences {
    private HiveMindReferences() {
    }

    /**
     * Werte, die HiveMind erst zur Laufzeit ersetzt: Substitutionssymbole {@code ${config.dir}} (Doku "Configurations")
     * und lokalisierte Nachrichten {@code %message.key} (Doku "Localization"). Dafür gibt es keine Referenzen.
     */
    static boolean isDynamic(@NotNull String text) {
        return text.contains("${") || text.trim().startsWith("%");
    }

    /**
     * Referenz(en) auf einen Punkt; Leerraum um die id herum wird ignoriert.
     *
     * @param offset Beginn von {@code text} innerhalb des Elements
     */
    static PsiReference @NotNull [] point(@NotNull PsiElement element, @NotNull String text, int offset,
                                          @NotNull PointKind kind, @Nullable HiveModule module,
                                          @NotNull HighlightSeverity severity) {
        final TextRange range = trimmed(text, offset);
        if (range == null || isDynamic(text)) return PsiReference.EMPTY_ARRAY;
        return new PsiReference[]{new PointReference(element, range, kind, module, severity)};
    }

    /**
     * Klassenname. Im Kontext eines Moduls wird erst der Name wie angegeben, dann im Modulpaket gesucht; findet sich der
     * Name wie angegeben, liefern die Java-Referenzen der Plattform Navigation je Paketsegment.
     * Ein Initialisierer ({@code "MailerImpl,host=localhost"}) wird ignoriert.
     *
     * @param interfaceRule Sonderregel von {@code service-point@interface}
     * @param severity      Schweregrad, wenn die Klasse fehlt; {@code null} = nicht melden
     */
    static PsiReference @NotNull [] className(@NotNull PsiElement element, @NotNull String text, int offset,
                                              @Nullable HiveModule module, boolean interfaceRule,
                                              @Nullable HighlightSeverity severity) {
        // Arrays ("java.lang.Object[]") und primitive Typen ("int[]") löst der DefaultClassResolver von HiveMind selbst auf
        final String withoutInitializer = HiveMindIds.stripArrayBrackets(HiveMindIds.stripInitializer(text));
        final TextRange range = trimmed(withoutInitializer, offset);
        if (range == null || isDynamic(text)) return PsiReference.EMPTY_ARRAY;
        final String name = withoutInitializer.trim();
        if (TypeConversionUtil.isPrimitive(name)) return PsiReference.EMPTY_ARRAY;
        final boolean resolvesAsIs = name.contains(".")
                && JavaPsiFacade.getInstance(element.getProject()).findClass(name, element.getResolveScope()) != null;
        if (module != null && !resolvesAsIs) {
            return new PsiReference[]{new ModuleClassReference(element, range, module.packageName(), interfaceRule, severity)};
        }
        final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        provider.setSoft(severity == null);
        return provider.getReferencesByString(name, element, range.getStartOffset());
    }

    /**
     * Object-Referenz (Doku "hivemind.ObjectProviders"): {@code service:id}, {@code configuration:id},
     * {@code service-property:id:property}, {@code bean:BeanFactory-id:name}, {@code instance:Klasse},
     * {@code class:Klasse}. Andere Präfixe ({@code spring:}, {@code infrastructure:} …) bleiben ohne Referenz.
     */
    static PsiReference @NotNull [] objectExpression(@NotNull PsiElement element, @NotNull String text, int offset,
                                                     @Nullable HiveModule module, @NotNull HighlightSeverity severity) {
        final ObjectReference expression = ObjectReference.parse(text);
        if (expression == null || isDynamic(text)) return PsiReference.EMPTY_ARRAY;
        final int targetOffset = offset + expression.targetStart();
        return switch (expression.prefix()) {
            case PROVIDER_SERVICE, PROVIDER_SERVICE_PROPERTY, PROVIDER_BEAN ->
                    point(element, expression.target(), targetOffset, PointKind.SERVICE, module, severity);
            case PROVIDER_CONFIGURATION -> point(element, expression.target(), targetOffset, PointKind.CONFIGURATION, module, severity);
            case PROVIDER_INSTANCE, PROVIDER_CLASS -> className(element, expression.target(), targetOffset, module, false, severity);
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** Bereich des Textes ohne führenden/abschließenden Leerraum, {@code null} bei leerem Text. */
    static @Nullable TextRange trimmed(String text, int offset) {
        final int start = text.length() - text.stripLeading().length();
        final int end = text.stripTrailing().length();
        return start < end ? TextRange.create(offset + start, offset + end) : null;
    }
}
