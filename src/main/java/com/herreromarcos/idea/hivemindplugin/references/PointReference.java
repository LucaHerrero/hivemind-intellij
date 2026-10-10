package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.util.IncorrectOperationException;
import com.herreromarcos.idea.hivemindplugin.HiveMindIcons;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindIds;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.herreromarcos.idea.hivemindplugin.model.Point;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Verweis auf einen Service-Point, Configuration-Point oder ein Schema, z.B. {@code service-id="hivemind.BuilderFactory"}.
 * Im Deskriptor sind unqualifizierte ids relativ zum eigenen Modul, außerhalb (Java, Tapestry) immer vollqualifiziert.
 */
public class PointReference extends PsiPolyVariantReferenceBase<PsiElement> implements HiveMindReference {
    private final PointKind kind;
    private final @Nullable HiveModule contextModule;
    private final HighlightSeverity severity;

    public PointReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull PointKind kind,
                          @Nullable HiveModule contextModule, @NotNull HighlightSeverity severity) {
        super(element, range, true);
        this.kind = kind;
        this.contextModule = contextModule;
        this.severity = severity;
    }

    public @Nullable HiveModule getContextModule() {
        return contextModule;
    }

    public @NotNull String getQualifiedId() {
        return HiveMindIds.qualify(contextModule, getValue());
    }

    /** Alle Punkte mit dieser id (mehrere bei doppelter Definition). */
    public @NotNull List<Point> findPoints() {
        if (getValue().isBlank()) return List.of();
        return HiveMindRegistry.getInstance(getElement().getProject()).findPoints(kind, getQualifiedId());
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return findPoints().stream()
                .map(point -> new PsiElementResolveResult(PointTarget.of(point).toPsi()))
                .toArray(ResolveResult[]::new);
    }

    /**
     * Rename des Punkts: Deklarierte ids sind einfach, nur der Teil nach dem Modulpräfix ändert sich
     * ({@code app.Mailer} → {@code app.Sender}).
     */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        final String value = getValue().trim();
        final int dot = value.lastIndexOf('.');
        return super.handleElementRename(dot < 0 ? newElementName : value.substring(0, dot + 1) + newElementName);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return HiveMindRegistry.getInstance(getElement().getProject()).getPoints(kind).stream()
                .filter(point -> point.isVisibleFrom(contextModule))
                .map(point -> LookupElementBuilder.create(point, lookupString(point))
                        .withIcon(HiveMindIcons.forKind(kind))
                        .withTypeText(point.module().id(), true))
                .toArray();
    }

    /** Im eigenen Modul genügt die unqualifizierte id. */
    private String lookupString(Point point) {
        return contextModule != null && contextModule.id().equals(point.module().id()) ? point.localId() : point.id();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return severity;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Unknown %s '%s'".formatted(kind.lowerCaseName(), getQualifiedId());
    }
}
