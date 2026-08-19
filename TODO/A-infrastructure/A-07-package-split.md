# A-07: Разбить org.kotlindotnet.compiler на подпакеты

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9 (чистый рефакторинг пакетов, не меняет поведение)
- **Зависимости:** A-01–A-05, B-01 (выполнены — файлы на месте)
- **Статус:** TODO

## Контекст

Пакет `org.kotlindotnet.compiler` (15 файлов, 1830 строк) вырос после
PRE-9 волн. Файлы разных ответственностей лежат в одном пакете.
Напрашивается вынос IL-модулей в отдельное подпространство.

Текущий состав:
- IL-генерация: `IlEmitter.kt` (interface), `TextIlEmitter.kt` (impl),
  `IlContext.kt` (sealed), `IlOpcode.kt` (enum),
  `IlEmitterContractTest.kt` (test).
- IR-константы: `IrOrigins.kt`, `KotlinOperators.kt`.
- Утилиты: `Log.kt`.
- Точка входа + visitor: `DotnetCompilerPluginRegistrar.kt`,
  `DotnetCommandLineProcessor.kt`, `DotnetIrGenerationExtension.kt`,
  `DotnetIrVisitor.kt`.
- Мапперы: `TypeMapper.kt`, `NameMapper.kt`, `StdlibResolver.kt`.

## Цель

Разбить `org.kotlindotnet.compiler` на подпакеты:

| Файл | Текущий пакет | Новый пакет |
|---|---|---|
| `IlEmitter.kt` | `...compiler` | `...compiler.il` |
| `TextIlEmitter.kt` | `...compiler` | `...compiler.il` |
| `IlContext.kt` | `...compiler` | `...compiler.il` |
| `IlOpcode.kt` | `...compiler` | `...compiler.il` |
| `IlEmitterContractTest.kt` (test) | `...compiler` | `...compiler.il` |
| `IrOrigins.kt` | `...compiler` | `...compiler.ir` |
| `KotlinOperators.kt` | `...compiler` | `...compiler.ir` |
| `Log.kt` | `...compiler` | `...compiler.util` |
| `DotnetCompilerPluginRegistrar.kt` | `...compiler` | `...compiler` (корень) |
| `DotnetCommandLineProcessor.kt` | `...compiler` | `...compiler` (корень) |
| `DotnetIrGenerationExtension.kt` | `...compiler` | `...compiler` (корень) |
| `DotnetIrVisitor.kt` | `...compiler` | `...compiler` (корень) |
| `TypeMapper.kt` | `...compiler` | `...compiler` (корень) |
| `NameMapper.kt` | `...compiler` | `...compiler` (корень) |
| `StdlibResolver.kt` | `...compiler` | `...compiler` (корень) |

## Задачи

1. Создать директории:
   - `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/il/`
   - `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/ir/`
   - `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/util/`
   - `compiler-plugin/src/test/kotlin/org/kotlindotnet/compiler/il/`
2. Переместить файлы (через `git mv` для сохранения истории):
   - `il/`: `IlEmitter.kt`, `TextIlEmitter.kt`, `IlContext.kt`,
     `IlOpcode.kt`.
   - `ir/`: `IrOrigins.kt`, `KotlinOperators.kt`.
   - `util/`: `Log.kt`.
   - test: `IlEmitterContractTest.kt` → `il/`.
3. В каждом перемещённом файле обновить `package` declaration:
   - `package org.kotlindotnet.compiler` → `package org.kotlindotnet.compiler.il`
     (для il/), и т.д.
4. Во всех файлах, остающихся в корне (`DotnetIrVisitor.kt`,
   `DotnetIrGenerationExtension.kt`, `DotnetCompilerPluginRegistrar.kt`,
   `DotnetCommandLineProcessor.kt`, `TypeMapper.kt`, `NameMapper.kt`,
   `StdlibResolver.kt`):
   - Добавить `import org.kotlindotnet.compiler.il.IlEmitter`,
     `import org.kotlindotnet.compiler.il.TextIlEmitter`,
     `import org.kotlindotnet.compiler.il.IlOpcode`,
     `import org.kotlindotnet.compiler.il.IlContext` (где используются).
   - Добавить `import org.kotlindotnet.compiler.ir.IrOrigins`,
     `import org.kotlindotnet.compiler.ir.KotlinOperators` (где используются).
   - Добавить `import org.kotlindotnet.compiler.util.Log` (где используется).
   - Убедиться, что нет wildcard-import'ов без необходимости.
5. В перемещённых файлах обновить обратные импорты, если они
   ссылаются на корневые классы (например, `TextIlEmitter` не должен
   импортить ничего из корня; `IlEmitterContractTest` может
   импортить `TextIlEmitter`, `IlContext`, `IlOpcode` — но они теперь
   в том же `il/` подпакете).
6. **ServiceLoader-файлы** (не трогать содержимое, но проверить, что
   пути не сломались):
   - `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
     — ссылается на `org.kotlindotnet.compiler.DotnetCompilerPluginRegistrar`
     (корень, не перемещён) — не меняется.
   - `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`
     — ссылается на `org.kotlindotnet.compiler.DotnetCommandLineProcessor`
     (корень, не перемещён) — не меняется.
7. `build.gradle.kts` — не меняется (source set `src/main/kotlin`
   включает подпакеты автоматически).

## Приёмка

- `./gradlew :compiler-plugin:jar -q` — без ошибок.
- `just test-all` — зелёный (IL на выходе идентичен).
- `grep -rn 'package org.kotlindotnet.compiler$' compiler-plugin/src/` →
  только корневые файлы (registrar, processor, extension, visitor,
  Type/Name/Stdlib).
- `ls compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/il/` →
  4 файла (IlEmitter, TextIlEmitter, IlContext, IlOpcode).
- `ls compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/ir/` →
  2 файла (IrOrigins, KotlinOperators).
- `ls compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/util/` →
  1 файл (Log).
- ServiceLoader-файлы не изменены (registrar/processor остались в корне).

## Заметки исполнителю

- Используй `git mv` для перемещения файлов (сохраняет историю).
- Это **чистый рефакторинг пакетов**: ни одной строки логики не
  меняется, только `package`/`import`.
- `DotnetIrVisitor.kt` (750 строк) **не разбивать** на подфайлы —
  только обновить импорты. Разбиение visitor'а — отдельная задача
  (POST-9, когда начнёт мешаться).
- Контрактный тест `IlEmitterContractTest` — переместить в
  `src/test/.../il/`, обновить package + импорты.
- После перемещения — проверить, что `emitter.opcode(IlOpcode.ADD)`
  и т.д. работают (импорты `IlOpcode` + `IlEmitter`/`TextIlEmitter`
  добавлены в visitor + extension).
