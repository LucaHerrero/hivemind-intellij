package com.herreromarcos.idea.hivemindplugin.highlighting;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.herreromarcos.idea.hivemindplugin.model.Point;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import com.herreromarcos.idea.hivemindplugin.model.ServiceImplementation;
import com.herreromarcos.idea.hivemindplugin.references.HiveMindReference;
import com.herreromarcos.idea.hivemindplugin.references.PointReference;
import org.apache.hivemind.conditional.Parser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Prüft Moduldeskriptoren und Verweise auf Services im Java-Code / in Tapestry-Spezifikationen:
 * <ul>
 *   <li>unaufgelöste Referenzen (Service-/Configuration-ids, Schemas, Module, Klassen im Modulpaket)</li>
 *   <li>Verweise auf private Punkte eines anderen Moduls</li>
 *   <li>doppelt definierte Punkte, Service-Points ohne bzw. mit mehreren überschreibenden Implementierungen</li>
 *   <li>Syntax von {@code if}-Bedingungen (Parser von HiveMind)</li>
 *   <li>{@code <construct>} der BuilderFactory ohne {@code class}, {@code parameters-schema} zusammen mit {@code parameters-schema-id}</li>
 * </ul>
 */
public class HiveMindAnnotator implements Annotator {
    private static final Set<String> CONDITIONAL_TAGS = Set.of(TAG_CONTRIBUTION, TAG_IMPLEMENTATION);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof final PsiLiteralExpression literal) {
            checkReferences(literal, holder);
            return;
        }
        if (!(element instanceof XmlAttributeValue) && !(element instanceof XmlTag)) return;
        final boolean descriptor = HiveMindRegistry.isDescriptor(element.getContainingFile());
        if (element instanceof final XmlAttributeValue value && value.getParent() instanceof final XmlAttribute attribute) {
            if (descriptor && ATTR_ID.equals(attribute.getName())) checkPointDefinition(value, attribute.getParent(), holder);
            if (descriptor && ATTR_IF.equals(attribute.getName())) checkCondition(value, holder);
            // außerhalb der Deskriptoren nur <inject object="..."> und "hivemind:"-Bindings in Tapestry-Dateien
            if (descriptor || ATTR_OBJECT.equals(attribute.getName()) || value.getValue().startsWith(HIVEMIND_BINDING_PREFIX)) {
                checkReferences(element, holder);
            }
        } else if (descriptor && element instanceof final XmlTag tag) {
            checkStructure(tag, holder);
            checkReferences(element, holder);
        }
    }

    private static void checkReferences(PsiElement element, AnnotationHolder holder) {
        final int start = element.getTextRange().getStartOffset();
        for (final PsiReference reference : element.getReferences()) {
            if (!(reference instanceof final HiveMindReference hiveMindReference)) continue;
            final TextRange range = reference.getRangeInElement().shiftRight(start);
            if (range.isEmpty()) continue;
            if (!isResolved(reference)) {
                final HighlightSeverity severity = hiveMindReference.getUnresolvedSeverity();
                if (severity == null) continue;
                holder.newAnnotation(severity, hiveMindReference.getUnresolvedMessage())
                        .range(range)
                        .highlightType(severity == HighlightSeverity.ERROR ? ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                                : ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                        .create();
            } else if (reference instanceof final PointReference pointReference) {
                checkVisibility(pointReference, range, holder);
            }
        }
    }

    private static boolean isResolved(PsiReference reference) {
        if (reference instanceof final PsiPolyVariantReference poly) return poly.multiResolve(false).length > 0;
        return reference.resolve() != null;
    }

    /**
     * Private Punkte dürfen nur im eigenen Modul referenziert oder beliefert werden (Doku "service-point",
     * "configuration-point", "schema"). Im Deskriptor bricht HiveMind ab → Fehler; aus Java/Tapestry nur Warnung.
     */
    private static void checkVisibility(PointReference reference, TextRange range, AnnotationHolder holder) {
        final HiveModule from = reference.getContextModule();
        final List<Point> points = reference.findPoints();
        if (points.stream().anyMatch(point -> point.isVisibleFrom(from))) return;
        final Point point = points.getFirst();
        holder.newAnnotation(from != null ? HighlightSeverity.ERROR : HighlightSeverity.WARNING, "%s '%s' is private to module '%s'"
                        .formatted(point.kind().displayName(), point.id(), point.module().id()))
                .range(range)
                .create();
    }

    /** {@code <service-point id="...">} usw.: eindeutig im Classpath, Service-Points brauchen eine Implementierung. */
    private static void checkPointDefinition(XmlAttributeValue value, XmlTag tag, AnnotationHolder holder) {
        if (tag == null || tag.getParentTag() == null || !TAG_MODULE.equals(tag.getParentTag().getName())) return;
        final PointKind kind = PointKind.byTagName(tag.getName());
        final HiveModule module = HiveMindRegistry.moduleOf(tag);
        final String id = HiveMindRegistry.attr(tag, ATTR_ID);
        if (kind == null || module == null || id == null) return;
        final HiveMindRegistry registry = HiveMindRegistry.getInstance(tag.getProject());
        final Point self = registry.findPoints(kind, module.pointId(id))
                .stream().filter(point -> point.tag() == tag).findFirst().orElse(null);
        if (self == null) return;

        // Gleiches Modul doppelt im Classpath (Quellen + JAR) ist kein Konflikt im Editor → nur Projektdefinitionen zählen
        final List<Point> others = registry.findPoints(kind, self.id()).stream()
                .filter(point -> point != self && point.module().inProject())
                .toList();
        if (!others.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate %s '%s' (also defined in %s)"
                            .formatted(kind.lowerCaseName(), self.id(), others.getFirst().module().location()))
                    .range(value)
                    .create();
        }
        if (kind == PointKind.SERVICE) checkImplementations(self, value, registry, holder);
    }

    /**
     * Doku "Services" (FAQ): ohne Service-Konstruktor meldet HiveMind beim Aufbau der Registry einen Fehler – die
     * Implementierung kann aber aus einem Modul kommen, das im Editor nicht sichtbar ist → Warnung.
     * Doku "Overriding a Service": mehr als ein überschreibendes {@code <implementation>} ist ein Fehler; mit
     * {@code if}-Bedingungen können sie sich ausschließen → dann nur Warnung.
     */
    private static void checkImplementations(Point service, XmlAttributeValue value, HiveMindRegistry registry,
                                             AnnotationHolder holder) {
        final List<ServiceImplementation> implementations = registry.getImplementations(service.id());
        if (implementations.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Service point '%s' has no implementation (create-instance or invoke-factory)".formatted(service.id()))
                    .range(value)
                    .create();
            return;
        }
        final List<ServiceImplementation> overrides = implementations.stream().filter(i -> !i.isDefault()).toList();
        if (overrides.size() > 1) {
            final boolean conditional = overrides.stream().anyMatch(i -> i.condition() != null);
            final String modules = overrides.stream().map(i -> i.module().id()).distinct().collect(Collectors.joining(", "));
            holder.newAnnotation(conditional ? HighlightSeverity.WARNING : HighlightSeverity.ERROR,
                            "Service point '%s' is overridden by %d <implementation> elements (%s)%s".formatted(service.id(),
                                    overrides.size(), modules, conditional ? "; only one may be active" : ""))
                    .range(value)
                    .create();
        }
    }

    /**
     * {@code if="class org.example.Foo and not (property debug)"}: geprüft mit dem Parser von HiveMind selbst
     * ({@link Parser}), der die Bedingung auch beim Aufbau der Registry auswertet.
     */
    private static void checkCondition(XmlAttributeValue value, AnnotationHolder holder) {
        final XmlTag tag = value.getParent() instanceof final XmlAttribute attribute ? attribute.getParent() : null;
        if (tag == null || !CONDITIONAL_TAGS.contains(tag.getName()) || value.getValue().isBlank()) return;
        try {
            new Parser().parse(value.getValue());
        } catch (final RuntimeException e) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Invalid condition: %s".formatted(e.getMessage())).range(value).create();
        }
    }

    /** Strukturregeln, die das Schema nicht ausdrücken kann. */
    private static void checkStructure(XmlTag tag, AnnotationHolder holder) {
        final XmlTag parent = tag.getParentTag();
        // Doku "parameters-schema": schließt sich mit parameters-schema-id aus
        if (TAG_PARAMETERS_SCHEMA.equals(tag.getName()) && parent != null && TAG_SERVICE_POINT.equals(parent.getName())
                && parent.getAttribute(ATTR_PARAMETERS_SCHEMA_ID) != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "<parameters-schema> and the parameters-schema-id attribute are mutually exclusive")
                    .range(startTagName(tag))
                    .create();
        }
        // <construct> der BuilderFactory braucht class (andere Factories haben eigene <construct>-Parameter)
        if (TAG_CONSTRUCT.equals(tag.getName()) && parent != null && TAG_INVOKE_FACTORY.equals(parent.getName())
                && DEFAULT_FACTORY.equals(HiveMindRegistry.factoryId(parent, HiveMindRegistry.moduleOf(tag)))
                && HiveMindRegistry.attr(tag, ATTR_CLASS) == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "hivemind.BuilderFactory requires the class attribute of <construct>")
                    .range(startTagName(tag))
                    .create();
        }
    }

    /** Name im Start-Tag – als Markierungsbereich kürzer als das ganze Element. */
    private static PsiElement startTagName(XmlTag tag) {
        final PsiElement name = XmlTagUtil.getStartTagNameElement(tag);
        return name != null ? name : tag;
    }
}
