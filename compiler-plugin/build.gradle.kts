// compiler-plugin/build.gradle.kts — сборка Kotlin compiler plugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20-RC"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
}

dependencies {
    // compileOnly: артефакт предоставляется kotlinc в рантайме.
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20-RC")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // API плагина помечен @ExperimentalCompilerApi
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

tasks.jar {
    archiveBaseName.set("dotnet-compiler-plugin")
    archiveVersion.set("0.1.0-SNAPSHOT")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
