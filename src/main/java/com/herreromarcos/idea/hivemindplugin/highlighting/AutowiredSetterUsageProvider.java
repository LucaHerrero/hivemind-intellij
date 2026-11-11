package com.herreromarcos.idea.hivemindplugin.highlighting;

import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * Setter, die HiveMind per Autowiring aufruft, sind benutzt – kein "Method is never used".
 *
 * <p>Die BuilderFactory von HiveMind 1.1 belegt jede schreibbare Property der erzeugten Instanz, die nicht schon per
 * {@code <set-…>} gesetzt wird, mit dem Service ihres Typs ({@code BuilderFactoryLogic.autowireProperty}). Voraussetzung:
 * {@code autowire-services} ist nicht {@code false} und vom Modul der Konstruktion aus ist genau ein Service mit diesem
 * Interface sichtbar. Gibt es keinen solchen Service, ruft auch HiveMind den Setter nicht auf – er bleibt unbenutzt.
 */
public class AutowiredSetterUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return element instanceof final PsiMethod method && isAutowiredSetter(method);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    /** Bean-Setter, wie ihn {@link java.beans.Introspector} findet: öffentlich, nicht statisch, {@code void setXxx(T)}. */
    static boolean isAutowiredSetter(@NotNull PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.STATIC)) return false;
        if (!PropertyUtilBase.isSimplePropertySetter(method) || !PsiTypes.voidType().equals(method.getReturnType())) return false;
        final PsiClass containingClass = method.getContainingClass();
        final String className = containingClass != null ? containingClass.getQualifiedName() : null;
        if (className == null) return false;
        if (!(method.getParameterList().getParameters()[0].getType() instanceof final PsiClassType type)) return false;
        final PsiClass typeClass = type.resolve();
        final String typeName = typeClass != null ? typeClass.getQualifiedName() : null;
        if (typeName == null) return false;

        final HiveMindRegistry registry = HiveMindRegistry.getInstance(method.getProject());
        // zuerst die billige Prüfung: ist der Typ überhaupt das Interface eines Services?
        if (registry.findServicesByInterface(typeName).isEmpty()) return false;
        return registry.findAutowiringConstructions(className).stream()
                .anyMatch(construction -> registry.autowiresService(construction, typeName));
    }
}
