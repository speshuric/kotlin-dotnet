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
    // PE-бэкенд (ADR 0010): классы бандлятся в plugin JAR (см. tasks.jar).
    implementation(project(":dotnetutils"))
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // API плагина помечен @ExperimentalCompilerApi
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        // Uuid.random() для MVID в PE-бэкенде
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

tasks.jar {
    archiveBaseName.set("dotnet-compiler-plugin")
    archiveVersion.set("0.1.0-SNAPSHOT")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Бандлим классы dotnetutils внутрь plugin JAR — kotlinc грузит
    // один JAR через -Xplugin, отдельных зависимостей не ждёт.
    val duJar = project(":dotnetutils").tasks.named("jar")
    dependsOn(duJar)
    from(zipTree(duJar.map { it.outputs.files.singleFile }))
}

// Тесты контракта IlEmitter (A-03). Компилируются через `:compiler-plugin:compileTestKotlin`,
// но не входят в plugin JAR. Запускать через `./gradlew :compiler-plugin:test` (когда
// появится JUnit); пока — отдельный файл с `fun main()` для ручной проверки контракта.
sourceSets {
    test {
        kotlin.srcDir("src/test/kotlin")
    }
}
