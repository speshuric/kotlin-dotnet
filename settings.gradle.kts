// Корень репозитория — не Gradle-сборка по сути, но объявлен composite build,
// чтобы IDE (IntelliJ IDEA) при открытии корня автоматически подхватывала
// движок и не падала с "does not contain a Gradle build".
//
// Вся реальная сборка живёт в kotlin-dotnet-engine/ (compiler-plugin +
// dotnetutils) и запускается её wrapper'ом:
//   cd kotlin-dotnet-engine && ./gradlew <tasks>
// Скрипты проекта (scripts/*.sh, justfile) используют именно этот путь.
//
// Не добавляйте сюда задачи/модули: корень — только точка входа для IDE.

rootProject.name = "kotlin-dotnet"

includeBuild("kotlin-dotnet-engine")
