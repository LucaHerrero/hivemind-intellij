package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ProcessingContext;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindSchemas;
import com.herreromarcos.idea.hivemindplugin.model.HiveModule;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Referenzen in Moduldeskriptoren. Was ein Wert bedeutet, hängt davon ab, wo er steht:
 * <ul>
 *   <li><b>Deskriptorformat</b> (Doku "Module Descriptor"): {@code service-id}, {@code configuration-id}, {@code schema-id},
 *       {@code module-id}, Klassennamen, {@code sub-module descriptor}, Klassen in {@code if}-Bedingungen</li>
 *   <li><b>Parameter der {@code hivemind.BuilderFactory}</b> (Doku "BuilderFactory"): {@code construct class},
 *       {@code set-service}, {@code set-configuration}, {@code set-object}, {@code set-resource}, {@code event-listener},
 *       Inhalt von {@code <service>}, {@code <configuration>}, {@code <object>}, {@code <resource>}</li>
 *   <li><b>Contributions und Parameter anderer Factories/Interceptoren</b>: laut {@code translator} im zugehörigen
 *       Schema (Doku "Contribution Processing Rules")</li>
 * </ul>
 * Unaufgelöste ids sind Fehler: HiveMind meldet sie beim Aufbau der Registry. Werte mit Symbolen ({@code ${...}}) oder
 * Nachrichten ({@code %key}) ersetzt HiveMind erst zur Laufzeit; sie verweisen auf Symbol-Standardwerte bzw. Properties.
 */
class DescriptorReferenceProvider extends PsiReferenceProvider {
    private static final HighlightSeverity ERROR = HighlightSeverity.ERROR;
    /** Bedingung {@code if="class org.example.Foo and not (property debug)"} (Doku "Conditional Contributions"). */
    private static final Pattern CONDITION_CLASS = Pattern.compile("(?i)\\bclass\\s+([\\w.$-]+)");

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!HiveMindRegistry.isDescriptor(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;
        if (element instanceof final XmlTag tag) return tagValueReferences(tag);
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final XmlTag tag = attribute.getParent();
        if (tag == null) return PsiReference.EMPTY_ARRAY;
        final String text = ElementManipulators.getValueText(value);
        final int offset = ElementManipulators.getValueTextRange(value).getStartOffset();
        final XmlTag container = container(tag);
        if (HiveMindReferences.isDynamic(text)) return dynamicReferences(value, text, offset, container);

        final HiveModule module = HiveMindRegistry.moduleOf(element);
        if (container == null) return descriptorReferences(value, tag, attribute.getName(), text, offset, module);
        if (isBuilderFactory(container, module)) return builderReferences(value, tag.getName(), attribute.getName(), text, offset, module);
        return translatorReferences(value, HiveMindSchemas.attributeTranslator(tag, attribute.getName()), text, offset, module);
    }

