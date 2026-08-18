# D-01: kotlin-dotnet-stdlib — каркас + неявный импорт

- **Тема:** D. kotlin-dotnet-stdlib
- **Разметка:** PRE-10 (каркас + механизм), POST-9 (интеграция с resolver)
- **Зависимости:** A-01 (Log), A-05 (visitor без хардкода)
- **Статус:** TODO

## Контекст

AGENTS.md §«Runtime-библиотека» описывает `KotlinDotnetRuntime` (C#),
но этого недостаточно. Нужен **kotlin-dotnet-stdlib** — Kotlin-проект
(компилируемый нашим же компилятором), аналогичный
`kotlin-stdlib-js`:

- Атрибуты (аннотации), которые распознаёт компилятор:
  `@kotlinToPascalCase`, `@dotnetFromPascalCase` (см. F-01),
  `@DotnetName("...")` (аналог `@JSName`).
- Прокладки (shims) — Kotlin-declarations, которые внутри вызывают
  runtime (C#) или BCL. Например:
  ```kotlin
  // kotlin-dotnet-stdlib
  public fun println(x: Int) {
      // резолвится в [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(int32)
  }
  ```
- Организация — похоже на js/wasm подсистему самого Kotlin:
  неявный импорт (implicit import), чтобы пользовательский код не
  писал `import kotlin.io.println`.

Сейчас:
- `StdlibResolver` (см. D-02) хардкодит `kotlin.io.println` → runtime.
- Нет Kotlin-части stdlib.
- Нет неявного импорта.
- Нет атрибутов.

## Цель (каркас, не полная реализация)

1. Создать **каркас** `kotlin-dotnet-stdlib/` — Kotlin-проект с
   минимальными declarations.
2. Описать механизм **неявного импорта** (design doc + ADR), не
   реализуя полностью.
3. Завести место для атрибутов (пустые `annotation class` —
   TODO/NotImplemented).
4. Не делать полную stdlib — только скелет + план.

## Задачи

### 1. Каркас проекта

Создать:
```
kotlin-dotnet-stdlib/
├── README.md
├── build.gradle.kts           # (позже, когда компилятор сможет собрать)
└── src/
    └── kotlin/
        └── kotlin/
            ├── io/
            │   └── StandardIO.kt    # println/print (shims)
            ├── dotnet/
            │   └── annotations/
            │       ├── KotlinToPascalCase.kt   # @kotlinToPascalCase (TODO F-01)
            │       ├── DotnetFromPascalCase.kt  # @dotnetFromPascalCase (TODO F-01)
            │       └── DotnetName.kt            # @DotnetName (TODO, analog @JSName)
            └── ...
```

`README.md` — цель, статус (skeleton), ссылка на AGENTS.md §Runtime.

### 2. Атрибуты (annotations)

```kotlin
// kotlin/dotnet/annotations/KotlinToPascalCase.kt
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class KotlinToPascalCase

// kotlin/dotnet/annotations/DotnetFromPascalCase.kt
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class DotnetFromPascalCase

// kotlin/dotnet/annotations/DotnetName.kt
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DotnetName(val name: String)
```

Эти атрибуты — **декларации сейчас**. Механика их обработки — TODO
(F-01). Компилятор пока должен их игнорировать (не падать), но
запоминать, что они есть (для будущей реализации).

### 3. Прокладки (shims)

Минимальные:
```kotlin
// kotlin/io/StandardIO.kt
// shim: резолвится компилятором в [KotlinDotnetRuntime]Kotlin.Runtime.Print::println
@DotnetName("Kotlin.Runtime.Print.println")  // hint для resolver
public fun println(x: Int) { /* compiled away */ }

public fun println(x: String) { }
public fun println(x: Double) { }
// ...
public fun print(x: Int) { }
// ...
```

Поскольку компилятор пока не умеет собирать Kotlin-stdlib сам, эти
shims — **декларативные**: их сигнатуры доступны в IR (через
`IrSimpleFunction.kotlinFqName`), а тело — заглушка. `StdlibResolver`
(D-02) маппит fqName → runtime-вызов, тело shim не эмитится.

### 4. Неявный импорт — design doc

Создать `docs/implicit-import.md` (или ADR):
- Как Kotlin/JS делает неявный импорт: компилятор добавляет
  `import kotlin.*` в каждый файл (через `JSDummyInlineabilityChecker`
  и т.п. — посмотреть в `.sources/kotlin/`).
- Для .NET: компилятор-плагин должен на этапе `IrGenerationExtension`
  **inject** объявления из kotlin-dotnet-stdlib в `IrModuleFragment`,
  либо (проще) — резолвить fqName-ы `kotlin.io.*` через
  `StdlibResolver` (как сейчас), не требуя физического импорта.
- PoC-решение (D-02): `StdlibResolver` — таблица fqName →
  runtime-сигнатура. Неявный импорт физически не нужен, пока
  компилятор не умеет собирать stdlib.
- Будущее: когда компилятор сможет собрать kotlin-dotnet-stdlib,
  IR будет содержать реальные declarations, и resolver будет
  ходить по IR, а не по таблице. Зафиксировать этот переход в ADR.

### 5. ADR

Создать `adr/0008-kotlin-dotnet-stdlib.md`:
- Цель: аналог kotlin-stdlib-js.
- Структура: C#-runtime + Kotlin-shims (позже).
- Неявный импорт: пока через resolver (таблица), потом через
  реальный IR.
- Bootstrapping: по мере роста компилятора, shims переписываются с
  «деклараций с заглушкой» на реальные Kotlin-реализации.

## Приёмка

- `kotlin-dotnet-stdlib/` существует, содержит README + каркас.
- Атрибуты декларированы (компилируются `kotlinc`-ом как обычные
  аннотации, если их не трогать плагином).
- `docs/implicit-import.md` или `adr/0008` описывает механизм.
- `just test-all` не сломан (stdlib не подключается к сборке, пока
  компилятор не умеет его собирать).

## Заметки исполнителю

- **Каркас, не реализация.** Цель — занять место в структуре и
  зафиксировать дизайн. Полная stdlib — bootstrapping (POST-PoC).
- Shims — декларации, не тела. Тела будут «compiled away» resolver-ом.
- Не подключать `kotlin-dotnet-stdlib/` к `just plugin` сборке, пока
  компилятор не умеет его собирать (иначе Gradle зафейлится).
