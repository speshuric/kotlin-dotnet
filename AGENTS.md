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

### Kotlin-компилятор
- Репозиторий: https://github.com/JetBrains/kotlin
- IR-дерево: compiler/ir/ir.tree/
  - IrElement — базовый класс всех IR-элементов
  - IrTree.kt — спецификация, из которой генерируются классы
  - Ключевые элементы: IrModuleFragment, IrFile, IrClass,
    IrSimpleFunction, IrBlockBody, IrCall, IrConst, IrGetValue,
    IrReturn, IrWhen, IrBranch, IrTypeOperatorCall, и др.
- Существующие бэкенды (для референса):
  - compiler/ir/backend.jvm — JVM bytecode generation
  - compiler/ir/backend.js — JavaScript generation
  - compiler/ir/backend.wasm — WebAssembly generation
  - Kotlin/Native backend (в kotlin-native репозитории) — LLVM
- Сериализация IR:
  - compiler/ir/serialization.common — общий формат
  - compiler/ir/serialization.jvm, serialization.js, serialization.native
- FIR (Frontend IR): compiler/fir/
- IR README: compiler/ir/ReadMe.md

### Kotlin 2.0+ (K2 compiler)
- Документация: https://kotlinlang.org/docs/whatsnew20.html
- K2 стабилен с Kotlin 2.0, используется по умолчанию
- Архитектура: Frontend (FIR) → Backend (IR) → Codegen
- IR используется всеми бэкендами для lowering и оптимизации
- Compiler plugins поддерживаются: all-open, serialization,
  Compose, KSP, и др.

### .NET
- Стандарт формата сборок: ECMA-335
  https://www.ecma-international.org/publications-and-standards/standards/ecma-335/
- Описание формата:
  https://learn.microsoft.com/en-us/dotnet/standard/assembly-format
- .NET runtime: https://github.com/dotnet/runtime
- Roslyn (C# компилятор, референс по codegen):
  https://github.com/dotnet/roslyn
- ilasm — утилита для компиляции IL-текста в сборку
  (входит в .NET SDK)

### Библиотеки для эмиссии .NET-сборок (для запасного варианта)
- dnlib: https://github.com/dnlib/dnlib (C# библиотека,
  создание/чтение/модификация .NET-сборок)
- System.Reflection.Metadata (встроенный .NET API)

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

### Почему .NET reified generics — это преимущество, а не проблема

.NET сохраняет type parameters в runtime (reified), JVM стирает (erasure).
Это значит:
- `List<string>` и `List<int>` — **разные типы** в .NET runtime
- **Не нужны** bridge-методы для covariance/contravariance
- **Не нужны** явные cast'ы — type safety на уровне runtime
- `List<int>` — специализированный тип, **без boxing** для value types

### Variance: маппинг почти 1:1

Kotlin и .NET оба используют **declaration-site variance** (не Java wildcards):

| Kotlin                       | .NET (IL)                           |
|------------------------------|-------------------------------------|
| `class Box<T>`               | `class Box<T>` (invariant)          |
| `interface Producer<out T>`  | `interface Producer<+T>` (covariant)|
| `interface Consumer<in T>`   | `interface Consumer<-T>` (contravariant) |

### Критический вопрос: в каком состоянии IR до нас доходит?

Pipeline компилятора:
```
Frontend (FIR) → IR Generation → IR Lowering → Backend Codegen
                                      ↑
                          JVM-specific passes тут?
```

Нужно выяснить на Этапе 0: **выполняются ли JVM-специфичные lowering-проходы
(type erasure, bridge methods) до того, как наш плагин получит IR?**

Возможные сценарии:
- **A (хороший):** IR сохраняет полные type arguments. Erasure — только в
  JVM codegen. Мы имеем полную информацию о дженериках.
- **B (плохой):** JVM-specific lowering стирает типы в IR до нас.
  Реконструкция крайне сложна.
- **C (рабочий):** IrGenerationExtension позволяет выбрать фазу перехвата
  до erasure-проходов.

Тот факт, что Kotlin/JS и Kotlin/Native работают с IR и поддерживают
reified-подобное поведение, говорит в пользу сценария A или C.

