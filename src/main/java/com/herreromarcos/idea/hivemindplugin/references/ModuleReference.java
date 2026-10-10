package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_ID;

/** {@code <dependency module-id="hivemind.lib"/>} → {@code <module id="hivemind.lib">}. */
public class ModuleReference extends PsiPolyVariantReferenceBase<PsiElement> implements HiveMindReference {

    public ModuleReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range, true);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return HiveMindRegistry.getInstance(getElement().getProject()).findModules(getValue()).stream()
                .map(module -> module.root().getAttribute(ATTR_ID))
                .filter(Objects::nonNull)
                .map(XmlAttribute::getValueElement)
                .filter(Objects::nonNull)
                .map(PsiElementResolveResult::new)
                .toArray(ResolveResult[]::new);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return HiveMindRegistry.getInstance(getElement().getProject()).getModules().stream()
                .map(HiveModule::id)
                .distinct()
                .map(id -> LookupElementBuilder.create(id).withIcon(HiveMindIcons.MODULE))
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Unknown module '%s'".formatted(getValue().trim());
    }
}
