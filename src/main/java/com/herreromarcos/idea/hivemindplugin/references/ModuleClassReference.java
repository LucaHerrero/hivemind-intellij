package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindClasses;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindIds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Klassenname, der (auch) relativ zum Modulpaket gemeint sein kann, z.B. {@code class="impl.AdderImpl"} in
 * {@code <module package="hivemind.examples">}. Auflösung siehe {@link HiveMindClasses}; für
 * {@code service-point@interface} gilt die Sonderregel aus {@link HiveMindIds#qualifyClass}.
 */
public class ModuleClassReference extends PsiReferenceBase<PsiElement> implements HiveMindReference {
    private final String packageName;
    private final boolean interfaceRule;
    private final @Nullable HighlightSeverity severity;

    public ModuleClassReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull String packageName,
                                boolean interfaceRule, @Nullable HighlightSeverity severity) {
        super(element, range, true);
        this.packageName = packageName;
        this.interfaceRule = interfaceRule;
        this.severity = severity;
    }

    @Override
    public @Nullable PsiElement resolve() {
        if (interfaceRule) {
            return JavaPsiFacade.getInstance(getElement().getProject())
                    .findClass(HiveMindIds.qualifyClass(packageName, getValue()), getElement().getResolveScope());
        }
        return HiveMindClasses.resolve(getElement().getProject(), getElement().getResolveScope(), packageName, getValue());
    }

    /** Klassen des Modulpakets (Kurzname) – vollqualifizierte Namen bietet die Java-Completion ohnehin an. */
    @Override
    public Object @NotNull [] getVariants() {
        final PsiPackage psiPackage = JavaPsiFacade.getInstance(getElement().getProject()).findPackage(packageName);
        if (psiPackage == null) return EMPTY_ARRAY;
        return Arrays.stream(psiPackage.getClasses(getElement().getResolveScope()))
                .filter(psiClass -> psiClass.getName() != null)
                .map(psiClass -> LookupElementBuilder.create(psiClass, psiClass.getName()).withIcon(psiClass.getIcon(0)))
                .toArray();
    }

    /** Verschieben/Umbenennen der Klasse: im Modulpaket bleibt der Name kurz, sonst wird er vollqualifiziert. */
    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (element instanceof final PsiClass psiClass && psiClass.getQualifiedName() != null) {
            final String qualifiedName = psiClass.getQualifiedName();
            final String prefix = packageName + ".";
            final String relative = qualifiedName.startsWith(prefix) ? qualifiedName.substring(prefix.length()) : null;
            final boolean shortForm = relative != null && (!interfaceRule || !relative.contains("."));
            return handleElementRename(shortForm ? relative : qualifiedName);
        }
        return super.bindToElement(element);
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return severity;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final String name = getValue().trim();
        return StringUtil.isEmpty(packageName) ? "Cannot resolve class '%s'".formatted(name)
                : "Cannot resolve class '%s' (also searched in module package '%s')".formatted(name, packageName);
    }
}