### Проблемные случаи и решения для PoC

1. **Star projection (`Array<*>`, `List<*>`)**:
   - В .NET нет прямого аналога.
   - PoC: маппить на `Array<object>` / `List<object>` с потерей type safety.
   - TODO: корректная обработка через variance-правила .NET.

2. **Use-site variance (`Box<out Number>`)**:
   - Редко, в основном для Java interop (неактуально).
   - PoC: игнорировать, маппить на `Box<object>` с TODO.

3. **`reified` type parameters**:
   - Работают через inline-инлайнинг — фронтенд разворачивает.
   - К нам в IR уже приходит развёрнутый код с конкретным типом.
   - **Не нужно ничего делать.**

4. **Type checks с дженериками (`x is List<String>`)**:
   - В JVM: невозможно из-за erasure (только `x is List<*>`).
   - В .NET: **возможно** через reflection.
   - PoC: если IR содержит generic type check — генерировать runtime check
     через .NET reflection. Если IR уже lowering'нул в erased check —
     всё равно работает, менее точно.

5. **Generic method instantiation**:
   - `fun <T> identity(x: T): T = x`
   - В .NET IL: метод с generic parameter (как в JVM bytecode).
   - Маппинг прямой: `.method <T> T identity(T x)`.

6. **Generic specialization для value types**:
   - JVM: `List<int>` невозможен → `List<Integer>` с boxing.
   - .NET: `List<int>` — специализированный тип, без boxing.
   - Если IR содержит `List<Int>` — генерируем `List<int32>`.
   - Если IR уже lowering'нул в erased `List<Object>` — работает,
     но с boxing. TODO: восстановить type arguments из IR, если возможно.

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

1. **struct vs class**: В .NET есть value types (struct),
   в JVM нет аналога. Для PoC — всё маппить на reference types
   (class). TODO: оптимизация — примитивные типы .NET и так
   value types, для кастомных struct'ов нужен отдельный механизм.

2. **Generics**: JVM — type erasure, .NET — reified generics.
   Для PoC — использовать reified generics .NET (упрощает жизнь:
   не нужно генерировать мостовые методы для covariance/contravariance).
   Критический вопрос для Этапа 0: в каком виде IR до нас доходит
   (сохранены ли type arguments или уже стёрты?).

3. **Null safety**: Kotlin nullable (T?) vs non-null (T).
   В .NET reference types тоже могут быть null. Для PoC:
   - Non-null reference types не проверяются в runtime
     (только в compile-time Kotlin)
   - Nullable reference types → просто .NET reference type
     (без nullable annotation)
   - Nullable value types (Int?) → System.Nullable<int32>
   TODO: добавить .nullable annotations для Roslyn analyzer

4. **Delegation (by)**: Транслировать в композицию —
   поле-делегат + проброс методов.

5. **Data class**: Генерировать equals, hashCode, toString,
   copy, componentN вручную в IL.

6. **Companion object**: Статические методы/поля на классе.
   Companion object как тип → вложенный класс с singleton,
   доступ через статическое поле.

7. **Sealed class**: .NET sealed class + private constructor
   + вложенные классы-наследники.

8. **Object declaration (singleton)**: Статическое поле
   с lazy init или eager init в static constructor.

9. **Lateinit**: Просто field без инициализатора (default
   null для reference types, исключение при доступе если null).

10. **Reified generics из inline-функций**: В Kotlin reified
    работает через инлайнинг — фронтенд разворачивает.
    К нашему бэкенду уже приходит развёрнутый код.

11. **String interpolation** ("$x ${expr}"):
    → string.Concat или String.Format in IL.
    Kotlin компилятор может lowering'нуть это в StringBuilder
    или concat ещё до нашего бэкенда.

12. **Elvis operator (?:)**:
    → if-then-else с null-check в IL.

13. **Smart casts**: Выполняются фронтендом, в IR уже
    появляются явные cast'ы (IrTypeOperatorCall AS/AS_DYNAMIC).

14. **when with subject**: В IR уже развёрнуто в
    последовательность if-else (IrWhen + IrBranch).

