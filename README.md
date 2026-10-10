# HiveMind Support – IntelliJ IDEA Plugin

Unterstützung für das Dependency-Injection-Framework Apache HiveMind 1.1 in IntelliJ IDEA **2024.3.5** (Build 243, Community & Ultimate; `sinceBuild=243`, kein `untilBuild`). Ergänzt das Plugin *Tapestry 4 Support*, funktioniert aber auch allein.

## Installation

```
gradlew buildPlugin
```
→ `build/distributions/hivemind-intellij-0.1.0.zip`, dann in IntelliJ: *Settings → Plugins → ⚙ → Install Plugin from Disk…*

Zum Ausprobieren in einer Sandbox-IDE: `gradlew runIde` – öffnet direkt das **Demo-Projekt** [`demo/`](demo) (Gradle-Projekt mit hivemind, hivemind-lib und Tapestry 4.1.6). Darin:
- [`hivemodule.xml`](demo/src/main/resources/META-INF/hivemodule.xml): Services, BuilderFactory, privater Service, Override, Schema mit Translatoren, Symbol `${demo.sender}`, Nachricht `%mail.host`, Interceptor-Reihenfolge, Bedingung, ChainFactory aus hivemind-lib – ohne Meldungen
- [`fehlerbeispiele.xml`](demo/src/main/resources/META-INF/fehlerbeispiele.xml): absichtliche Fehler (Versionsformat laut HiveMind-Parser, unbekannte Klasse/Service, privater Service, fehlende Implementierung, ungültige Bedingung)
- `DemoClient.java` (`registry.getService(...)`), `HomePage.java` (`@InjectObject("service:demo.Mailer")`)
- Toolwindow **HiveMind** (unten): alle Services aus Demo und JARs

`DemoProjectTest` stellt sicher, dass die Demo genau das zeigt.

Tests: `gradlew test`. Kompatibilitätsprüfung: `gradlew verifyPlugin`.

**Run-Konfigurationen** (im Projekt `isp` als „HiveMind - …“, im Plugin-Projekt allein ohne Präfix): *Run IDE with Plugin*, *Tests*, *Build Plugin*, *Verify Plugin*.

## Funktionen

### Toolwindow „HiveMind“ (Liste aller Services)
- Tabelle aller Services („Beans“) aller Module – aus dem Projekt und aus JARs im Classpath
- Spalten: Service-ID, Interface, Implementierung, Service-Modell, Modul, Herkunft (Projekt/JAR); fehlende Implementierung rot markiert, private Services gekennzeichnet
- Filterfeld (id, Interface, Klasse, Factory, Modul), sortierbare Spalten, Schalter „Show Library Modules“ (Bibliotheks-Services werden grau dargestellt)
- Detailbereich: Interface, alle Implementierungen (create-instance / invoke-factory, auch aus `<implementation>` anderer Module), Interceptors, Fundstelle – jeweils als Link
- Doppelklick / Enter / F4 → `<service-point>`; Kontextmenü: Go to Interface, Go to Implementation, Copy Service ID
- aktualisiert sich nach PSI- und Classpath-Änderungen selbst

