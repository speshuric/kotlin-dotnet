// libraries/stdlib/build.gradle.kts — каркас kotlin-dotnet-stdlib.
//
// Компилируется обычным kotlinc (Kotlin JVM); к пользовательской
// компиляции через наш PE-бэкенд НЕ подключён (этап B плана D-03:
// подстановка вместо родного stdlib через -Xno-stdlib -cp <наш>.jar).
// Пакеты зеркалируют upstream kotlin-stdlib, чтобы неявные импорты
// и диффы с апстримом оставались простыми.
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
