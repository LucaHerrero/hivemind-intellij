# HiveMind für IntelliJ IDEA

IntelliJ-Plugin für Apache HiveMind 1.1, das Dependency-Injection-Framework hinter Tapestry 4. Läuft ab IntelliJ IDEA 2024.3 in der Community- und der Ultimate-Edition. Funktioniert allein, ergänzt aber am besten das [Tapestry-4-Plugin](https://github.com/LucaHerrero/tapestry4-intellij).

## Installation

**Über den Marketplace:** *Settings → Plugins → Marketplace*, nach „HiveMind“ suchen und installieren.

**Manuell:** Die ZIP aus den [Releases](https://github.com/LucaHerrero/hivemind-intellij/releases) herunterladen und unter *Settings → Plugins → ⚙ → Install Plugin from Disk…* auswählen.

## Features

### Toolwindow „HiveMind“
- Liste aller Services aus Projekt und Classpath-JARs
- Anzeige von Interface, Implementierung, Service-Modell und Modul
- Filtern, Sortieren und Navigation per Doppelklick
- Markierung fehlender Implementierungen
- Automatische Aktualisierung bei Änderungen

### Moduldeskriptoren
- Mitgeliefertes Schema für `hivemodule.xml` und Sub-Module: Completion und Validierung für Tags und Attribute
- Navigation und Completion für Service-, Configuration- und Schema-ids
- Find Usages und Rename über Deskriptoren, Java-Code und Tapestry-Spezifikationen hinweg
- Auflösung von Klassennamen wie in HiveMind, auch relativ zum Modulpaket
- Navigation von Symbolen (`${mail.host}`) zum Standardwert und von Nachrichten (`%mail.host`) zur Properties-Datei
- Auswertung von Contributions und Factory-Parametern anhand der Translator des Schemas
- Navigation für Interceptor-Reihenfolgen (`before`/`after`), `<dependency module-id>` und Klassen in `if`-Bedingungen
- Unterstützung des optionalen `neighbor-class`-Attributs am `<module>` (Erweiterung, nicht Teil von HiveMind 1.1)

### Prüfungen
- Validierung im Hintergrund mit dem originalen Parser aus HiveMind 1.1.1 – dieselben Meldungen wie beim Start der Registry
- Unbekannte ids und Klassen
- Doppelt definierte Service-Points
- Private Services, die aus einem anderen Modul verwendet werden
- Mehrere überschreibende `<implementation>`-Elemente
- Service-Points ohne Implementierung (als Warnung)

### Java und Tapestry
- Navigation aus `registry.getService(...)`, `getConfiguration(...)` und `containsService(...)`
- Navigation aus `@InjectObject("service:...")`, `<inject object="service:..."/>` und dem Binding-Präfix `hivemind:`

### Navigation
- Gutter-Icons zwischen Service-Interface, Implementierung und `<service-point>`

## Grenzen

- Maßgeblich ist HiveMind 1.1.1, nicht die Online-Doku, die teils eine nie erschienene Version beschreibt.
- HiveMind bringt weder DTD noch XSD mit. Das Schema im Plugin ist nach der offiziellen Doku geschrieben und wird per Test mit dem HiveMind-Parser abgeglichen.
- `if`-Bedingungen werden nicht ausgewertet, nur die Klassen darin verlinkt.
- Groovy-Moduldeskriptoren werden nicht unterstützt.
- Doppelte Service-Points werden nur gemeldet, wenn beide im Projekt liegen.

## Selbst bauen

```
gradlew buildPlugin
```

Die ZIP liegt danach in `build/distributions/`. Mit `gradlew runIde` startet eine Sandbox-IDE mit dem Demo-Projekt aus [`demo/`](demo), `gradlew test` führt die Tests aus, `gradlew verifyPlugin` prüft die Kompatibilität. Benötigt Java 21 (wird bei Bedarf von Gradle heruntergeladen).

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE). Enthält Apache HiveMind, Jakarta ORO und Commons Logging, Details in [NOTICE](NOTICE).
