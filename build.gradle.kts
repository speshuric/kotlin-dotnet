// The root build is empty: real building happens in the composite-included
// kotlin-dotnet-engine (see settings.gradle.kts). This file exists only
// to pin down the behavior of tasks that IDEA tries to run
// in the root.

// During sync, IDEA runs `gradle wrapper` in the root and spawns gradlew /
// gradle/wrapper for its own Gradle version. The project always builds via
// kotlin-dotnet-engine/gradlew, so the root wrapper task is disabled.
tasks.named("wrapper") {
    enabled = false
}
