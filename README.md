# HiveMind Support für IntelliJ IDEA

Ein Plugin für Apache HiveMind 1.1, das Dependency-Injection-Framework hinter Tapestry 4. Es läuft ab IntelliJ IDEA 2024.3, in der Community- wie in der Ultimate-Edition. Zusammen mit dem Tapestry-4-Plugin ist es am nützlichsten, es funktioniert aber auch allein.

## Installation

```
gradlew buildPlugin
```

Die ZIP-Datei liegt danach in `build/distributions/`. In IntelliJ unter *Settings → Plugins → ⚙ → Install Plugin from Disk…* auswählen, fertig.

Zum Ausprobieren gibt es `gradlew runIde`. Das startet eine Sandbox-IDE und öffnet direkt das Demo-Projekt aus [`demo/`](demo):

- [`hivemodule.xml`](demo/src/main/resources/META-INF/hivemodule.xml) zeigt, was alles funktioniert, und sollte keine einzige Meldung haben.
- [`fehlerbeispiele.xml`](demo/src/main/resources/META-INF/fehlerbeispiele.xml) enthält absichtlich Fehler, damit man sieht, was gemeldet wird.
- `DemoClient.java` und `HomePage.java` zeigen die Verweise aus Java und Tapestry.

## Was das Plugin kann

### Toolwindow „HiveMind“
Unten im Fenster gibt es eine Liste aller Services, aus dem Projekt und aus den JARs im Classpath. Man sieht Interface, Implementierung, Service-Modell und Modul, kann filtern und sortieren und kommt per Doppelklick zur Definition. Fehlt eine Implementierung, ist das rot markiert. Die Liste aktualisiert sich von selbst, wenn sich etwas ändert.

### Moduldeskriptoren
- Für `hivemodule.xml` und eingebundene Sub-Module gibt es ein Schema, also Completion und Validierung für Tags und Attribute.
- Service-, Configuration- und Schema-ids sind anklickbar und haben Completion. Find Usages und Rename funktionieren über alle Deskriptoren, Java-Code und Tapestry-Spezifikationen hinweg.
- Klassennamen werden so aufgelöst wie in HiveMind selbst, also auch relativ zum Modulpaket.
- Symbole wie `${mail.host}` führen zu ihrem Standardwert, Nachrichten wie `%mail.host` zur Properties-Datei neben dem Deskriptor.
- Bei Contributions und Factory-Parametern schaut das Plugin ins zugehörige Schema. Steht dort zum Beispiel der Translator `service`, wird der Wert als Service-id behandelt.
- Interceptor-Reihenfolgen (`before`/`after`), `<dependency module-id>` und Klassen in `if`-Bedingungen sind ebenfalls anklickbar.

### Prüfungen
Jeder Deskriptor wird im Hintergrund mit dem echten Parser aus HiveMind 1.1.1 geprüft. Man bekommt also dieselben Meldungen wie beim Start der Registry, nur eben schon im Editor. Dazu kommen eigene Prüfungen:

- unbekannte ids und Klassen
- doppelt definierte Service-Points
- private Services, die aus einem anderen Modul verwendet werden
- mehrere überschreibende `<implementation>`-Elemente
- Service-Points ohne Implementierung (nur als Warnung, weil die Implementierung aus einem Modul kommen kann, das im Projekt nicht sichtbar ist)

### Java und Tapestry
- `registry.getService("app.Mailer", Mailer.class)`, `getConfiguration(...)` und `containsService(...)` führen zum Service.
- In Tapestry 4 gilt das auch für `@InjectObject("service:app.Mailer")`, `<inject object="service:app.Mailer"/>` und das Binding-Präfix `hivemind:`.
- Unbekannte ids sind dort nur Warnungen.

Die Aufteilung zum Tapestry-Plugin: Verweise auf HiveMind-Objekte übernimmt dieses Plugin, Application State Objects und alle anderen Binding-Präfixe das Tapestry-Plugin.

### Navigation
Gutter-Icons verbinden Service-Interface und Implementierung mit dem `<service-point>`, und umgekehrt.

## Wie ids und Klassen aufgelöst werden

Wie in HiveMind selbst:

- Eine id ohne Punkt gehört zum eigenen Modul (`Mailer` wird zu `app.Mailer`), eine id mit Punkt ist schon vollständig. Außerhalb der Deskriptoren, also in Java und Tapestry, sind ids immer vollständig.
- Klassennamen werden zuerst so gesucht, wie sie dastehen, und erst danach im Paket des Moduls.
- Ein `<implementation>` überschreibt die Implementierung aus dem `<service-point>`. Tabelle und Gutter zeigen die, die tatsächlich gilt.

## Grenzen

- Maßgeblich ist HiveMind 1.1.1, nicht die Online-Doku. Die beschreibt teilweise schon Dinge aus einer Version, die nie erschienen ist.
- HiveMind bringt weder DTD noch XSD mit. Das Schema im Plugin habe ich nach der offiziellen Doku geschrieben. Ein Test gleicht es mit den Attributen ab, die der Parser von HiveMind kennt.
- `if`-Bedingungen werden nicht ausgewertet, nur die Klassen darin verlinkt.
- Groovy-Moduldeskriptoren werden nicht unterstützt. Das ist Programmcode, aus dem sich statisch kein verlässliches Modell gewinnen lässt.
- Doppelte Service-Points werden nur gemeldet, wenn beide im Projekt liegen. Dasselbe Modul einmal als Quelle und einmal als JAR ist kein Konflikt.

## Entwicklung

- Gebaut wird mit Java 21. Fehlt das JDK, lädt Gradle es selbst herunter.
- Der Gradle-Wrapper bleibt vorerst auf 9.7.1, aus demselben Grund wie beim Tapestry-Plugin: Mit 9.8 übernimmt IntelliJ 2026.2 die Abhängigkeiten nicht richtig ins Projekt.
- `gradlew test` startet die Tests, `gradlew verifyPlugin` prüft die Kompatibilität. Die Tests laufen gegen die echten JARs von HiveMind und Tapestry, nicht gegen nachgebaute Stubs.
- Was es von Apache gibt, kommt von Apache: Validierung, id-Regeln und Bedingungen stammen direkt aus HiveMind. Selbst geschrieben ist nur das Schema und die Anbindung an die IDE.

Zum Code-Stil: `final` an Feldern und lokalen Variablen, nicht an Parametern und Klassen. Meldungen schreibe ich als Format-Strings.

## Releases

Jeder Push auf `master` baut das Plugin, lässt Tests und Verifier laufen und veröffentlicht die ZIP als GitHub Release. Die Version steht in `build.gradle.kts`. Gibt es zu der Version schon ein Release, wird nur gebaut. Für ein neues Release also vorher die Version hochziehen.

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE). Das Plugin enthält Apache HiveMind, Jakarta ORO und Commons Logging, Details stehen in [NOTICE](NOTICE).
