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
import com.intellij.psi.xml.XmlTag;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_SYMBOL;
import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_VALUE;

/**
 * Substitutionssymbol {@code ${config.dir}} (Doku "Configurations") → {@code <default symbol="config.dir" .../>} in
 * {@code hivemind.ApplicationDefaults} bzw. {@code hivemind.FactoryDefaults}. {@code $${...}} ist maskiert und kein Symbol.
 * Symbole können auch aus anderen {@code hivemind.SymbolSources} kommen (z.B. Systemeigenschaften) → unaufgelöst keine Meldung.
 */
public class SymbolReference extends PsiPolyVariantReferenceBase<PsiElement> implements HiveMindReference {
    private static final Pattern SYMBOL = Pattern.compile("(?<!\\$)\\$\\{([^}]+)}");

    private SymbolReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range, true);
    }

    static @NotNull List<PsiReference> create(@NotNull PsiElement element, @NotNull String text, int offset) {
        final List<PsiReference> references = new ArrayList<>();
        final Matcher matcher = SYMBOL.matcher(text);
        while (matcher.find()) {
            final TextRange range = HiveMindReferences.trimmed(matcher.group(1), offset + matcher.start(1));
            if (range != null) references.add(new SymbolReference(element, range));
        }
        return references;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return HiveMindRegistry.getInstance(getElement().getProject()).findSymbolDefaults(getValue()).stream()
                .map(tag -> tag.getAttribute(ATTR_SYMBOL))
                .filter(Objects::nonNull)
                .map(XmlAttribute::getValueElement)
                .filter(Objects::nonNull)
                .map(PsiElementResolveResult::new)
                .toArray(ResolveResult[]::new);
    }

    @Override
    public Object @NotNull [] getVariants() {
        final HiveMindRegistry registry = HiveMindRegistry.getInstance(getElement().getProject());
        return registry.getSymbolNames().stream()
                .map(name -> {
                    final XmlTag first = registry.findSymbolDefaults(name).getFirst();
                    return LookupElementBuilder.create(name).withIcon(HiveMindIcons.CONFIGURATION)
                            .withTypeText(first.getAttributeValue(ATTR_VALUE), true);
                })
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Symbol '%s' has no default value".formatted(getValue());
    }
}
