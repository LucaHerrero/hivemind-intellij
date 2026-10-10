// Demo-Projekt für die Sandbox-IDE (gradlew runIde im Plugin öffnet es automatisch).
// Echte Apache-Bibliotheken, damit das Toolwindow "HiveMind" auch die Services aus den JARs zeigt.
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("hivemind:hivemind:1.1.1")
    implementation("hivemind:hivemind-lib:1.1.1")
    implementation("org.apache.tapestry:tapestry-annotations:4.1.6") { isTransitive = false }
    implementation("org.apache.tapestry:tapestry-framework:4.1.6") { isTransitive = false }
}
