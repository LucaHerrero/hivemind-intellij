package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Ein HiveMind-Modul, d.h. ein Moduldeskriptor mit {@code <module id="..." package="...">}.
 *
 * @param packageName Paket für unqualifizierte Klassennamen; ohne {@code package}-Attribut die Modul-id
 *
 * @param inProject {@code true} für Deskriptoren aus dem Projektinhalt, {@code false} für Bibliotheken (JARs)
 */
public record HiveModule(@NotNull String id, @NotNull String packageName, @NotNull XmlFile file, @NotNull XmlTag root,
                         boolean inProject) {

    /** Vollqualifizierte id eines hier deklarierten Punkts – deklarierte ids sind immer einfach ({@code Mailer} → {@code app.Mailer}). */
    public @NotNull String pointId(@NotNull String localId) {
        return "%s.%s".formatted(id, localId.trim());
    }

    /** Ort für die Anzeige: Dateipfad bzw. bei JARs "name.jar!/META-INF/hivemodule.xml". */
    public @NotNull String location() {
        final var vf = file.getVirtualFile();
        if (vf == null) return file.getName();
        final String path = vf.getPath();
        final int jar = path.indexOf("!/");
        if (jar < 0) return path;
        return "%s%s".formatted(path.substring(path.lastIndexOf('/', jar) + 1, jar), path.substring(jar));
    }
}
