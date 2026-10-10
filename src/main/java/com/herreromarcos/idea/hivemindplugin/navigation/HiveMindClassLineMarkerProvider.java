package com.herreromarcos.idea.hivemindplugin.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.ServiceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Gutter-Icon an Service-Interfaces und -Implementierungen → {@code <service-point>} im Moduldeskriptor. */
public class HiveMindClassLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof final PsiClass psiClass)) return;
        if (psiClass.getNameIdentifier() != element || psiClass.getQualifiedName() == null) return;
        final List<ServiceInfo> services = HiveMindRegistry.getInstance(element.getProject()).findServicesByClass(psiClass.getQualifiedName());
        if (services.isEmpty()) return;
        result.add(NavigationGutterIconBuilder.create(HiveMindIcons.SERVICE)
                .setTargets(services.stream().map(service -> service.point().tag()).toList())
                .setTooltipText("HiveMind service: %s".formatted(services.stream().map(ServiceInfo::id).collect(Collectors.joining(", "))))
                .setPopupTitle("HiveMind Services")
                .createLineMarkerInfo(element));
    }
}
