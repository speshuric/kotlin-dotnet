# A-02: DotnetIrGenerationExtension — имя файла и onFailure

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** A-01 (логирование)
- **Статус:** TODO

## Контекст

Файл: `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/DotnetIrGenerationExtension.kt`.

Две проблемы:

### 1. Хардкод имени файла (строки 20, 37)

```kotlin
// строка 20
val dumpFile = File("build/ir-dump.txt")
// строка 37
val ilFile = File(buildDir, "$asmName.il")
```

- `build/ir-dump.txt` — один файл на весь модуль, перетирается между
  файлами (если модуль содержит несколько `.kt`, дамп последнего
  выигрывает). Имя фиксировано, не зависит от модуля/файла.
- `build/<asmName>.il` — имя берётся через `NameMapper.assemblyName(irFile)`,
  что лучше, но всё ещё хардкод директории `build/` в плагине.

Плагин не должен знать про `build/` — это забота build-системы/скрипта
(AGENTS.md §«Принципы»: плагин — только кодогенерация). Плагин должен
писать туда, куда скажут, или хотя бы в директорию, заданную через
`CompilerConfiguration`.

### 2. onFailure без последствий (строка 24)

```kotlin
runCatching {
    val dumpFile = File("build/ir-dump.txt")
    dumpFile.parentFile.mkdirs()
    dumpFile.writeText(moduleFragment.dump())
    System.err.println("[kotlin-dotnet] IR dump: ${dumpFile.absolutePath}")
}.onFailure { e ->
    System.err.println("[kotlin-dotnet] IR dump failed: ${e.message}")
}
```

- `onFailure` просто печатает — но дальше код идёт как ни в чём не бывало.
  Это правильно для dump (отладочный вывод), но паттерн `runCatching{}.onFailure{}`
  без ретраина/статуса — «silent failure». Нужно как минимум:
  - печатать через `Log` (см. A-01),
  - сделать явным, что dump — best-effort, а не часть контракта.
- Для IL-генерации failure должен быть **не silent** — упавший emitter
  должен падать с понятной ошибкой, а не писать в stderr и продолжать.

## Цель

- Убрать хардкод `build/` из плагина: путь к выходу — через
  `CompilerConfiguration` (опция `kotlinc -Xplugin=...` + плагин-аргумент
  `-P plugin:kotlin.dotnet:output.dir=...`). Дефолт — текущее поведение
  (`build/`) для совместимости, но конфигурируемо.
- Имя IR-дампа — по имени модуля или первого файла, не фиксированное
  `ir-dump.txt`.
- `onFailure` для dump — через `Log.warn`, явно помечено best-effort.
- IL-генерация — без `runCatching`; любая ошибка эмиссии бросает
  `IllegalStateException` с контекстом (имя файла, узел).

## Задачи

1. Прочитать ADR 0001 (pipeline) и ADR 0006 (just) — подтвердить, что
   плагин не должен знать про `build/`.
2. Добавить чтение `output.dir` из `CompilerConfiguration` в
   `DotnetCompilerPluginRegistrar` (через `CliOption`), передать в
   `DotnetIrGenerationExtension` через конструктор.
3. В `DotnetIrGenerationExtension.generate()`:
   - `outputDir` = конфиг или `File("build")`.
   - IR-dump имя: `ir-dump-<moduleFragment.name>.txt` (или по первому
     файлу). Один dump на модуль.
   - IL-имя: как сейчас через `NameMapper.assemblyName(irFile)`, но
     в `outputDir`.
4. Заменить `System.err.println` → `Log.info/warn` (см. A-01).
5. Для IL-генерации убрать неявный best-effort: если `irFile.accept`
  бросает — ошибка летит наружу (плагин фейлит компиляцию). Убедиться,
  что тесты `just test-all` это не ломают (сейчас visitor на
  неподдержанных узлах бросает `TODO`, что и есть нужное поведение).
6. Обновить `scripts/kotlinc-net.sh` и `justfile`: при сборке
   передавать `-P plugin:kotlin.dotnet:output.dir=build` (или
   положиться на дефолт).
7. Обновить ADR 0001 (или создать 0007) — зафиксировать, что плагин
   не хардкодит `build/`, а получает output-директорию через конфиг.

## Приёмка

- `grep -n 'build/' compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/DotnetIrGenerationExtension.kt`
  → только в дефолте (fallback), не в коде путей.
- `just test-all` зелёный.
- При падении IL-эмиссии (симулировать TODO-узел) — `kotlinc` падает
  с понятной ошибкой, а не пишет в stderr и не оставляет пустой `.il`.

## Заметки исполнителю

- Контекст для задачи: только `DotnetIrGenerationExtension.kt`,
  `DotnetCompilerPluginRegistrar.kt`, ADR 0001, A-01 (Log). Остальное
  не нужно.
- Не усложнять: конфигурация — одна строка `output.dir`. Не делать
  «профили» или «targets».
