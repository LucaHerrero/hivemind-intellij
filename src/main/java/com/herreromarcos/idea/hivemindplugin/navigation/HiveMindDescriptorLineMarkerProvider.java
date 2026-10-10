package com.herreromarcos.idea.hivemindplugin.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlTagUtil;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.herreromarcos.idea.hivemindplugin.model.ServiceImplementation;
import com.herreromarcos.idea.hivemindplugin.model.ServiceInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_ID;
import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.TAG_SERVICE_POINT;

/** Gutter-Icon an {@code <service-point>} → Interface, Implementierungsklassen und {@code <implementation>} anderer Module. */
public class HiveMindDescriptorLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof final XmlToken token) || token.getTokenType() != XmlTokenType.XML_NAME) return;
        if (!(token.getParent() instanceof final XmlTag tag) || !TAG_SERVICE_POINT.equals(tag.getName())) return;
        if (XmlTagUtil.getStartTagNameElement(tag) != token) return;
        if (!HiveMindRegistry.isDescriptor(tag.getContainingFile())) return;

        final ServiceInfo service = findService(tag);
        if (service == null) return;
        final List<PsiElement> targets = new ArrayList<>();
        final GlobalSearchScope scope = tag.getResolveScope();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(tag.getProject());
        final PsiClass serviceInterface = facade.findClass(service.interfaceName(), scope);
        if (serviceInterface != null) targets.add(serviceInterface);
        for (final ServiceImplementation implementation : service.implementations()) {
            final PsiClass implementationClass = implementation.className() != null ? facade.findClass(implementation.className(), scope) : null;
            if (implementationClass != null && !targets.contains(implementationClass)) targets.add(implementationClass);
            // Implementierung in einem anderen <implementation>-Element (evtl. anderes Modul)
            if (implementation.tag().getParentTag() != tag) targets.add(implementation.tag());
        }
        if (targets.isEmpty()) return;
        result.add(NavigationGutterIconBuilder.create(HiveMindIcons.SERVICE)
                .setTargets(targets)
                .setTooltipText("Navigate to interface / implementation of '%s'".formatted(service.id()))
                .setPopupTitle("Service '%s'".formatted(service.id()))
                .createLineMarkerInfo(token));
    }

    private static @Nullable ServiceInfo findService(XmlTag tag) {
        final HiveModule module = HiveMindRegistry.moduleOf(tag);
        final String id = HiveMindRegistry.attr(tag, ATTR_ID);
        if (module == null || id == null) return null;
        final ServiceInfo service = HiveMindRegistry.getInstance(tag.getProject()).findService(module.pointId(id));
        // bei doppelt definierten Service-Points gehört der Eintrag evtl. zur anderen Definition
        return service != null && service.point().tag() == tag ? service : null;
    }
}
