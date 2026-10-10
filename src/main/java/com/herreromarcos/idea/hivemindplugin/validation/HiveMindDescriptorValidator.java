package com.herreromarcos.idea.hivemindplugin.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import org.apache.commons.logging.Log;
import org.apache.hivemind.ApplicationRuntimeException;
import org.apache.hivemind.ErrorHandler;
import org.apache.hivemind.Location;
import org.apache.hivemind.impl.DefaultClassResolver;
import org.apache.hivemind.parse.XmlResourceProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Prüft Moduldeskriptoren mit dem echten Parser von Apache HiveMind 1.1.1 ({@code XmlResourceProcessor} →
 * {@code DescriptorParser}) – dieselben Meldungen wie beim Start der Registry: Pflichtattribute, Format von Modul-ids,
 * Punkt-ids und Versionen, Wahrheitswerte, Aufzählungswerte (visibility, occurs …), doppelte Schemas.
 *
 * <p>Ausgelassen werden Meldungen, die der Editor schon anders zeigt: unbekannte Elemente/Attribute (Schema),
 * nicht wohlgeformtes XML (XML-Parser der IDE) und nicht ladbare {@code <custom>}-Regelklassen (die Projektklassen
 * liegen nicht im Classpath der IDE).
 */
public class HiveMindDescriptorValidator extends ExternalAnnotator<HiveMindDescriptorValidator.Input, List<HiveMindDescriptorValidator.Problem>> {
    private static final List<String> SKIPPED_MESSAGE_PREFIXES = skippedMessagePrefixes();

    public record Input(@NotNull String path, @NotNull String text) {
    }

    public record Problem(@NotNull String message, int line, int column) {
    }

    /**
     * Die Plattform überspringt externe Prüfungen standardmäßig, sobald die Datei schon Fehler hat. Die Meldungen von
     * HiveMind sind davon unabhängig (z.B. ungültige Version neben einer unbekannten Klasse) → immer prüfen.
     */
    @Override
    public @Nullable Input collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file);
    }

    @Override
    public @Nullable Input collectInformation(@NotNull PsiFile file) {
        if (!HiveMindRegistry.isDescriptor(file)) return null;
        final var vf = file.getOriginalFile().getVirtualFile();
        return new Input(vf != null ? vf.getPath() : file.getName(), file.getText());
    }

    @Override
    public @Nullable List<Problem> doAnnotate(Input input) {
        if (input == null) return null;
        final List<Problem> problems = new ArrayList<>();
        final ErrorHandler collector = (Log log, String message, Location location, Throwable cause) ->
                add(problems, message, location);
        try {
            new XmlResourceProcessor(new DefaultClassResolver(HiveMindDescriptorValidator.class.getClassLoader()), collector)
                    .processResource(new TextResource(input.path(), input.text()));
        } catch (final ApplicationRuntimeException e) {
            // processResource verpackt Abbrüche in "Unable to read descriptor …" – die eigentliche Ursache melden,
            // nicht wohlgeformtes XML (SAXException) zeigt bereits die IDE
            if (e.getRootCause() instanceof final ApplicationRuntimeException cause) add(problems, cause.getMessage(), cause.getLocation());
            else if (!(e.getRootCause() instanceof org.xml.sax.SAXException)) add(problems, e.getMessage(), e.getLocation());
        }
        return problems;
    }

    private static void add(List<Problem> problems, @Nullable String message, @Nullable Location location) {
        if (message == null || SKIPPED_MESSAGE_PREFIXES.stream().anyMatch(message::startsWith)) return;
        problems.add(new Problem(message, location != null ? location.getLineNumber() : 0, location != null ? location.getColumnNumber() : 0));
    }

    @Override
    public void apply(@NotNull PsiFile file, List<Problem> problems, @NotNull AnnotationHolder holder) {
        final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || problems == null) return;
        for (final Problem problem : problems) {
            holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(rangeOf(file, document, problem))
                    .create();
        }
    }

    /**
     * Der SAX-Locator zeigt auf das Ende des Start-Tags → das Tag davor wird markiert (sein Name);
     * ohne Position die erste Zeile.
     */
    private static TextRange rangeOf(PsiFile file, Document document, Problem problem) {
        if (problem.line() <= 0 || problem.line() > document.getLineCount()) {
            return TextRange.create(0, document.getLineEndOffset(0));
        }
        final int lineStart = document.getLineStartOffset(problem.line() - 1);
        final int offset = Math.min(document.getLineEndOffset(problem.line() - 1), lineStart + Math.max(problem.column() - 1, 0));
        final XmlTag tag = PsiTreeUtil.getParentOfType(file.findElementAt(Math.max(offset - 1, 0)), XmlTag.class, false);
        if (tag != null) {
            final PsiElement name = XmlTagUtil.getStartTagNameElement(tag);
            if (name != null) return name.getTextRange();
        }
        return TextRange.create(lineStart, document.getLineEndOffset(problem.line() - 1));
    }

    /** Anfang der HiveMind-Meldungen (aus dem Original-Bundle {@code ParseStrings}), die nicht gemeldet werden. */
    private static List<String> skippedMessagePrefixes() {
        final ResourceBundle strings = ResourceBundle.getBundle("org.apache.hivemind.parse.ParseStrings", java.util.Locale.ROOT,
                XmlResourceProcessor.class.getClassLoader());
        final List<String> prefixes = new ArrayList<>();
        for (final String key : List.of("unexpected-element", "unknown-attribute", "bad-rule-class", "error-reading-descriptor")) {
            final String formatted = new MessageFormat(strings.getString(key)).format(new Object[]{"\u0000", "\u0000", "\u0000", "\u0000"});
            prefixes.add(formatted.substring(0, formatted.indexOf('\u0000')));
        }
        return prefixes;
    }
}
