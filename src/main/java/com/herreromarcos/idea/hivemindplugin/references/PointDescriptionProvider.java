package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Texte für Find Usages / Rename: "service point app.Mailer". */
public class PointDescriptionProvider extends PomDescriptionProvider {

    @Override
    public @Nullable String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
        if (!(element instanceof final PointTarget target) || !target.isValid()) return null;
        if (location instanceof UsageViewTypeLocation) return target.getKind().lowerCaseName();
        if (location instanceof UsageViewLongNameLocation) {
            final HiveModule module = HiveMindRegistry.moduleOf(target.getTag());
            return module != null ? module.pointId(target.getName()) : target.getName();
        }
        return target.getName();
    }
}
