package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindIds;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Ein Name in {@code <interceptor before="..." after="...">} (Doku "interceptor"): kommaseparierte Namen anderer
 * Interceptoren desselben Services – Name ist {@code name}, sonst die {@code service-id} der Interceptor-Factory;
 * unqualifiziert relativ zum Modul. {@code *} steht für "ganz vorne/ganz hinten" und ist keine Referenz.
 */
public class InterceptorOrderReference extends PsiPolyVariantReferenceBase<PsiElement> implements HiveMindReference {
    private final XmlTag interceptor;
    private final @Nullable HiveModule module;

    private InterceptorOrderReference(@NotNull XmlAttributeValue element, @NotNull TextRange range,
                                      @NotNull XmlTag interceptor, @Nullable HiveModule module) {
        super(element, range, true);
        this.interceptor = interceptor;
        this.module = module;
    }

    static PsiReference @NotNull [] create(@NotNull XmlAttributeValue value, @NotNull String text, int offset,
                                           @NotNull XmlTag interceptor, @Nullable HiveModule module) {
        if (text.trim().equals("*")) return PsiReference.EMPTY_ARRAY;
        final List<PsiReference> references = new ArrayList<>();
        int start = 0;
        for (final String part : text.split(",", -1)) {
            final TextRange range = HiveMindReferences.trimmed(part, offset + start);
            if (range != null) references.add(new InterceptorOrderReference(value, range, interceptor, module));
            start += part.length() + 1;
        }
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** Name eines Interceptors für die Reihenfolge: {@code name}, sonst die (qualifizierte) service-id der Factory. */
    static @Nullable String orderName(@NotNull XmlTag interceptor) {
        final String name = HiveMindRegistry.attr(interceptor, ATTR_NAME);
        final String id = name != null ? name : HiveMindRegistry.attr(interceptor, ATTR_SERVICE_ID);
        return id != null ? HiveMindIds.qualify(HiveMindRegistry.moduleOf(interceptor), id) : null;
    }

    /** Alle anderen Interceptoren desselben Services (aus allen Modulen). */
    private List<XmlTag> siblings() {
        final String serviceId = serviceId(interceptor);
        if (serviceId == null) return List.of();
        return HiveMindRegistry.getInstance(interceptor.getProject()).getInterceptors(serviceId).stream()
                .filter(other -> other != interceptor)
                .toList();
    }

    private @Nullable String serviceId(XmlTag tag) {
        final XmlTag parent = tag.getParentTag();
        if (parent == null) return null;
        final String id = HiveMindRegistry.attr(parent, TAG_SERVICE_POINT.equals(parent.getName()) ? ATTR_ID : ATTR_SERVICE_ID);
        if (id == null || module == null) return null;
        return TAG_SERVICE_POINT.equals(parent.getName()) ? module.pointId(id) : HiveMindIds.qualify(module, id);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        final String wanted = qualifiedValue();
        return siblings().stream()
                .filter(other -> wanted.equals(orderName(other)))
                .map(InterceptorOrderReference::nameElement)
                .map(PsiElementResolveResult::new)
                .toArray(ResolveResult[]::new);
    }

    /** Wert von {@code name} bzw. {@code service-id} – dort steht der Name, auf den verwiesen wird. */
    private static @NotNull PsiElement nameElement(XmlTag interceptor) {
        final XmlAttribute attribute = interceptor.getAttribute(interceptor.getAttribute(ATTR_NAME) != null ? ATTR_NAME : ATTR_SERVICE_ID);
        final PsiElement value = attribute != null ? attribute.getValueElement() : null;
        return value != null ? value : interceptor;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return siblings().stream()
                .map(InterceptorOrderReference::orderName)
                .filter(Objects::nonNull)
                .distinct()
                .map(name -> LookupElementBuilder.create(name).withIcon(HiveMindIcons.SERVICE))
                .toArray();
    }

    /** Der Orderer meldet unbekannte Namen nur im Log und ordnet trotzdem → Warnung. */
    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "No interceptor named '%s' for this service".formatted(qualifiedValue());
    }

    private @NotNull String qualifiedValue() {
        return HiveMindIds.qualify(module, getValue());
    }
}
