import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// Gradle-Version (gradle/wrapper): wie beim Tapestry-Plugin bewusst 9.7.1 – IntelliJ 2026.2 übernimmt mit
// Gradle 9.8.x die Abhängigkeiten des main-Source-Sets nicht ins IDE-Modell.
group = "com.herreromarcos.idea"
version = "0.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    // Apache HiveMind selbst statt Nachbauten: DescriptorParser (Validierung), IdUtils, Parser der if-Bedingungen.
    // Das POM von hivemind 1.1.1 deklariert keine Abhängigkeiten → die benötigten Apache-Bibliotheken explizit:
    // ORO (Id-/Versionsformate im DescriptorParser) und Commons Logging (API von ErrorHandler).
    implementation("hivemind:hivemind:1.1.1")
    implementation("oro:oro:2.0.8")
    implementation("commons-logging:commons-logging:1.2")

    // Tests laufen gegen die echten JARs: Deskriptoren von hivemind/hivemind-lib, Tapestry-Annotationen
    testImplementation("hivemind:hivemind-lib:1.1.1")
    testImplementation("org.apache.tapestry:tapestry-annotations:4.1.6") { isTransitive = false }
    testImplementation("org.apache.tapestry:tapestry-framework:4.1.6") { isTransitive = false }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    // Keine GUI-Designer-Formulare → Bytecode-Instrumentierung unnötig
    instrumentCode = false

    pluginConfiguration {
        id = "com.herreromarcos.idea.hivemindplugin"
        name = "HiveMind Support"
        version = project.version.toString()
        changeNotes = """
            <b>0.1.2</b>
            <ul>
              <li>Optional <code>neighbor-class</code> on <code>&lt;module&gt;</code>: resolved like any other class name,
                  must be a class (not an interface)</li>
            </ul>
            <b>0.1.1</b>
            <ul>
              <li>Plugin logo</li>
              <li>Gutter icon on <code>&lt;service-point&gt;</code> finds its service directly instead of scanning all services</li>
              <li>Messages no longer depend on the system locale</li>
              <li>Internal clean-up: duplicated code removed</li>
            </ul>
            <b>0.1.0</b>
            <ul>
              <li>First release: tool window with all services, module descriptors, validation with the HiveMind parser,
                  references from Java and Tapestry 4</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create("IC", "2024.3.5")
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Sandbox-IDE öffnet direkt das Demo-Projekt (demo/: Gradle-Projekt mit hivemind, hivemind-lib und Tapestry)
    runIde {
        args(layout.projectDirectory.dir("demo").asFile.absolutePath)
    }

    processResources {
        // mitgelieferte Apache-Bibliotheken → LICENSE und NOTICE gehören ins JAR
        from(files("LICENSE", "NOTICE")) {
            into("META-INF")
        }
    }
}
