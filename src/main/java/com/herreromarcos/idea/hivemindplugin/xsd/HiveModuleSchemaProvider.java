package com.herreromarcos.idea.hivemindplugin.xsd;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import com.herreromarcos.idea.hivemindplugin.model.HiveMindRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * Hinterlegt für Moduldeskriptoren (ohne Namespace und DOCTYPE) das mitgelieferte Schema
 * {@code /schema/hivemodule-1.1.xsd} → Tag-/Attribut-Completion und Validierung.
 */
public class HiveModuleSchemaProvider extends XmlSchemaProvider {
    static final String SCHEMA_PATH = "/schema/hivemodule-1.1.xsd";

    @Override
    public boolean isAvailable(@NotNull XmlFile file) {
        return HiveMindRegistry.isDescriptor(file);
    }

    @Override
    public @Nullable XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull PsiFile baseFile) {
        if (!url.isEmpty()) return null;
        final URL resource = HiveModuleSchemaProvider.class.getResource(SCHEMA_PATH);
        final VirtualFile vf = resource != null ? VfsUtil.findFileByURL(resource) : null;
        return vf != null && PsiManager.getInstance(baseFile.getProject()).findFile(vf) instanceof final XmlFile xsd ? xsd : null;
    }
}
