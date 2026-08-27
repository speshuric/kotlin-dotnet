// dotnetutils/build.gradle.kts — build for org.kotlindotnet.dotnetutils
//
// Pure-Kotlin library (kotlin-stdlib only, no java.* in the sources):
// the full cycle of producing .NET assemblies (PE DLL/EXE) — a port of the write-path
// System.Reflection.Metadata (see adr/0009-srm-port-to-kotlin.md).
plugins {
    kotlin("jvm") version "2.4.20-RC"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // kotlin.uuid.Uuid is experimental (analysis/02 §E)
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("dotnetutils")
    archiveVersion.set("0.1.0-SNAPSHOT")
}
