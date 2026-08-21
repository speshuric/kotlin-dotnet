# Проект: Компиляция Kotlin → .NET (Proof of Concept)

## Цель
Создать proof-of-concept компилятора, который принимает Kotlin-код и
производит исполняемую .NET-сборку (EXE/DLL), способную выполняться
в .NET runtime.

## Архитектура

### Общий подход: IR → IL-текст → ilasm → .NET-сборка

Pipeline:
  исходный код Kotlin (.kt)
    → компилятор Kotlin (K2, bleeding edge версия)
    → Kotlin compiler plugin с IrGenerationExtension
    → обход IR-дерева, генерация CIL-текста (.il файл)
    → вызов ilasm (утилита из .NET SDK) как подпроцесс
    → .NET EXE/DLL

### Почему так
1. Не нужно реализовывать бинарный PE/CIL формат на Java —
   ilasm делает это за нас.
2. IL-текст человекочитаем — критично для отладки на этапе PoC.
3. Весь pipeline работает в одном процессе (JVM) + один вызов
   внешней утилиты (ilasm).
4. ilasm доступен в .NET SDK на всех платформах (Windows, Linux, macOS).

### Запасной вариант
Если перехват IR через compiler plugin окажется невозможным или
слишком сложным:
  - Плагин сериализует/дампает IR в файл (в любом удобном формате:
    JSON, бинарный klib, текстовый дамп)
  - Отдельная .NET-утилита (C#) читает файл и генерирует сборку
    через dnlib или System.Reflection.Metadata

## Runtime-библиотека (KotlinDotnetRuntime)

### Концепция
Аналог kotlin-stdlib-js для JS: промежуточная библиотека, которая
предоставляет Kotlin-совместимый API поверх .NET BCL. Компилятор
генерирует вызовы к этой библиотеке, а не напрямую к .NET BCL.

### Структура
Runtime-библиотека состоит из двух частей:

1. **C#-часть** (KotlinDotnetRuntime.csproj):
   - Реализует базовые операции, которые сложно или невозможно
     выразить напрямую в IL-генерации (например, сложные операции
     со строками, коллекциями, отражением).
   - Предоставляет .NET-классы с методами, которые компилятор
     будет вызывать.
   - Примеры:
     - `Kotlin.Runtime.Print` — println/print
     - `Kotlin.Runtime.Stdio` — readLine
     - `Kotlin.Runtime.Strings` — строковые операции
     - `Kotlin.Runtime.Collections` — операции с коллекциями
     - Заглушки для нереализованного —
       throw NotImplementedException с именем функции

2. **Kotlin-часть** (опционально, на поздних этапах):
   - Часть stdlib написана на самом Kotlin и компилируется
     нашим компилятором (bootstrapping).
   - На начальном этапе — всё на C#.
   - По мере роста возможностей компилятора, части runtime
     переписываются с C# на Kotlin.
   - Аналог: kotlin-stdlib-js написана на Kotlin и компилируется
     в JS.

### Роль в pipeline
- IL-генератор не маппит `println` → `Console.WriteLine` напрямую.
  Вместо этого генерирует вызов `[KotlinDotnetRuntime]Kotlin.Runtime.Print::println(string)`.
- Runtime-библиотека — предкомпилированная .NET DLL, на которую
  ссылается сгенерированный EXE/DLL.
- Это даёт:
  - Чистое разделение: компилятор генерирует простые вызовы,
    семантика Kotlin живёт в runtime.
  - Лёгкая замена/расширение: добавляем функции в runtime без
    изменения кодогенератора.
  - Bootstrapping: постепенно переписываем runtime с C# на Kotlin.

### Маппинг вызовов stdlib (примеры)

| Kotlin                | Runtime-вызов                                   |
|-----------------------|-------------------------------------------------|
| println(x)            | Kotlin.Runtime.Print.println(object)           |
| print(x)              | Kotlin.Runtime.Print.print(object)             |
| readLine()            | Kotlin.Runtime.Stdio.readLine()                |
| listOf(a, b, c)       | Kotlin.Runtime.Collections.listOf(object[])    |
| mutableListOf(...)    | Kotlin.Runtime.Collections.mutableListOf(...)   |
| mapOf(...)            | Kotlin.Runtime.Collections.mapOf(...)          |
| setOf(...)            | Kotlin.Runtime.Collections.setOf(...)          |
| "str".length          | property access (string.Length в .NET)          |
| "str".substring(a,b)  | Kotlin.Runtime.Strings.substring(...)           |
| array.size            | property access (array.Length в .NET)          |
| list.size             | property access (list.Count в .NET)           |
| list.map { ... }      | Kotlin.Runtime.Collections.map(...)             |
| list.filter { ... }  | Kotlin.Runtime.Collections.filter(...)         |

## Источники данных

Подробные ссылки — в [`docs/references.md`](docs/references.md).

- Kotlin: https://github.com/JetBrains/kotlin (v2.4.20-RC, shallow clone в `.sources/kotlin/`)
- .NET runtime: https://github.com/dotnet/runtime (release/10.0, `.sources/dotnet-runtime/`)
- ilasm — из NuGet `ilasm-cli`, `dotnet-ildasm` — NuGet global tool
- Запасной вариант (если IL-текст не подойдёт): dnlib, System.Reflection.Metadata

## Маппинг типов

### Примитивные типы

| Kotlin         | .NET (CIL)                |
|----------------|---------------------------|
| kotlin.Int     | int32 (System.Int32)     |
| kotlin.Long    | int64 (System.Int64)     |
| kotlin.Double  | float64 (System.Double)  |
| kotlin.Float   | float32 (System.Single)  |
| kotlin.Boolean | bool (System.Boolean)    |
| kotlin.Char    | char (System.Char)       |
| kotlin.String  | string (System.String)   |
| kotlin.Unit    | void (System.Void)       |
| kotlin.Any     | object (System.Object)   |
| kotlin.Byte    | int8 (System.SByte)      |
| kotlin.Short   | int16 (System.Int16)     |
| kotlin.UByte   | uint8 (System.Byte)      |
| kotlin.UShort  | uint16 (System.UInt16)   |
| kotlin.UInt    | uint32 (System.UInt32)   |
| kotlin.ULong   | uint64 (System.UInt64)   |
| Nothing        | throw (не возвращается)   |

### Специализированные массивы (примитивные)

| Kotlin           | .NET (CIL)                          |
|------------------|-------------------------------------|
| IntArray         | int32[]                             |
| LongArray        | int64[]                             |
| ShortArray       | int16[]                             |
| ByteArray        | int8[] (System.SByte[])            |
| DoubleArray      | float64[]                           |
| FloatArray       | float32[]                           |
| CharArray        | char[]                              |
| BooleanArray     | bool[]                              |
| UIntArray        | uint32[]                            |
| ULongArray       | uint64[]                            |
| UShortArray      | uint16[]                            |
| UByteArray       | uint8[] (System.Byte[])            |

Примечание: в .NET нет проблемы с примитивными массивами (как на JVM,
где int[] и Integer[] — разные типы). CLR напрямую поддерживает
массивы из примитивов, поэтому IntArray → int32[] — честный unboxed
массив без boxing.

### Обобщённые массивы и коллекции

| Kotlin         | .NET (CIL)                |
|----------------|---------------------------|
| Array<T>       | T[] (где T — reference type) |
| List<T>        | System.Collections.Generic.List<T> |
| Map<K,V>       | System.Collections.Generic.Dictionary<K,V> |
| Set<T>         | System.Collections.Generic.HashSet<T> |
| MutableList<T> | List<T> (та же реализация)|
| Nothing        | throw (не возвращается)   |

## Дженерики: JVM type erasure vs .NET reified generics

Подробности — в [`docs/generics-strategy.md`](docs/generics-strategy.md).

**Кратко:**
- .NET использует reified generics (типы сохранены в runtime), JVM — erasure.
- Variance: `out T` → `+T` (covariant), `in T` → `-T` (contravariant) — маппинг 1:1.
- **ADR 0003:** IR до нас доходит со сохранёнными type arguments (сценарий A).
- `reified` type parameters — фронтенд разворачивает через inline, к нам уже приходит конкретный тип.
- Полная проверка дженериков — Phase 12.

## Что НЕ реализуется в первой версии (PoC)

1. **Корутины (suspend functions)** — заглушка с
   NotImplementedException. State machine transformation —
   этап дальнейшего развития.
2. **Kotlin reflection** — не реализуем.
3. **Inline functions** — инлайнинг выполняется фронтендом
   компилятора (с сохранением reified-типов), до нашего IR-бэкенда.
   Отдельный JIT-инлайнинг — тоже не наша зона.
4. **Multiplatform expect/actual** — только один target (.NET).
5. **Extension functions** — транслируем в статические методы
   с первым параметром = receiver.
6. **Kotlin/Java interop** — не актуально, нет JVM.
7. **Annotation processing (kapt, KSP)** — не реализуем.

## Известные проблемы на стыке JVM/.NET

### Решения для PoC (с фиксацией в adr/)

1. **struct vs class**: всё маппить на reference types (class). TODO: кастомные struct'ы — отдельный механизм.
2. **Generics**: использовать reified generics .NET. ADR 0003 — сценарий A (типы сохранены). Полная проверка — Phase 12.
3. **Null safety**: non-null reference types не проверяются в runtime; nullable reference types → просто .NET reference type; nullable value types (Int?) → `System.Nullable<int32>`. TODO: .nullable annotations.
4. **Delegation (by)**: транслировать в композицию (поле-делегат + проброс методов).
5. **Data class**: генерировать equals/hashCode/toString/copy/componentN вручную в IL.
6. **Companion object**: статические методы/поля на классе + вложенный класс с singleton.
7. **Sealed class**: .NET sealed class + private constructor + вложенные классы-наследники.
8. **Object declaration (singleton)**: статическое поле с lazy/eager init в static constructor.
9. **Lateinit**: field без инициализатора (default null для reference types).
10. **Reified generics**: фронтенд разворачивает через inline, к нам приходит развёрнутый код.
11. **String interpolation** ("$x ${expr}"): → string.Concat/String.Format (Kotlin lowering обычно уже в concat).
12. **Elvis (?:)**: if-then-else с null-check в IL.
13. **Smart casts**: фронтенд, в IR уже явные cast'ы (IrTypeOperatorCall AS/AS_DYNAMIC).
14. **when with subject**: в IR развёрнуто в if-else (IrWhen + IrBranch).
15. **for loop over range**: Kotlin IR lowering разворачивает в while + iterator.
16. **Tail recursion**: игнорировать для PoC. TODO: `tail.` prefix.
17. **Crossinline/noinline**: обрабатываются фронтендом при инлайнинге.
18. **Contracts**: игнорировать — hints для компилятора, в runtime не нужны.
19. **Специализированные массивы (IntArray и др.)**: прямой маппинг 1:1 → int32[], int64[], и т.д. (в .NET нет проблемы erasure).

## План работ (поэтапный)

> **Статус обновлён: 2026-08-19.** Этапы 0–1 и этап 2 выполнены
> (Phase 0–9 в нумерации коммитов; `for`-loop — TODO Phase 11/G-02).
> См. `CHANGELOG.md` и `adr/`.

> **Выравнивающие задачи (не по фазам):** структурированный план
> рефакторинга и дизайна, не входящего в фиксированные фазы, — в
> [`TODO/README.md`](TODO/README.md). Там же разметка «до/после
> Phase 9», рекомендуемая последовательность и практики для
> исполнителей (production-ready, экономия контекста). Каждая задача
> имеет полный текст в `TODO/<тема>/<NN>-*.md`; ссылка на ADR и
> зависимости указаны в файле задачи. Фаз зафиксированных (9, 10, ...)
> это TODO не касается — они идут по своему плану ниже.

### Этап 0: Исследование и настройка окружения ✅

> **Важно по окружению:** JDK и .NET SDK тащить **локально в проект**,
> не глобально в систему. Не захламлять машину версиями.
> - JDK: локальная установка в `.sdk/jdk/` (Temurin 21)
> - .NET SDK: локальная установка через `dotnet-install.sh --install-dir .sdk/dotnet/`
>   (ilasm — через NuGet `ilasm-cli`, `dotnet-ildasm` — через NuGet)
> - Gradle: локально в `.sdk/gradle/` (9.7.0)
> - В дальнейшем — изоляция через контейнеры (Docker/Podman)

- [x] Клонировать репозиторий kotlin, изучить структуру
      compiler/ir/ → `.sources/kotlin/` (shallow clone v2.4.20-RC)
- [x] Изучить IrGenerationExtension API — `CompilerPluginRegistrar`
      (не устаревший `ComponentRegistrar`), `IrGenerationExtension.generate()`
- [x] Изучить существующие примеры compiler plugins
      (all-open, serialization) — API подтверждён в `.sources/kotlin/`
- [x] **Критично:** проверить, в каком виде IR до нас доходит —
      сохранены ли type arguments в дженериках или уже стёрты?
      → ADR 0003: **сценарий A** (типы сохранены). Для `test_add` проверено;
      полную проверку дженериков — на Phase 12.
- [x] Изучить IL-синтаксис (CIL assembly language):
      - Объявление assembly, module, class, method
      - Базовые инструкции: ldstr, call, callvirt, ret,
        ldarg, stloc, ldloc, br, brtrue, brfalse
      - Объявление точки входа (.entrypoint)
- [x] Установить JDK и .NET SDK локально в проект → `scripts/install-sdks.sh`
- [x] Создать скелет Gradle-проекта для compiler plugin → `compiler-plugin/`
- [x] Создать скелет KotlinDotnetRuntime (C# class library) → `runtime/` (Phase 8)

### Этап 1: "Hello World" (минимальный PoC) ✅
- [x] Написать Kotlin compiler plugin, регистрирующий
      IrGenerationExtension → `DotnetCompilerPluginRegistrar`
- [x] В плагине: перехватить IR для fun main() { println("Hello") }
- [x] Сгенерировать IL-текст (заголовок, класс, метод, `.entrypoint`)
- [x] Создать минимальный KotlinDotnetRuntime с Print.println → `runtime/src/Print.cs`
- [x] Вызвать ilasm для компиляции .il → .exe → `scripts/kotlinc-net.sh`
- [x] Проверить, что EXE запускается и печатает "Hello" → `just test 03-hello`
- [x] Зафиксировать pipeline в скрипте → `kotlinc-net.sh`, `justfile`

### Этап 2: Базовые конструкции (частично ✅)
- [x] Маппинг примитивных типов (Int, Long, Double, Boolean,
      String, Char, Unit) → `TypeMapper.kt`
- [x] Локальные переменные (ldloc, stloc) → Phase 7
- [x] Арифметические операции (add, sub, mul, div, rem) → Phase 7
- [x] Сравнения (clt, cgt, ceq + brtrue/brfalse) → Phase 7
- [x] If/when expressions → ветвления в IL → Phase 7
- [x] Циклы (while/do-while, break/continue) → br/brtrue/brfalse циклы → **Phase 9**
      (for — TODO, требует итераторы Phase 11/G-02)
- [x] Вызовы пользовательских функций: top-level functions → static methods → **Phase 9**
- [x] Возврат значений (ret с значением) → Phase 7
- [x] Строковые литералы и интерполяция → string.Concat → **Phase 9**
- [ ] Специализированные массивы (IntArray, LongArray, и т.д.)
      → int32[], int64[], и т.д. → **Phase 11**
- [ ] Расширить runtime: Stdio.readLine, базовые строковые операции → **Phase 11**

### Этап 3: Классы и ООП
- [ ] Объявление классов в IL (.class)
- [ ] Поля (instance и static)
- [ ] Конструкторы (.ctor — instance и static)
- [ ] Методы (instance и static)
- [ ] Интерфейсы (.interface)
- [ ] Наследование (extends, implements)
- [ ] Abstract методы (abstract в IL)
- [ ] Data classes (генерация equals/hashCode/toString/copy)
- [ ] Companion objects (→ static methods + nested class)
- [ ] Enums (→ .NET enum или класс со статическими полями)
- [ ] Object declarations (singleton → static field + lazy init)
- [ ] Extension functions (→ static methods с receiver
      первым параметром)
- [ ] Nullable типы: T? для value types → Nullable<T>,
      для reference types — без изменения
- [ ] Дженерики: объявление generic классов и методов в IL
- [ ] Variance: out → +, in → − в IL

### Этап 4: Коллекции и стандартная библиотека (через runtime)
- [ ] Array<T> → T[] (CLR array)
- [ ] IntArray/LongArray/и т.д. → int32[]/int64[]/и т.д.
- [ ] List<T>/MutableList<T> → System.Collections.Generic.List<T>
- [ ] Map<K,V>/MutableMap<K,V> → Dictionary<K,V>
- [ ] Set<T>/MutableSet<T> → HashSet<T>
- [ ] Базовые операции: add, get, set, size, contains,
      iterator, map, filter, forEach — через runtime
- [ ] listOf(), mutableListOf(), mapOf(), setOf() —
      фабричные методы в runtime
- [ ] Строковые операции: length, substring, split, trim,
      + (concat), equals — через runtime
- [ ] Ranges (..) → for loop с инкрементом или .NET Range
- [ ] in/!in → Contains() call
- [x] println/print → Kotlin.Runtime.Print → Phase 8
- [ ] readLine → Kotlin.Runtime.Stdio
- [ ] Остальное из kotlin.* → заглушки в runtime
      (NotImplementedException с именем функции)

### Этап 5 (вне PoC): Корутины
- [ ] State machine transformation для suspend functions
- [ ] Интеграция с .NET async/await или своя машина состояний
- [ ] runBlocking, launch, async — заглушки или минимальная
      реализация

### Этап 6 (вне PoC): Bootstrapping runtime
- [ ] Переписать части KotlinDotnetRuntime с C# на Kotlin
- [ ] Компилировать Kotlin-часть runtime нашим компилятором
- [ ] Постепенно увеличивать долю Kotlin, уменьшать долю C#

### Дальнейший план (по фазам, с приоритетами)

| Phase | Что | Зависимости | Приоритет |
|---|---|---|---|
| **9** | Циклы (for/while) + вызовы пользовательских функций + строковая интерполяция | — | высокий |
| **10** | Классы (минимальные): `.class`, поля, `.ctor`, instance-методы, `newobj` | — | высокий |
| **11** | Массивы (`int32[]`) + `readLine` + базовые строки (length, substring, +) | runtime расширение | средний |
| **12** | Nullable (`Nullable<T>`) + дженерики (минимальные) | ADR 0003 проверка на реальном дженерике | средний |
| **13+** | Data classes, companion objects, object declarations, enums, extension functions, коллекции (List/Map/Set через runtime) | runtime расширение | низкий |

**Phase 9 — выполнена** (циклы + функции + интерполяция, коммит `6a86837`):
- `IrWhileLoop` / `IrDoWhileLoop` → IL-цикл с метками (`br`/`brfalse`).
- `IrBreak` / `IrContinue` → `br` к меткам выхода/итерации (стек `LoopContext`).
- Вызовы пользовательских top-level функций → `call int32 org.example.math.OtherKt::foo(int32)`.
- Рекурсия (прямая) — работает автоматически (статический `call`).
- Строковая интерполяция `"$x ${expr}"` → `string.Concat` (IR — `IrStringConcatenation`).
- `for (i in 0..10)` — оставлен как TODO (требует итераторов — Phase 11 / G-02).
- Спец-поддержка: `IrTypeOperatorCall` (`IMPLICIT_COERCION_TO_UNIT` → `pop`),
  `IrComposite`, operator `inc`/`dec` (→ `±1`), экранирование CIL-опкод-имён
  (`box` → `'box'`).
- **Полный план Phase 9** (задачи 9.1–9.8, IR-наблюдения, IL-паттерны,
  приёмка) — в [`TODO/PHASE-9.md`](TODO/PHASE-9.md). Перенос spec-тестов
  while/do-while из kotlin-компилятора — задача 9.8 (4 теста, все "OK").
- Тестовый проект `04-loops/` (+ `just test 04-loops`, `just test 04-loops-spec`).

**Следующая сессия — Phase 10** (классы):
- `.class`, поля (instance/static), `.ctor` (instance/static), `newobj`.
- Instance-методы (`callvirt`), `IrConstructorCall`, `IrGetField`/`IrSetField`.
- `IrClass` → IL `.class`, наследование (`extends`/`implements`).
- TODO: план `TODO/PHASE-10.md` (предстоит написать).

## Структура проекта (актуальная)

```
kotlin-dotnet/
├── .sdk/                        # Локальные SDK (не в git)
│   ├── jdk/                      # JDK Temurin 21
│   ├── kotlinc/                  # kotlin-compiler-2.4.20-RC.zip
│   ├── dotnet/                   # .NET 10 SDK + ilasm-cli + dotnet-ildasm
│   └── gradle/                   # Gradle 9.7.0
├── .sources/                    # Локальные исходники для референса (не в git)
│   ├── kotlin/                   # shallow clone JetBrains/kotlin @ v2.4.20-RC
│   └── dotnet-runtime/           # shallow clone dotnet/runtime @ release/10.0
├── .gitignore                    # .sdk/, .sources/, build/, *.dll, *.exe, .kotlin/
├── kotlin-dotnet-engine/         # Gradle-корень JDK-области (проекты на Kotlin)
│   ├── build.gradle.kts            # корневой build
│   ├── settings.gradle.kts         # include: :compiler-plugin, :dotnetutils
│   ├── gradle.properties           # toolchain: ../.sdk/jdk (от корня engine)
│   ├── gradle/wrapper/             # Gradle wrapper 9.7.0
│   ├── gradlew, gradlew.bat
│   ├── compiler-plugin/            # Kotlin compiler plugin (K2 IR)
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── kotlin/org/kotlindotnet/compiler/
│   │       │   ├── DotnetCompilerPluginRegistrar.kt  # CompilerPluginRegistrar (K2)
│   │       │   ├── DotnetCommandLineProcessor.kt     # CLI-опция output.dir (ADR 0007)
│   │       │   ├── DotnetIrGenerationExtension.kt    # IrGenerationExtension
│   │       │   ├── DotnetIrVisitor.kt                # IrVisitor → IL
│   │       │   ├── TypeMapper.kt                     # Kotlin → CIL типы
│   │       │   ├── NameMapper.kt                     # package → namespace, <File>Kt
│   │       │   ├── StdlibResolver.kt                 # println/print → runtime
│   │       │   ├── il/                               # IL-эмиттер (A-03/A-04)
│   │       │   │   ├── IlEmitter.kt                  # интерфейс
│   │       │   │   ├── TextIlEmitter.kt              # реализация (CIL-текст)
│   │       │   │   ├── IlOpcode.kt                   # enum опкодов
│   │       │   │   └── IlContext.kt                  # стек контекстов
│   │       │   ├── ir/                               # IR-константы (A-05)
│   │       │   │   ├── IrOrigins.kt                  # debugName IR-origin
│   │       │   │   └── KotlinOperators.kt            # имена operator-функций
│   │       │   └── util/
│   │       │       └── Log.kt                        # логгер (stderr)
│   │       └── resources/META-INF/services/
│   │           ├── org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
│   │           └── org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
│   └── dotnetutils/                # Порт write-path SRM → Kotlin (без ilasm)
│       ├── build.gradle.kts          # чистый kotlin-stdlib, без java.* в исходниках
│       └── src/main/kotlin/org/kotlindotnet/dotnetutils/
│           └── system/reflection/metadata/   # MetadataBuilder и окружение (ADR 0009)
├── justfile                        # Единая точка входа: just bootstrap/test/compile
├── runtime/                        # KotlinDotnetRuntime (C# class library)
│   ├── KotlinDotnetRuntime.csproj
│   └── src/Print.cs              # Kotlin.Runtime.Print (println/print)
├── test-projects/                # Тестовые Kotlin-проекты
│   ├── 00-int-add/               # test_add(Int,Int): Int → DLL + C# consumer
│   ├── 02-expr/                  # 16 выражений → DLL + C# consumer
│   ├── 03-hello/                 # fun main() { println(...) } → EXE
│   └── 04-loops/                 # циклы/функции/интерполяция → EXE + spec-тесты
├── scripts/
│   ├── common.sh               # общие функции (source-only)
│   ├── activate.sh             # активация env (JAVA_HOME, DOTNET_ROOT, ...)
│   ├── deactivate.sh
│   ├── install-sdks.sh         # установка JDK/kotlinc/.NET/Gradle в .sdk/
│   ├── install-sources.sh      # shallow clones в .sources/
│   ├── kotlinc-net.sh          # CLI: .kt → .exe/.dll (per-test layout)
│   ├── tests.sh                # реестр тестов (source-only, ADR 0008)
│   ├── build-test.sh           # сборка + верификация одного теста
│   ├── build.sh                # диспетчер: plugin | runtime | all
│   ├── test.sh                 # диспетчер тестов: selector → id → build-test.sh
│   ├── show.sh                 # il | ir | disasm × last | all | <testid> | <glob>
│   └── clean.sh                # all | build | sdk | sources
├── discussions/                  # Архив чатов и логов (gitignored кроме README)
├── docs/                         # il-reference.md, references.md, generics-strategy.md
├── TODO/                         # Выравнивающие задачи + PHASE-9.md (см. TODO/README.md)
├── adr/                          # Architecture Decision Records
│   ├── 0001-pipeline-ir-to-il.md
│   ├── 0002-runtime-library.md
│   ├── 0003-ir-state-at-interception.md
│   ├── 0004-net10-kotlin24-versions.md
│   ├── 0005-name-mapping.md
│   ├── 0006-just-as-build-runner.md
│   ├── 0007-output-dir-via-cli-option.md
│   └── 0008-justfile-refactor-per-test-layout.md
├── AGENTS.md                     # Этот файл
├── CLAUDE.md -> AGENTS.md        # Симлинк
├── CHANGELOG.md
└── README.md
```

## Принципы и ограничения

1. **Быстрее и проще — лучше, чем правильно и долго.**
   Это PoC.
2. **Всё, что не критично для базовой работы — гасить через
   NotImplementedException** (в runtime-библиотеке) с понятным
   сообщением, включающим имя функции/конструкции.
3. **Фиксировать все "грязные" решения** в adr/ (Architecture
   Decision Records) с пометкой TODO и описанием правильного решения.
4. **Не пытаться реализовать всю стандартную библиотеку Kotlin.**
   Достаточно: примитивы, коллекции, строки, print/readLine.
5. **Один target**: .NET 8+ (или 9, bleeding edge).
6. **Версия Kotlin**: bleeding edge (последний stable или
   dev-сборка).
7. **Не встраиваться в Gradle Kotlin Multiplatform** как
   официальный target — пока отдельный инструмент/плагин.
8. **IL-текст — основной формат вывода** (человекочитаемый,
   отлаживаемый). Бинарный PE — через ilasm.
9. **Каждый этап — с тестовым проектом**, который можно
   скомпилировать и запустить.
10. **Сохранять работоспособность предыдущих этапов** при
    добавлении новых возможностей (регрессионные тесты).
11. **Runtime-библиотека — промежуточный слой** между
    сгенерированным кодом и .NET BCL. Компилятор генерирует
    вызовы к runtime, runtime делегирует в BCL.
12. **Bootstrapping**: постепенно переписывать runtime с C#
    на Kotlin, по мере роста возможностей компилятора.
13. **Локальные SDK**: JDK и .NET SDK устанавливаются локально
    в проект (`.sdk/`), не глобально. В дальнейшем — контейнеры.
14. **Коммиты — только по явному указанию пользователя.**
    Не делать `git commit` (или `amend`, `push`, PR) без прямой
    команды. Сначала показать `git status` + `git diff --cached` /
    `git add -n` для проверки стейджа. Это правило касается и
    любых других деструктивных/публичных операций над git.
15. **DSH-изоляция (read-only корневая ФС).** Команды запускаются
    в песочнице, где корневая файловая система пользователя —
    read-only (нельзя писать в `~/.local/`, `/run/user/<uid>/`,
    `~/.gradle/` и т.п.). Из-за этого «из коробки» падают:
    - `just` — не может создать temp-директорию в
      `/run/user/<uid>/just/` (read-only).
    - `kotlinc` / Kotlin daemon — пытается писать маркер в
      `~/.local/share/kotlin/daemon/` (read-only → исключение
      `FileSystemException`, но компиляция при этом может
      пройти — это best-effort warning daemon'а).
    - `dotnet` CLI (C#-consumer-тесты 00/02) — не может
      определить домашнюю директорию → «The user's home
      directory could not be determined».
    - `./gradlew` — кэш Gradle по умолчанию в `~/.gradle/`
      (read-only).

    **Обязательный prelude** для любой команды сборки/тестов
    (иначе `just`/`gradlew` падают на read-only ФС):
    ```bash
    cd /home/speshuric/tmp/kotlin-dotnet
    source scripts/activate.sh
    export GRADLE_USER_HOME="$PWD/build/tmp/gradle-home"
    export XDG_RUNTIME_DIR="$PWD/build/tmp/runtime"
    export HOME="$PWD/build/tmp/home"
    export DOTNET_CLI_HOME="$HOME"
    mkdir -p "$GRADLE_USER_HOME" "$XDG_RUNTIME_DIR" "$HOME"
    ```

    Назначение переменных:
    - `GRADLE_USER_HOME` — редирект кэша Gradle в writable
      `build/tmp/gradle-home` (вместо read-only `~/.gradle/`).
    - `XDG_RUNTIME_DIR` — writable tmp для `just`
      (`/run/user/<uid>/` — read-only).
    - `HOME` — writable home для `kotlinc`/Kotlin daemon
      (`~/.local/share/kotlin/...` → `build/tmp/home/.local/...`).
    - `DOTNET_CLI_HOME` — writable home для `dotnet` CLI
      (нужен для C#-consumer-тестов 00-int-add/02-expr, где
      `dotnet run` разворачивает NuGet-restore в HOME).

    Это не особенность проекта, а особенность среды исполнения
    (DSH). В «нормальном» окружении (без изоляции) эти
    переменные не нужны. Но в DSH-сессиях — обязательны.

    > **Примечание (ADR 0008):** сборочные скрипты
    > (`scripts/build-test.sh`, `build.sh`, `test.sh`, `show.sh`,
    > `clean.sh`, `kotlinc-net.sh`) инкапсулируют этот prelude
    > внутри себя (`source common.sh` + экспорт
    > `GRADLE_USER_HOME`/`XDG_RUNTIME_DIR`/`HOME`/`DOTNET_CLI_HOME`
    > в writable `build/tmp/`). Поэтому `just build`/`just test`/
    > и т.д. работают без ручного prelude. Ручной prelude (выше)
    > нужен только при запуске `just`/`gradlew`/`kotlinc`/`dotnet`
    > напрямую из шелла в обход скриптов.

## Формат commit message

Использовать следующий шаблон:

```text
Заголовок commit

Описание (тело) commit. Может быть в несколько строк.
Можно использовать markdown. Можно использовать gitlab/github соглашения, 
но пока работа ведётся локально.

```

- Пустая строка после заголовка обязательна
- Заголовок не должен быть длинным. Рекомендуется до 75 символов, но это не жёсткое ограничение.
- Заголовок не должен содержать идентификаторов задач или коды фаз или подобные рабочие идентификаторы. Нельзя ожидать, что читатель истории в контексте что такое "требование `Q-13`"
- Заголовок, формируемый агентом должен быть на английском языке. Пользовательские могут быть на английском или русском языке.
- Заголовок обычно начинается со строчной буквы и первым словом обычно является глагол совершенного вида в инфинитивной форме или в прошедшем времени
- Если грамматически глагол неудобен, то можно использовать общие префиксы:
  - `fix: ` - исправлен недочёт или ошибка
  - `neat: ` - исправлен мелкий недочёт или незначительная ошибка или опечатка
  - `doc: ` - изменения только документации, при этом предполагается что документации стало больше
  - `cleanup: ` - изменения, которые не носят функциональный характер, обычно кода/файлов становится меньше
  - `new: `, `add: ` или `feature: ` - для новой функциональности. 
    - `new: ` для нового блока, 
    - `feature: ` для улучшений внутри блока
    - `add: ` для новых функций в блоке функциональности
  - `revert: ` - возврат предыдущего состояния. В теле коммита или в заголовке указать куда. В заголовке - если хватает места.
  - `pick: ` - изменения взяты из другого коммита. В теле коммита или в заголовке указать откуда. В заголовке - если хватает места.
  - `perf: ` - изменения влияют только на производительность
- Тело commit может использовать markdown и соглашения gitlab/github. Сейчас работа идёт вне процессов gitlab/github.
- Для перечисления задач в теле можно использовать bullet-список. Использовать идентификаторы задач можно, но только как дополнение, расшифровка сути должна быть в тексте (суть задачи могла поменяться). Нумерованный список использовать можно от 6-7 пунктов
- Рекомендуется, чтобы тело было самодостаточным (без ссылок на файлы, без внешних ссылок). Но если суть работы прямо зависит от внешнего источника, то внешняя ссылка допустима (например, ссылка на источник спецификации или стандарта, даже если этот стандарт был внесён в проект)
- Тело не должно дублировать diff, а должно пояснять суть задач. 
- В теле могут быть примеры кода или замеры времени, если это поясняет суть.
- Если тело больше 50-100 строк стандартной длины, стоит уточнить, следует ли это вынести в документацию

## Формат IL-текста

Справочник по синтаксису CIL и ключевым инструкциям — в
[`docs/il-reference.md`](docs/il-reference.md).
