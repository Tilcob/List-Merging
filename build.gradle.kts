import groovy.json.JsonOutput
import io.github.fourlastor.construo.Target

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("io.github.fourlastor.construo") version "2.1.0"
}

group = "github.tilcob.app"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    modularity.inferModulePath.set(true)
}

application {
    mainModule.set("github.tilcob.app.listmerging")
    mainClass.set("github.tilcob.app.listmerging.HelloApplication")
}

javafx {
    version = "17.0.14"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("com.dlsc.formsfx:formsfx-core:11.6.0") {
        exclude(group = "org.openjfx")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val appName = "List-Merging"

construo {
    name.set(appName)
    humanName.set(appName)

    jlink {
        guessModulesFromJar.set(false)
        modules.addAll(
            "java.base",
            "java.desktop",
            "java.logging",
            "jdk.crypto.ec",
            "jdk.unsupported",
            "javafx.controls",
            "javafx.fxml",
            "javafx.graphics",
            "com.fasterxml.jackson.databind"
        )
    }

    targets {
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            icon.set(project.file("icons/logo.png"))
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_x64_windows_hotspot_17.0.15_6.zip")
        }

        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_x64_linux_hotspot_17.0.15_6.tar.gz")
        }

        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            identifier.set("com.github.tilcob.app.$appName")
            macIcon.set(project.file("icons/logo.icns"))
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.15_6.tar.gz")
        }

        create<Target.MacOs>("macX64") {
            architecture.set(Target.Architecture.X86_64)
            identifier.set("com.github.tilcob.app.$appName")
            macIcon.set(project.file("icons/logo.icns"))
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_x64_mac_hotspot_17.0.15_6.tar.gz")
        }
    }
}

val generateIndex by tasks.registering {
    val headersDir = file("src/main/resources/headers")
    val outputFile = file("src/main/resources/headers/index.json")

    inputs.dir(headersDir)
    outputs.file(outputFile)

    doLast {
        if (!headersDir.exists()) {
            println("No headers found in $headersDir")
            return@doLast
        }

        val files = headersDir
            .listFiles()
            ?.filter {
                it.isFile &&
                        it.extension.equals("json", ignoreCase = true) &&
                        !it.name.equals("index.json", ignoreCase = true)
            }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

        val index = JsonOutput.prettyPrint(JsonOutput.toJson(files))
        outputFile.writeText(index)

        println("Generated index.json with ${files.size} entries")
    }
}

tasks.named("processResources") {
    dependsOn(generateIndex)
}
