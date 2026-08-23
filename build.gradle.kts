// Корневой build пуст: реальная сборка — composite-включение
// kotlin-dotnet-engine (см. settings.gradle.kts). Файл существует, только
// чтобы зафиксировать поведение задач, которые IDEA пытается запускать
// в корне.

// IDEA при синке вызывает `gradle wrapper` в корне и плодит gradlew /
// gradle/wrapper под свою версию Gradle. Сборка проекта всегда идёт через
// kotlin-dotnet-engine/gradlew, поэтому корневой wrapper-таск отключён.
tasks.named("wrapper") {
    enabled = false
}