15. **for loop over range**:
    → for i from start to end (IL loop или .NET Enumerable).
    Kotlin IR lowering обычно разворачивает в while +
    iterator или индексированный доступ.

16. **Tail recursion**: Игнорировать для PoC.
    TODO: добавить tail. prefix в IL.

17. **Crossinline/noinline**: Обрабатываются фронтендом
    при инлайнинге.

18. **Contracts (contracts {})**: Игнорировать для PoC —
    это hints для компилятора, в runtime не нужны.

19. **Специализированные массивы (IntArray и др.)**:
    В Kotlin они существуют из-за type erasure на JVM —
    Array<Int> boxed, а IntArray — unboxed.
    В .NET это не проблема: IntArray → int32[],
    LongArray → int64[], и т.д. Прямой маппинг 1:1.

## План работ (поэтапный)

### Этап 0: Исследование и настройка окружения

> **Важно по окружению:** JDK и .NET SDK тащить **локально в проект**,
> не глобально в систему. Не захламлять машину версиями.
> - JDK: через Gradle toolchain или локальная установка в `.sdk/jdk/`
> - .NET SDK: локальная установка через `dotnet-install.sh --install-dir .sdk/dotnet/`
>   (ilasm входит в .NET SDK)
> - В дальнейшем — изоляция через контейнеры (Docker/Podman)

- [ ] Клонировать репозиторий kotlin, изучить структуру
      compiler/ir/
- [ ] Изучить IrGenerationExtension API — как зарегистрировать
      плагин, как получить доступ к IrModuleFragment
- [ ] Изучить существующие примеры compiler plugins
      (all-open, serialization)
- [ ] **Критично:** проверить, в каком виде IR до нас доходит —
      сохранены ли type arguments в дженериках или уже стёрты?
      (Определяет стратегию работы с дженериками)
- [ ] Изучить IL-синтаксис (CIL assembly language):
      - Объявление assembly, module, class, method
      - Базовые инструкции: ldstr, call, callvirt, ret,
        ldarg, stloc, ldloc, br, brtrue, brfalse
      - Объявление точки входа (.entrypoint)
