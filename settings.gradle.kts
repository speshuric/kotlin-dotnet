// The repository root is not really a Gradle build by itself, but it is declared as a composite build,
// so that the IDE (IntelliJ IDEA) automatically picks up
// the engine when opening the root and does not fail with "does not contain a Gradle build".
//
// All real building lives in kotlin-dotnet-engine/ (compiler-plugin +
// dotnetutils) and is run through its wrapper:
//   cd kotlin-dotnet-engine && ./gradlew <tasks>
// Project scripts (scripts/*.sh, justfile) use exactly this path.
//
// Do not add tasks/modules here: the root is only an entry point for the IDE.

rootProject.name = "kotlin-dotnet"

includeBuild("kotlin-dotnet-engine")
