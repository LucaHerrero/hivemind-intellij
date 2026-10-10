package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.herreromarcos.idea.hivemindplugin.model.PointKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Verweise auf Services außerhalb der Deskriptoren – ids sind dort immer vollqualifiziert:
 * <ul>
 *   <li>Java: {@code registry.getService("app.Mailer", Mailer.class)}, {@code getConfiguration("app.Settings")},
 *       {@code containsService(...)} an {@code org.apache.hivemind.Registry} bzw. {@code Module}</li>
 *   <li>Tapestry 4: {@code @InjectObject("service:app.Mailer")}, {@code <inject object="service:app.Mailer"/>}
 *       in .page/.jwc/.application/.library und das Binding-Präfix {@code hivemind:} in Templates und Spezifikationen
 *       ({@code value="hivemind:service:app.Mailer"})</li>
 * </ul>
 * Unaufgelöste ids sind hier nur Warnungen (die Registry ist im Editor evtl. nicht vollständig sichtbar).
 */
class InjectionReferenceProvider extends PsiReferenceProvider {
    private static final Set<String> TAPESTRY_SPEC_EXTENSIONS = Set.of("page", "jwc", "application", "library");
    private static final Set<String> REGISTRY_CLASSES = Set.of(REGISTRY_CLASS, MODULE_CLASS);

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (element instanceof final PsiLiteralExpression literal) return javaReferences(literal);
        if (element instanceof final XmlAttributeValue value) return tapestryReferences(value);
        return PsiReference.EMPTY_ARRAY;
    }

    private static PsiReference[] javaReferences(PsiLiteralExpression literal) {
        if (!(literal.getValue() instanceof final String text)) return PsiReference.EMPTY_ARRAY;
        final int offset = ElementManipulators.getValueTextRange(literal).getStartOffset();
        final HighlightSeverity warning = HighlightSeverity.WARNING;

        final PsiAnnotation annotation = annotationOfValue(literal);
        if (annotation != null) {
            return annotation.hasQualifiedName(TAPESTRY_INJECT_OBJECT)
                    ? HiveMindReferences.objectExpression(literal, text, offset, null, warning) : PsiReference.EMPTY_ARRAY;
        }
        final PointKind kind = registryLookupKind(literal);
        return kind != null ? HiveMindReferences.point(literal, text, offset, kind, null, warning) : PsiReference.EMPTY_ARRAY;
    }

    /** Annotation, deren (einziger) Wert das Literal ist. */
    private static @Nullable PsiAnnotation annotationOfValue(PsiLiteralExpression literal) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof final PsiNameValuePair pair && pair.getParent() instanceof final PsiAnnotationParameterList list
                && list.getParent() instanceof final PsiAnnotation annotation) {
            return pair.getAttributeName().equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) ? annotation : null;
        }
        return null;
    }

    /** Erstes Argument von getService/getConfiguration/containsService/containsConfiguration der HiveMind-Registry. */
    private static @Nullable PointKind registryLookupKind(PsiLiteralExpression literal) {
        if (!(literal.getParent() instanceof final PsiExpressionList arguments)
                || arguments.getExpressionCount() == 0 || arguments.getExpressions()[0] != literal
                || !(arguments.getParent() instanceof final PsiMethodCallExpression call)) {
            return null;
        }
        final PointKind kind = switch (call.getMethodExpression().getReferenceName()) {
            case "getService", "containsService" -> PointKind.SERVICE;
            case "getConfiguration", "containsConfiguration", "getConfigurationAsMap" -> PointKind.CONFIGURATION;
            case null, default -> null;
        };
        if (kind == null) return null;
        final PsiMethod method = call.resolveMethod();
        final PsiClass owner = method != null ? method.getContainingClass() : null;
        return owner != null && REGISTRY_CLASSES.contains(owner.getQualifiedName()) ? kind : null;
    }

    private static PsiReference[] tapestryReferences(XmlAttributeValue value) {
        if (!(value.getParent() instanceof final XmlAttribute attribute)) return PsiReference.EMPTY_ARRAY;
        final PsiFile file = value.getContainingFile();
        final String text = ElementManipulators.getValueText(value);
        final int offset = ElementManipulators.getValueTextRange(value).getStartOffset();
        if (text.startsWith(HIVEMIND_BINDING_PREFIX) && (isTapestrySpec(file) || isTemplate(file))) {
            final int start = HIVEMIND_BINDING_PREFIX.length();
            return HiveMindReferences.objectExpression(value, text.substring(start), offset + start, null, HighlightSeverity.WARNING);
        }
        return isInjectObject(attribute) && isTapestrySpec(file)
                ? HiveMindReferences.objectExpression(value, text, offset, null, HighlightSeverity.WARNING) : PsiReference.EMPTY_ARRAY;
    }

    /** {@code <inject object="service:app.Mailer"/>} ohne type oder mit {@code type="object"}. */
    private static boolean isInjectObject(XmlAttribute attribute) {
        final XmlTag tag = attribute.getParent();
        if (tag == null || !ATTR_OBJECT.equals(attribute.getName()) || !TAPESTRY_INJECT.equals(tag.getName())) return false;
        final String type = tag.getAttributeValue(ATTR_TYPE);
        return type == null || ATTR_OBJECT.equals(type);
    }

    private static boolean isTapestrySpec(PsiFile file) {
        final var vf = file.getOriginalFile().getVirtualFile();
        return vf != null && TAPESTRY_SPEC_EXTENSIONS.contains(vf.getExtension());
    }

    private static boolean isTemplate(PsiFile file) {
        final Language language = file.getViewProvider().getBaseLanguage();
        return language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE);
    }
}
