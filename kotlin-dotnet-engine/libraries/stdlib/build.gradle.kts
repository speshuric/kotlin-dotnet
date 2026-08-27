// libraries/stdlib/build.gradle.kts — skeleton of kotlin-dotnet-stdlib.
//
// Built with plain kotlinc (Kotlin JVM); it is NOT wired
// into user compilation through our PE backend (stage B of plan D-03:
// substitution for the native stdlib via -Xno-stdlib -cp <our>.jar).
// Packages mirror upstream kotlin-stdlib so that implicit imports
// and diffs against upstream stay simple.
plugins {
    kotlin("jvm") version "2.4.20-RC"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Declarations live in kotlin.* packages, like upstream kotlin-stdlib
        // (which builds with the same opt-out).
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}

tasks.jar {
    archiveBaseName.set("kotlin-dotnet-stdlib")
    archiveVersion.set("0.1.0-SNAPSHOT")
}
