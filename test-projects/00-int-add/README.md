# test-projects/00-int-add — минимальный pipeline `test_add`

Проверка end-to-end: Kotlin `test_add(Int, Int): Int` → .NET DLL → C# consumer.

## Этапы

### C# consumer

```bash
just test 00-int-add
# либо вручную:
./gradlew :compiler-plugin:jar
kotlinc -Xplugin=kotlin-dotnet-engine/compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar Arithmetic.kt -d build/kt-out
# плагин пишет build/Arithmetic.dll
cd csharp-test && dotnet run    # печатает 5
```
