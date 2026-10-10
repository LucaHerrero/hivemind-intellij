package com.herreromarcos.idea.hivemindplugin.references;

import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

/** Registriert die Referenzen in Moduldeskriptoren, Java-Code und Tapestry-Spezifikationen. */
public class HiveMindReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        final DescriptorReferenceProvider descriptor = new DescriptorReferenceProvider();
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), descriptor);
        registrar.registerReferenceProvider(XmlPatterns.xmlTag(), descriptor);

        final InjectionReferenceProvider injection = new InjectionReferenceProvider();
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), injection);
        registrar.registerReferenceProvider(PsiJavaPatterns.literalExpression(), injection);
    }
}