    /**
     * Werte, die HiveMind zur Laufzeit ersetzt: Symbole {@code ${name}} → Standardwerte; Nachrichten {@code %key} →
     * Properties neben dem Deskriptor (laut Doku nur in {@code <contribution>} und {@code <invoke-factory>}).
     */
    private static PsiReference[] dynamicReferences(PsiElement element, String text, int offset,
                                                    @Nullable XmlTag container) {
        final List<PsiReference> references = new ArrayList<>(SymbolReference.create(element, text, offset));
        final String trimmed = text.trim();
        if (trimmed.startsWith("%") && container != null && !TAG_INTERCEPTOR.equals(container.getName())) {
            final int start = offset + text.indexOf('%') + 1;
            if (trimmed.length() > 1) references.add(new MessageReference(element, TextRange.from(start, trimmed.length() - 1)));
        }
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** Elemente des Deskriptorformats selbst (außerhalb von Contributions und Factory-Parametern). */
    private static PsiReference[] descriptorReferences(XmlAttributeValue value, XmlTag tag, String attrName,
                                                       String text, int offset, HiveModule module) {
        return switch ("%s@%s".formatted(tag.getName(), attrName)) {
            case "service-point@interface" -> HiveMindReferences.className(value, text, offset, module, true, ERROR);
            case "create-instance@class", "conversion@class", "create-object@class", "custom@class" ->
                    HiveMindReferences.className(value, text, offset, module, false, ERROR);
            case "invoke-factory@service-id", "interceptor@service-id", "implementation@service-id" ->
                    HiveMindReferences.point(value, text, offset, PointKind.SERVICE, module, ERROR);
            case "contribution@configuration-id" -> HiveMindReferences.point(value, text, offset, PointKind.CONFIGURATION, module, ERROR);
            case "configuration-point@schema-id", "service-point@parameters-schema-id" ->
                    HiveMindReferences.point(value, text, offset, PointKind.SCHEMA, module, ERROR);
            case "dependency@module-id" -> text.isBlank() ? PsiReference.EMPTY_ARRAY
                    : new PsiReference[]{new ModuleReference(value, TextRange.from(offset, text.length()))};
            case "sub-module@descriptor" -> new FileReferenceSet(text, value, offset, null, true).getAllReferences();
            case "contribution@if", "implementation@if" -> conditionReferences(value, text, offset);
            case "interceptor@before", "interceptor@after" -> InterceptorOrderReference.create(value, text, offset, tag, module);
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** Parameter der {@code hivemind.BuilderFactory}. */
    private static PsiReference[] builderReferences(XmlAttributeValue value, String tagName, String attrName,
                                                    String text, int offset, HiveModule module) {
        return switch ("%s@%s".formatted(tagName, attrName)) {
            case "construct@class" -> HiveMindReferences.className(value, text, offset, module, false, ERROR);
            case "set-service@service-id", "event-listener@service-id" ->
                    HiveMindReferences.point(value, text, offset, PointKind.SERVICE, module, ERROR);
            case "set-configuration@configuration-id" -> HiveMindReferences.point(value, text, offset, PointKind.CONFIGURATION, module, ERROR);
            case "set-object@value" -> HiveMindReferences.objectExpression(value, text, offset, module, ERROR);
            case "set-resource@path" -> resourceReferences(value, text, offset);
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** Bedeutung laut Translator des Schemas (Doku "Translators"). */
    private static PsiReference[] translatorReferences(PsiElement element, @Nullable String translator,
                                                       String text, int offset, HiveModule module) {
        if (translator == null) return PsiReference.EMPTY_ARRAY;
        return switch (translator) {
            case "service", "service-point" -> HiveMindReferences.point(element, text, offset, PointKind.SERVICE, module, ERROR);
            case "configuration" -> HiveMindReferences.point(element, text, offset, PointKind.CONFIGURATION, module, ERROR);
            case "object" -> HiveMindReferences.objectExpression(element, text, offset, module, ERROR);
            case "instance", "class" -> HiveMindReferences.className(element, text, offset, module, false, ERROR);
            case "resource" -> resourceReferences(element, text, offset);
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** Textinhalt: BuilderFactory-Konstruktorparameter bzw. {@code content-translator} des Schemas. */
    private static PsiReference[] tagValueReferences(XmlTag tag) {
        final XmlTagValue tagValue = tag.getValue();
        final String text = tagValue.getText();
        if (text.isBlank() || tag.getSubTags().length > 0) return PsiReference.EMPTY_ARRAY;
        final XmlTag container = container(tag);
        final int offset = tagValue.getTextRange().getStartOffset() - tag.getTextRange().getStartOffset();
        if (HiveMindReferences.isDynamic(text)) return dynamicReferences(tag, text, offset, container);
        if (container == null) return PsiReference.EMPTY_ARRAY;
        final HiveModule module = HiveMindRegistry.moduleOf(tag);
        if (!isBuilderFactory(container, module)) {
            return translatorReferences(tag, HiveMindSchemas.contentTranslator(tag), text, offset, module);
        }
        final XmlTag parent = tag.getParentTag();
        if (parent == null || !TAG_CONSTRUCT.equals(parent.getName())) return PsiReference.EMPTY_ARRAY;
        return switch (tag.getName()) {
            case TAG_SERVICE -> HiveMindReferences.point(tag, text, offset, PointKind.SERVICE, module, ERROR);
            case TAG_CONFIGURATION -> HiveMindReferences.point(tag, text, offset, PointKind.CONFIGURATION, module, ERROR);
            case TAG_OBJECT -> HiveMindReferences.objectExpression(tag, text, offset, module, ERROR);
            case TAG_RESOURCE -> {
                final TextRange range = HiveMindReferences.trimmed(text, offset);
                yield range == null ? PsiReference.EMPTY_ARRAY : resourceReferences(tag, text.trim(), range.getStartOffset());
            }
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** Pfad relativ zum Deskriptor des beitragenden Moduls (Doku "BuilderFactory", Translator "resource"). */
    private static PsiReference[] resourceReferences(PsiElement element, String text, int offset) {
        return new FileReferenceSet(text, element, offset, null, true).getAllReferences();
    }

    /** Klassen in Bedingungen sind gewollt optional → Navigation ja, Meldung nein. */
    private static PsiReference[] conditionReferences(XmlAttributeValue value, String text, int offset) {
        final List<PsiReference> references = new ArrayList<>();
        final Matcher matcher = CONDITION_CLASS.matcher(text);
        final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        provider.setSoft(true);
        while (matcher.find()) {
            references.addAll(Arrays.asList(provider.getReferencesByString(matcher.group(1), value, offset + matcher.start(1))));
        }
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** Nächstes umgebendes {@code <contribution>}, {@code <invoke-factory>} oder {@code <interceptor>} (ohne das Tag selbst). */
    private static @Nullable XmlTag container(XmlTag tag) {
        for (XmlTag current = tag.getParentTag(); current != null; current = current.getParentTag()) {
            if (HiveMindSchemas.isContainer(current)) return current;
        }
        return null;
    }

    private static boolean isBuilderFactory(XmlTag container, @Nullable HiveModule module) {
        return TAG_INVOKE_FACTORY.equals(container.getName()) && DEFAULT_FACTORY.equals(HiveMindRegistry.factoryId(container, module));
    }
}
