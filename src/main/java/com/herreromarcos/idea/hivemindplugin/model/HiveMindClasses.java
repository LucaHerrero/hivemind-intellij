package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Auflösung von Klassennamen wie HiveMind 1.1 (Doku "module", "service-point"):
 * <ul>
 *   <li>allgemein: zuerst der Name wie angegeben, und erst wenn es diese Klasse nicht gibt, mit vorangestelltem
 *       Modulpaket – das gilt auch für Namen mit Punkt ({@code impl.AdderImpl})</li>
 *   <li>{@code service-point@interface}: nur Namen <em>ohne</em> Punkt bekommen das Modulpaket vorangestellt</li>
 * </ul>
 * Ein Initialisierer ({@code "MailerImpl,host=localhost"}) wird vorher abgeschnitten.
 */
public class HiveMindClasses {
    private HiveMindClasses() {
    }

    public static @Nullable PsiClass resolve(@NotNull Project project, @NotNull GlobalSearchScope scope,
                                             @Nullable String packageName, @NotNull String className) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        for (final String candidate : candidates(packageName, className)) {
            final PsiClass psiClass = facade.findClass(candidate, scope);
            if (psiClass != null) return psiClass;
        }
        return null;
    }

    /** Vollqualifizierter Name der aufgelösten Klasse, sonst der wahrscheinlichste Kandidat. */
    public static @NotNull String qualifiedName(@NotNull Project project, @Nullable String packageName,
                                                @NotNull String className) {
        final PsiClass psiClass = resolve(project, GlobalSearchScope.allScope(project), packageName, className);
        if (psiClass != null && psiClass.getQualifiedName() != null) return psiClass.getQualifiedName();
        final String[] candidates = candidates(packageName, className);
        return candidates[candidates.length - 1];
    }

    /** Suchreihenfolge: wie angegeben, dann im Modulpaket. */
    static @NotNull String[] candidates(@Nullable String packageName, @NotNull String className) {
        final String name = HiveMindIds.stripArrayBrackets(HiveMindIds.stripInitializer(className)).trim();
        if (packageName == null || packageName.isEmpty()) return new String[]{name};
        return new String[]{name, "%s.%s".formatted(packageName, name)};
    }
}