### Moduldeskriptoren (`hivemodule.xml` und Sub-Module)
| Was | Beispiel | Funktion |
|---|---|---|
| Schema | `<module>`, `<service-point>`, `<construct>`, `<rules>` … | mitgeliefertes XSD → Tag-/Attribut-Completion und Validierung; Beschreibungen als Text im Element erlaubt |
| Service-ids | `service-id` an `<invoke-factory>`, `<interceptor>`, `<implementation>` | Strg+Klick, Completion; unbekannt = Fehler |
| Configuration-/Schema-ids | `<contribution configuration-id>`, `schema-id`, `parameters-schema-id` | dito |
| BuilderFactory | `<construct class>`, `set-service`, `set-configuration`, `set-object`, `set-resource`, `event-listener`, `<service>`, `<configuration>`, `<object>`, `<resource>` | dito; nur innerhalb von `hivemind.BuilderFactory` |
| Contributions und Factory-Parameter | `<load service-id="…"/>`, `<task executable="service:…"/>`, `<construct configuration-id="…"/>` der ChainFactory | Bedeutung laut `translator` im Schema des Configuration-Points bzw. `parameters-schema` der Factory: `service`, `service-point`, `configuration`, `object`, `instance`, `class`, `resource` |
| Object-Provider | `service:`, `configuration:`, `service-property:`, `bean:`, `instance:`, `class:` | dito |
| Klassen | `interface`, `create-instance@class`, `construct@class`, `conversion@class`, `create-object@class`, `custom@class` | Auflösung wie HiveMind (siehe unten); Initialisierer (`Impl,a=b`) wird ignoriert; Arrays und primitive Typen (`int[]`, `java.lang.Object[]`) sind erlaubt |
| Module | `<dependency module-id="hivemind"/>` | → `<module id>` |
| Dateien | `<sub-module descriptor>`, `set-resource path`, `<resource>`, Translator `resource` | relativ zum Deskriptor |
| Bedingungen | `if="class org.example.Foo and not (property debug)"` | Klassen navigierbar, fehlende werden nicht gemeldet (gerade das prüft die Bedingung) |
| Interceptor-Reihenfolge | `<interceptor before="*" after="hivemind.LoggingInterceptor, Perf">` | Namen anderer Interceptoren desselben Services (`name`, sonst `service-id`); unbekannt = Warnung |
| Symbole | `value="${mail.host}:25"` | → `<default symbol>` in `hivemind.ApplicationDefaults` (vorrangig) bzw. `hivemind.FactoryDefaults`; `$${…}` ist maskiert; ohne Standardwert keine Meldung (kann aus anderen Symbolquellen kommen) |
| Nachrichten | `%message.key` in `<contribution>`/`<invoke-factory>` | → `hivemodule.properties` und `hivemodule_<locale>.properties` neben dem Deskriptor; unbekannt = Warnung |

Find Usages (Alt+F7), Rename (Umschalt+F6) und Highlight Usages funktionieren auf den ids von `<service-point>`, `<configuration-point>` und `<schema>` – über alle Deskriptoren, Java-Code und Tapestry-Spezifikationen hinweg. Beim Umbenennen bleibt das Modulpräfix qualifizierter Verweise erhalten (`app.Mailer` → `app.Sender`).

Prüfungen:
- **Validierung mit dem Original-Parser**: Jeder Deskriptor wird im Hintergrund mit `XmlResourceProcessor`/`DescriptorParser` aus `hivemind-1.1.1.jar` geparst – dieselben Meldungen wie beim Start der Registry (Pflichtattribute, Format von Modul-ids, Punkt-ids und Versionen, Wahrheitswerte, Aufzählungswerte, doppelte Schemas). `if`-Bedingungen prüft der Parser `org.apache.hivemind.conditional.Parser`.
- **Fehler**: unbekannte ids und Klassen, doppelte Punkte, privater Punkt eines anderen Moduls (referenziert, beliefert oder überschrieben), mehr als ein überschreibendes `<implementation>`, `<construct>` der BuilderFactory ohne `class`, `<parameters-schema>` zusammen mit `parameters-schema-id`
- **Warnungen**: Service-Point ohne Implementierung (kann aus einem im Editor unsichtbaren Modul kommen); mehrere Overrides, wenn `if`-Bedingungen im Spiel sind

### Java und Tapestry 4
- `registry.getService("app.Mailer", Mailer.class)`, `getConfiguration(...)`, `containsService(...)` an `org.apache.hivemind.Registry` / `Module`
- `@InjectObject("service:app.Mailer")` und `<inject object="service:app.Mailer"/>` (ohne `type` bzw. `type="object"`) in .page/.jwc/.application/.library
- Binding-Präfix `hivemind:` in Templates und Spezifikationen, z.B. `value="hivemind:service:app.Mailer"`
- ausgewertet werden die Provider `service:`, `configuration:`, `service-property:` und `instance:`; andere (`infrastructure:`, `app-property:` …) bleiben ohne Referenz
- dort nur Warnungen bei unbekannten ids