- [ ] Установить JDK и .NET SDK локально в проект (см. замечание выше)
- [ ] Создать скелет Gradle-проекта для compiler plugin
- [ ] Создать скелет KotlinDotnetRuntime (C# class library)

### Этап 1: "Hello World" (минимальный PoC)
- [ ] Написать Kotlin compiler plugin, регистрирующий
      IrGenerationExtension
- [ ] В плагине: перехватить IR для fun main() { println("Hello") }
- [ ] Сгенерировать IL-текст:
      .assembly extern KotlinDotnetRuntime {}
      .assembly MyKotlinApp {}
      .class Program {
        .method public static void Main() cil managed {
          .entrypoint
          ldstr "Hello"
          call void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(string)
          ret
        }
      }
- [ ] Создать минимальный KotlinDotnetRuntime с Print.println
- [ ] Вызвать ilasm для компиляции .il → .exe
- [ ] Проверить, что EXE запускается и печатает "Hello"
- [ ] Зафиксировать pipeline в скрипте (Gradle task или shell script)

### Этап 2: Базовые конструкции
- [ ] Маппинг примитивных типов (Int, Long, Double, Boolean,
      String, Char, Unit)
- [ ] Локальные переменные (ldloc, stloc)
- [ ] Арифметические операции (add, sub, mul, div, rem)
- [ ] Сравнения (clt, cgt, ceq + brtrue/brfalse)
- [ ] If/when expressions → ветвления в IL
- [ ] Циклы (for, while) → br/brtrue/brfalse циклы
- [ ] Вызовы функций: top-level functions → static methods,
      функции в объектах → static methods
- [ ] Возврат значений (ret с значением)
- [ ] Строковые литералы и интерполяция → string.Concat
- [ ] Специализированные массивы (IntArray, LongArray, и т.д.)
      → int32[], int64[], и т.д.
- [ ] Расширить runtime: Stdio.readLine, базовые строковые операции

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
- [ ] println/print → Kotlin.Runtime.Print
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

## Структура проекта (предлагаемая)

```
kotlin-dotnet-compiler/
├── .sdk/                        # Локальные SDK (не в git)
│   ├── jdk/                      # JDK (через Gradle toolchain)
│   └── dotnet/                   # .NET SDK (dotnet-install.sh)
├── .sources/                    # Локальные исходники для референса (не в git)
│   ├── kotlin/                   # shallow clone JetBrains/kotlin
│   └── dotnet-runtime/           # shallow clone dotnet/runtime
├── .gitignore                    # Игнорировать .sdk/, .sources/, build/, и т.д.
├── build.gradle.kts              # Gradle-сборка плагина
├── compiler-plugin/              # Kotlin compiler plugin
│   ├── src/main/kotlin/
│   │   ├── DotnetIrGenerationExtension.kt
│   │   ├── DotnetIrVisitor.kt          # IrElementVisitor
│   │   │                                # для обхода IR
│   │   ├── IlEmitter.kt                # Генератор IL-текста
│   │   ├── TypeMapper.kt               # Kotlin → .NET
│   │   │                                # маппинг типов
│   │   ├── StdlibResolver.kt           # Разрешение вызовов
│   │   │                                # stdlib → runtime
│   │   └── IlasmRunner.kt              # Вызов ilasm
│   └── build.gradle.kts
├── runtime/                      # KotlinDotnetRuntime
│   ├── src/                      # C#-часть runtime
│   │   ├── Print.cs              # println/print
│   │   ├── Stdio.cs              # readLine
│   │   ├── Strings.cs            # строковые операции
│   │   ├── Collections.cs        # коллекции, listOf, etc.
│   │   └── Stubs.cs              # NotImplementedException
│   ├── kotlin/                   # Kotlin-часть runtime
│   │   │                          # (появляется на этапе 6)
│   │   └── ...
│   └── KotlinDotnetRuntime.csproj
├── test-projects/                # Тестовые Kotlin-проекты
│   ├── 01-hello-world/
│   ├── 02-arithmetic/
│   ├── 03-classes/
│   └── 04-collections/
├── docs/                        # Документация проекта
├── adr/                         # Architecture Decision Records
│   ├── 0001-pipeline-ir-to-il.md
│   ├── 0002-runtime-library.md
│   └── ...
├── AGENTS.md                    # Инструкции для AI-агентов
├── CLAUDE.md -> AGENTS.md       # Симлинк для Claude
├── CHANGELOG.md                 # Журнал изменений
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

## Формат IL-текста (краткий справочник)

### Структура .il файла
```
.assembly extern mscorlib {}
.assembly extern KotlinDotnetRuntime {}
.assembly MyKotlinApp {}
.module MyKotlinApp.exe

.class public auto ansi MyKotlinApp.Program
       extends [mscorlib]System.Object
{
  .method public hidebysig static void Main(string[] args) cil managed
  {
    .entrypoint
    ldstr "Hello"
    call void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(string)
    ret
  }
  .method public hidebysig specialname rtspecialname
          instance void .ctor() cil managed
  {
    ldarg.0
    call instance void [mscorlib]System.Object::.ctor()
    ret
  }
}
```

### Ключевые IL-инструкции (шпаргалка)
- Загрузка: ldstr, ldc.i4, ldc.i8, ldc.r4, ldc.r8,
  ldloc, ldarg, ldnull, ldc.i4.0/1
- Сохранение: stloc, starg
- Арифметика: add, sub, mul, div, rem, neg
- Сравнение: clt, cgt, ceq, clt.un, cgt.un
- Ветвление: br, brtrue, brfalse, beq, bne, blt, bgt,
  ble, bge, switch
- Вызовы: call, callvirt, ret
- Объекты: newobj, ldfld, stfld, ldsfld, stsfld,
  isinst, castclass, box, unbox
- Массивы: newarr, ldelem, stelem, ldlen
- Исключения: throw, rethrow, leave, endfilter
- Дженерики: !0, !1 (type params), !!0 (method type params),
  box, unbox.any, constrained.
