package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_ID;
import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.TAG_MODULE;

/**
 * Macht {@code <service-point id="Mailer">} (und Configuration-Points, Schemas) zur Deklaration: Find Usages, Rename
 * und Highlight Usages funktionieren direkt auf der id.
 */
public class PointDeclarationSearcher extends PomDeclarationSearcher {

    @Override
    public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement,
                                   @NotNull Consumer<? super PomTarget> consumer) {
        final PointTarget target = targetOf(element);
        if (target != null) consumer.consume(target);
    }

    static @Nullable PointTarget targetOf(@NotNull PsiElement element) {
        final PsiElement valueElement = element instanceof XmlAttributeValue ? element : element.getParent();
        if (!(valueElement instanceof XmlAttributeValue) || !(valueElement.getParent() instanceof final XmlAttribute attribute)) return null;
        if (!ATTR_ID.equals(attribute.getName())) return null;
        final XmlTag tag = attribute.getParent();
        final XmlTag parent = tag != null ? tag.getParentTag() : null;
        if (parent == null || !TAG_MODULE.equals(parent.getName()) || parent.getParentTag() != null) return null;
        final PointKind kind = PointKind.byTagName(tag.getName());
        if (kind == null || !HiveMindRegistry.isDescriptor(tag.getContainingFile())) return null;
        return new PointTarget(kind, tag);
    }
}
