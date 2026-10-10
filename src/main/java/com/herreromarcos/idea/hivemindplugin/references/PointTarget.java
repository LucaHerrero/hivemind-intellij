package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;
import com.herreromarcos.idea.hivemindplugin.model.Point;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.ATTR_ID;

/**
 * Deklaration eines Service-Points, Configuration-Points oder Schemas als benanntes Ziel. Alle {@link PointReference}s
 * lösen darauf auf ({@link #toPsi()}); dadurch funktionieren Find Usages, Rename und Highlight Usages –
 * der Name ist die einfache id aus {@code <service-point id="...">}.
 */
public class PointTarget implements PsiDeclaredTarget, PomRenameableTarget<Object> {
    private final PointKind kind;
    private final XmlTag tag;

    public PointTarget(@NotNull PointKind kind, @NotNull XmlTag tag) {
        this.kind = kind;
        this.tag = tag;
    }

    public static @NotNull PointTarget of(@NotNull Point point) {
        return new PointTarget(point.kind(), point.tag());
    }

    public @NotNull PsiElement toPsi() {
        return PomService.convertToPsi(tag.getProject(), this);
    }

    public @NotNull PointKind getKind() {
        return kind;
    }

    public @NotNull XmlTag getTag() {
        return tag;
    }

    private @Nullable XmlAttributeValue idValue() {
        final XmlAttribute attribute = tag.getAttribute(ATTR_ID);
        return attribute != null ? attribute.getValueElement() : null;
    }

    @Override
    public @NotNull String getName() {
        final String id = tag.getAttributeValue(ATTR_ID);
        return id != null ? id.trim() : "";
    }

    @Override
    public boolean isWritable() {
        return tag.isWritable();
    }

    @Override
    public @Nullable Object setName(@NotNull String newName) {
        tag.setAttribute(ATTR_ID, newName);
        return null;
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        final XmlAttributeValue value = idValue();
        return value != null ? value : tag;
    }

    /** Bereich der id im Navigationselement (ohne Anführungszeichen). */
    @Override
    public @Nullable TextRange getNameIdentifierRange() {
        final XmlAttributeValue value = idValue();
        return value != null ? ElementManipulators.getValueTextRange(value) : null;
    }

    @Override
    public boolean isValid() {
        return tag.isValid();
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiNavigateUtil.navigate(getNavigationElement(), requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return isValid();
    }

    @Override
    public boolean canNavigateToSource() {
        return isValid();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof final PointTarget other && kind == other.kind && tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, tag);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(kind.displayName(), getName());
    }
}
