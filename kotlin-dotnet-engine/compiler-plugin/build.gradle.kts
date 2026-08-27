// compiler-plugin/build.gradle.kts — build for the Kotlin compiler plugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20-RC"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
}

dependencies {
    // compileOnly: the artifact is provided by kotlinc at runtime.
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20-RC")
    // PE backend (ADR 0010): classes are bundled into the plugin JAR (see tasks.jar).
    implementation(project(":dotnetutils"))
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // The plugin API is marked @ExperimentalCompilerApi
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        // Uuid.random() for MVIDs in the PE backend
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

tasks.jar {
    archiveBaseName.set("dotnet-compiler-plugin")
    archiveVersion.set("0.1.0-SNAPSHOT")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Bundle the dotnetutils classes into the plugin JAR — kotlinc loads a
    // single JAR via -Xplugin and expects no separate dependencies.
    val duJar = project(":dotnetutils").tasks.named("jar")
    dependsOn(duJar)
    from(zipTree(duJar.map { it.outputs.files.singleFile }))
}

// IlEmitter contract tests (A-03). Compiled via `:compiler-plugin:compileTestKotlin`,
// but not included in the plugin JAR. Run via `./gradlew :compiler-plugin:test` (once
// JUnit lands); for now — a standalone file with `fun main()` for manual contract checks.
sourceSets {
    test {
        kotlin.srcDir("src/test/kotlin")
    }
}
