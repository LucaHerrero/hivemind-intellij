package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Lokalisierte Nachricht {@code %message.key} (Doku "Localization") in Attributen und Inhalten von {@code <contribution>}
 * und {@code <invoke-factory>}. Die Nachrichten liegen neben dem Deskriptor: Name ohne ".xml" plus Locale plus
 * ".properties", also {@code hivemodule.properties}, {@code hivemodule_fr.properties} …
 * HiveMind ersetzt fehlende Schlüssel durch {@code [MESSAGE.KEY]} → nur Warnung.
 */
public class MessageReference extends PsiPolyVariantReferenceBase<PsiElement> implements HiveMindReference {

    MessageReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range, true);
    }

    /** Alle Nachrichtendateien des Deskriptors, die Basisdatei zuerst. */
    static @NotNull List<PropertiesFile> messageFiles(@NotNull PsiFile descriptor) {
        final PsiFile original = descriptor.getOriginalFile();
        final VirtualFile vf = original.getVirtualFile();
        final PsiDirectory dir = original.getContainingDirectory();
        if (vf == null || dir == null) return List.of();
        final String base = vf.getNameWithoutExtension();
        final List<PropertiesFile> result = new ArrayList<>();
        for (final PsiFile file : dir.getFiles()) {
            final String name = file.getName();
            if (file instanceof final PropertiesFile properties && name.endsWith(".properties")
                    && (name.equals(base + ".properties") || name.startsWith(base + "_"))) {
                if (name.equals(base + ".properties")) result.addFirst(properties);
                else result.add(properties);
            }
        }
        return result;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        final String key = getValue().trim();
        return messageFiles(getElement().getContainingFile()).stream()
                .map(file -> file.findPropertyByKey(key))
                .filter(Objects::nonNull)
                .map(IProperty::getPsiElement)
                .map(PsiElementResolveResult::new)
                .toArray(ResolveResult[]::new);
    }

    @Override
    public Object @NotNull [] getVariants() {
        final Set<String> keys = new LinkedHashSet<>();
        for (final PropertiesFile file : messageFiles(getElement().getContainingFile())) {
            for (final IProperty property : file.getProperties()) {
                if (property.getKey() != null) keys.add(property.getKey());
            }
        }
        return keys.stream().map(LookupElementBuilder::create).toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final String key = getValue().trim();
        return "Unknown message key '%s' (HiveMind shows [%s])".formatted(key, key.toUpperCase(Locale.ROOT));
    }
}
