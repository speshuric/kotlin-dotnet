# test-projects/00-int-add — минимальный pipeline `test_add`

Проверка end-to-end: Kotlin `test_add(Int, Int): Int` → .NET DLL → C# consumer.

## Этапы

### Phase 2: manual.il (без компилятора Kotlin)

Проверка что `ilasm` работает и runtime не нужен:

```bash
source scripts/activate.sh
cd test-projects/00-int-add
ilasm /dll /output:manual.dll manual.il
ildasm manual.dll | head -40    # инспекция (опционально)
```

### Phase 3: C# consumer manual.dll

```bash
cd csharp-test
dotnet run    # печатает 5
```

### Phase 4–6: compiler plugin → Arithmetic.dll

```bash
# (после реализации плагина)
./gradlew :compiler-plugin:jar
kotlinc -Xplugin=compiler-plugin/build/libs/dotnet-compiler-plugin.jar Arithmetic.kt -d build/kt-out
# плагин пишет build/Arithmetic.il
ilasm /dll /output:build/Arithmetic.dll build/Arithmetic.il
# переключить <HintPath> в csharp-test.csproj на ../../build/Arithmetic.dll
cd csharp-test && dotnet run
```