**Abgrenzung zum Tapestry-4-Plugin:** Objekt-Referenzen (`inject object`, `@InjectObject`, `hivemind:`) übernimmt dieses Plugin, Application State Objects (`state:`, `@InjectState`, `<inject type="state">`) und alle übrigen Binding-Präfixe das Tapestry-Plugin.

### Navigation
- Gutter-Icon an Service-Interfaces und Implementierungsklassen → `<service-point>`
- Gutter-Icon an `<service-point>` → Interface, Implementierungsklassen, `<implementation>` anderer Module

## Abhängigkeiten statt Eigenbau

Was es von Apache gibt, kommt von Apache; das Plugin liefert diese Bibliotheken unverändert mit (siehe [NOTICE](NOTICE)):

| Bibliothek | Wofür |
|---|---|
| `hivemind:hivemind:1.1.1` (Apache HiveMind) | Validierung mit `DescriptorParser`, Qualifizierung von ids mit `IdUtils`, Prüfung der `if`-Bedingungen mit `conditional.Parser` |
| `oro:oro:2.0.8` (Jakarta ORO) | vom `DescriptorParser` benötigt (Formate von ids und Versionen); das POM von HiveMind 1.1.1 deklariert keine Abhängigkeiten |
| `commons-logging:commons-logging:1.2` (Apache Commons) | API von HiveMind (`ErrorHandler`) |
| IntelliJ-Plattform | Icons (`AllIcons`), Typprüfung primitiver Typen, Properties-Plugin für `%key`-Nachrichten |

Die Tests laufen gegen die echten JARs `hivemind`, `hivemind-lib` (1.1.1), `tapestry-annotations` und `tapestry-framework` (4.1.6) – keine nachgebauten Stubs.

Selbst geschrieben ist nur, wofür es kein Original gibt: das Schema für die Completion (HiveMind liefert weder DTD noch XSD) und die Anbindung an die IDE. Das Schema wird automatisch gegen die Attributtabelle `DescriptorParser.properties` aus `hivemind-1.1.1.jar` geprüft (`ApacheReferenceTest`).

## Wie Dinge gefunden werden
- **Module**: alle Dateien `hivemodule.xml` (Projekt + Classpath, z.B. `hivemind-1.1.jar!/META-INF/hivemodule.xml`) mit Wurzel-Tag `<module id>`, plus rekursiv alle `<sub-module>`-Deskriptoren.
- **ids**: ohne Punkt relativ zum eigenen Modul (`Mailer` → `app.Mailer`), mit Punkt vollqualifiziert – wie `IdUtils.qualify` in HiveMind. Außerhalb der Deskriptoren immer vollqualifiziert.
- **Deklarierte ids** (`<service-point id>` …) sind immer einfach und werden mit der Modul-id verbunden.
- **Paket**: `<module package="...">`, ohne Attribut die Modul-id.
- **Klassennamen**: zuerst wie angegeben, und nur wenn es diese Klasse nicht gibt, mit vorangestelltem Modulpaket – auch mit Punkt (`impl.AdderImpl` im Paket `hivemind.examples` → `hivemind.examples.impl.AdderImpl`).
- **`interface`**: ohne Punkt mit Modulpaket, mit Punkt unverändert; fehlt das Attribut, ist es die id im Modulpaket.
- **Implementierung**: `create-instance@class` bzw. `invoke-factory` (Standard-Factory `hivemind.BuilderFactory`, nur dort `construct@class`); Standard-Modell `singleton`. Ein `<implementation>` **überschreibt** die Implementierung im `<service-point>` – Tabelle und Gutter zeigen die wirksame, die Details alle (default / overridden / override).

