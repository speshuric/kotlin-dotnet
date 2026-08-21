// dotnetutils/build.gradle.kts — сборка org.kotlindotnet.dotnetutils
//
// Чисто-Kotlin библиотека (только kotlin-stdlib, без java.* в исходниках):
// полный цикл создания .NET-сборок (PE DLL/EXE) — порт write-path
// System.Reflection.Metadata (см. adr/0009-srm-port-to-kotlin.md).
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
