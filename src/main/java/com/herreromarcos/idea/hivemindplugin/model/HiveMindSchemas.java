package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Findet zu einem Element in einer {@code <contribution>} bzw. in den Parametern einer Factory/eines Interceptors die
 * Beschreibung im zugehörigen {@code <schema>} – und damit die Translatoren, die laut Doku ("Contribution Processing
 * Rules") festlegen, was ein Attribut oder Inhalt bedeutet ({@code service}, {@code configuration}, {@code object} …).
 *
 * <p>Schemaquelle:
 * <ul>
 *   <li>{@code <contribution configuration-id>} → {@code <schema>} im Configuration-Point oder dessen {@code schema-id}</li>
 *   <li>{@code <invoke-factory service-id>} / {@code <interceptor service-id>} → {@code <parameters-schema>} im
 *       Service-Point der Factory oder dessen {@code parameters-schema-id}</li>
 * </ul>
 */
public class HiveMindSchemas {
    private HiveMindSchemas() {
    }

    /** Translator des Attributs (ohne Initialisierer, z.B. "int" für "int,min=0") oder {@code null}. */
    public static @Nullable String attributeTranslator(@NotNull XmlTag contributed, @NotNull String attributeName) {
        final XmlTag element = schemaElement(contributed);
        if (element == null) return null;
        for (final XmlTag attribute : element.findSubTags(TAG_ATTRIBUTE)) {
            if (attributeName.equals(HiveMindRegistry.attr(attribute, ATTR_NAME))) {
                return translatorId(HiveMindRegistry.attr(attribute, ATTR_TRANSLATOR));
            }
        }
        return null;
    }

    /** {@code content-translator} des Elements oder {@code null}. */
    public static @Nullable String contentTranslator(@NotNull XmlTag contributed) {
        final XmlTag element = schemaElement(contributed);
        return element != null ? translatorId(HiveMindRegistry.attr(element, ATTR_CONTENT_TRANSLATOR)) : null;
    }

    private static @Nullable String translatorId(@Nullable String translator) {
        return translator != null ? HiveMindIds.stripInitializer(translator).trim() : null;
    }

    /** {@code <element name="...">} im Schema, das das beigesteuerte Element beschreibt. */
    public static @Nullable XmlTag schemaElement(@NotNull XmlTag contributed) {
        // Pfad der Elementnamen bis zum Container (<contribution>, <invoke-factory>, <interceptor>)
        final Deque<String> path = new ArrayDeque<>();
        XmlTag container = contributed;
        while (container != null && !isContainer(container)) {
            path.push(container.getName());
            container = container.getParentTag();
        }
        if (container == null || path.isEmpty()) return null;
        XmlTag scope = schemaOf(container);
        for (final String name : path) {
            if (scope == null) return null;
            scope = findElement(scope, name);
        }
        return scope;
    }

    /** {@code <contribution>} direkt im Modul bzw. {@code <invoke-factory>}/{@code <interceptor>} im service-point oder implementation. */
    public static boolean isContainer(@NotNull XmlTag tag) {
        final XmlTag parent = tag.getParentTag();
        if (parent == null) return false;
        return switch (tag.getName()) {
            case TAG_CONTRIBUTION -> TAG_MODULE.equals(parent.getName());
            case TAG_INVOKE_FACTORY, TAG_INTERCEPTOR ->
                    TAG_SERVICE_POINT.equals(parent.getName()) || TAG_IMPLEMENTATION.equals(parent.getName());
            default -> false;
        };
    }

    private static @Nullable XmlTag findElement(XmlTag scope, String name) {
        for (final XmlTag element : scope.findSubTags(TAG_ELEMENT)) {
            if (name.equals(HiveMindRegistry.attr(element, ATTR_NAME))) return element;
        }
        return null;
    }

    /** Das {@code <schema>}/{@code <parameters-schema>}, das den Inhalt des Containers beschreibt. */
    public static @Nullable XmlTag schemaOf(@NotNull XmlTag container) {
        final HiveMindRegistry registry = HiveMindRegistry.getInstance(container.getProject());
        final HiveModule module = registry.getModule(container.getContainingFile());
        if (module == null) return null;
        if (TAG_CONTRIBUTION.equals(container.getName())) {
            final String id = HiveMindRegistry.attr(container, ATTR_CONFIGURATION_ID);
            final Point point = id != null ? first(registry.findPoints(PointKind.CONFIGURATION, HiveMindIds.qualify(module.id(), id))) : null;
            return point != null ? schemaOfPoint(point, TAG_SCHEMA, ATTR_SCHEMA_ID, registry) : null;
        }
        final String factory = TAG_INVOKE_FACTORY.equals(container.getName())
                ? HiveMindRegistry.factoryId(container, module)
                : qualifiedOrNull(module, HiveMindRegistry.attr(container, ATTR_SERVICE_ID));
        final Point point = factory != null ? first(registry.findPoints(PointKind.SERVICE, factory)) : null;
        return point != null ? schemaOfPoint(point, TAG_PARAMETERS_SCHEMA, ATTR_PARAMETERS_SCHEMA_ID, registry) : null;
    }

    private static @Nullable XmlTag schemaOfPoint(Point point, String inlineTag, String idAttribute,
                                                  HiveMindRegistry registry) {
        final XmlTag inline = point.tag().findFirstSubTag(inlineTag);
        if (inline != null) return inline;
        final String schemaId = HiveMindRegistry.attr(point.tag(), idAttribute);
        final Point schema = schemaId != null ? first(registry.findPoints(PointKind.SCHEMA, HiveMindIds.qualify(point.module().id(), schemaId))) : null;
        return schema != null ? schema.tag() : null;
    }

    private static @Nullable String qualifiedOrNull(HiveModule module, @Nullable String id) {
        return id != null ? HiveMindIds.qualify(module.id(), id) : null;
    }

    private static @Nullable Point first(List<Point> points) {
        return points.isEmpty() ? null : points.getFirst();
    }
}