## Grenzen / bewusste Entscheidungen
- Maßgeblich ist das Release **1.1.1**, nicht die Online-Doku: Diese beschreibt z.B. `if` an `<sub-module>` aus dem nie erschienenen 1.1.2 – der Parser von 1.1.1 kennt es nicht, das Plugin daher auch nicht.
- HiveMind liefert weder DTD noch XSD. Das Schema `schema/hivemodule-1.1.xsd` ist nach der [offiziellen Dokumentation](https://hivemind.apache.org/hivemind1/) selbst geschrieben (descriptor, rules, BuilderFactory, HiveDoc, Change Log) und wird gegen `DescriptorParser.properties` geprüft.
- Der Original-Parser läuft auch dann, wenn die Datei schon andere Fehler hat (die Plattform würde externe Prüfungen sonst überspringen).
- Meldungen des Original-Parsers, die der Editor bereits zeigt, werden nicht doppelt angezeigt: unbekannte Elemente/Attribute (Schema), nicht wohlgeformtes XML (IDE) und nicht ladbare `<custom>`-Regelklassen (Projektklassen liegen nicht im Classpath der IDE). Die *Struktur* von Contributions und Factory-Parametern prüft es nicht – sie hängt vom jeweiligen Schema ab; dessen Translatoren wertet das Plugin für Referenzen aus. Service-Modelle und Translatoren sind frei (erweiterbar), die eingebauten werden vorgeschlagen.
- Die Editor-Hervorhebung von IntelliJ meldet Text in reinen Element-Inhalten nicht; das zeigt erst *Tools → Validate*. Deshalb prüfen die Tests den Inhaltstyp direkt.
- `if`-Bedingungen werden nicht ausgewertet (nur die Klassen darin verlinkt).
- Nicht unterstützt: Groovy-Moduldeskriptoren (`GroovyModuleDescriptorProvider`). Sie sind Programmcode, der zur Laufzeit ausgeführt wird (das Doku-Beispiel berechnet Klassennamen in einer Hilfsmethode), und müssen im Java-Code von Hand registriert werden – statisch lässt sich daraus kein verlässliches Modell gewinnen.
- Doppelte Punkte werden nur gemeldet, wenn beide im Projektinhalt liegen (gleiches Modul als Quelle und JAR ist kein Konflikt).

## Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE). Mitgelieferte Bibliotheken und ihre Lizenzen: [NOTICE](NOTICE).

## Entwicklung

Wie beim Tapestry-Plugin: Java-21-Toolchain, Gradle-Wrapper bewusst auf 9.7.1, Code-Stil mit `final` an Feldern und lokalen Variablen (nicht an Parametern und Klassen) und Meldungstexten als Format-Strings.

### Aufbau (`com.herreromarcos.idea.hivemindplugin`)
| Paket | Inhalt |
|---|---|
| `model` | **`HiveMindRegistry`** (alle Module, Punkte, Implementierungen, gecacht), **`HiveMindSchemas`** (Translatoren aus Schemas), **`HiveMindClasses`** (Klassenauflösung), `ServiceInfo`, `Point`, `HiveModule`, `ServiceImplementation`, Helfer `HiveMindIds`, `ObjectReference` |
| `references` | `DescriptorReferenceProvider` (Deskriptoren), `InjectionReferenceProvider` (Java/Tapestry), `PointReference` → `PointTarget` (Deklaration für Find Usages/Rename, gefunden über `PointDeclarationSearcher`), `ModuleReference`, `ModuleClassReference`, `InterceptorOrderReference`, `SymbolReference`, `MessageReference` |
| `highlighting` | Annotator (unaufgelöste Referenzen, Sichtbarkeit, Duplikate, fehlende Implementierung) |
| `navigation` | Gutter-Icons |
| `toolwindow` | Toolwindow „HiveMind“: `ServicesPanel`, `ServiceRow` |
| `xsd` | `HiveModuleSchemaProvider` (Schema für Dateien ohne Namespace) |
| `validation` | `HiveMindDescriptorValidator` (ExternalAnnotator mit dem `DescriptorParser` von HiveMind), `TextResource` (Editor-Inhalt als HiveMind-`Resource`) |

Tests (74): `DemoProjectTest` (Demo-Projekt), `ApacheReferenceTest` (Original-Deskriptoren von hivemind, hivemind-lib, tapestry-annotations und allen Modulen von tapestry-framework fehlerfrei; Schema ↔ `DescriptorParser.properties`; Original-Parser im Editor), `DescriptorSupportTest`, `InjectionSupportTest`, `DocumentationExamplesTest` (Beispiele aus der offiziellen Doku), `ServicesPanelTest` (gemeinsame Basis `HiveMindTestCase`).
