# F-01: PascalCase/camelCase — аннотации @kotlinToPascalCase / @dotnetFromPascalCase

- **Тема:** F. Name-case annotations
- **Разметка:** POST-9 (план + TODO-заглушки), POST-PoC (полная механика)
- **Зависимости:** D-01 (каркас stdlib, где живут аннотации)
- **Статус:** TODO

## Контекст

Пользователь (цитата, сокращено):
> PascalCase/camelCase. Режим буду переключать через аннотации файла.
> Если в файле есть `@kotlinToPascalCase`, то публичные имена, требующие
> PascalCase, должны в dotnet приходить в PascalCase, внутри должно
> идти преобразование. Если в файле есть `@dotnetFromPascalCase`, то
> импорты должны импортить в camelCase. Но пока всю эту механику не
> делаем. Пока появление этих аннотаций должно быть TODO/NotImplemented.
> Это место ещё потребует планирования. Ну и да, референсы потребуют
> поиска по ним и хранение альтернативных имён. Ну и потом надо будет
> завести аннотации типа как `@JSName`.

ADR 0005 зафиксировал verbatim-маппинг (PoC). PascalCase-режим — это
**следующий уровень**, требует:

1. Аннотации на уровне файла.
2. Двустороннее преобразование: public names → PascalCase при эмиссии.
3. Reverse-mapping для импортов (когда ссылаемся на BCL
   `Console.WriteLine` из Kotlin-кода, маппим в `console.writeLine`?).
4. Хранение «альтернативных имён» в references (для resolve при
   вызовах, reflection).
5. `@DotnetName` — аннотация-override (как `@JSName` в Kotlin/JS).

## Цель (сейчас — только план + TODO-заглушки)

1. Декларировать аннотации (в `kotlin-dotnet-stdlib`, см. D-01):
   `@KotlinToPascalCase`, `@DotnetFromPascalCase`, `@DotnetName`.
2. В компилятор-плагине: распознавать наличие этих аннотаций и
   бросать `TODO("[F-01] ...")` (не игнорировать молча).
3. Design doc: как будет работать полная механика (позже).

**Не** реализовывать преобразование имён сейчас.

## Задачи

### 1. Аннотации (в D-01, ссылка сюда)

См. D-01 — аннотации уже декларированы как каркас. Здесь — только
контракт, как компилятор их видит.

### 2. Распознавание в плагине

В `DotnetIrVisitor.visitFile` (или в `DotnetIrGenerationExtension`):
- Прочитать `IrFile.annotations`.
- Если есть `@KotlinToPascalCase` → `TODO("[F-01] @KotlinToPascalCase not implemented")`.
- Если есть `@DotnetFromPascalCase` → `TODO("[F-01] @DotnetFromPascalCase not implemented")`.
- Если есть `@DotnetName` на декларации → `TODO("[F-01] @DotnetName not implemented")`.

Сейчас — просто TODO. Не падать, если аннотаций нет (современный
код их не использует).

### 3. Design doc

Создать `docs/name-case-mapping.md`:
- **`@KotlinToPascalCase` (file-level):**
  - Влияние: public class/method/property имена эмитятся в
    PascalCase (`test_add` → `TestAdd`, `myFunc` → `MyFunc`).
  - Внутренние имена (local vars, private) — остаются camelCase.
  - References: при вызове `foo()` в том же файле —
    reverse-transform `foo` → `Foo` для IL-сигнатуры. Требуется
    хранение map original→emitted для resolve.
- **`@DotnetFromPascalCase` (file-level):**
  - Влияние: импорты (ссылки на BCL, другие сборки) — Kotlin-имя
    `console.writeLine` маппится в `Console.WriteLine`.
  - Требует таблицу camelCase → PascalCase для известных BCL-имён
    (либо конвенцию: `writeLine` → `WriteLine` механически).
- **`@DotnetName("...")` (decl-level):**
  - Явный override имени для конкретной декларации. Аналог
    `@JSName` в Kotlin/JS.
  - В IL: использовать `DotnetName.value` как имя.
  - При вызове `@DotnetName`-декларации — resolver ищет по
    `DotnetName`, не по Kotlin-имени.
- **References и alt-names:**
  - Когда имя преобразовано, в `IrCall.symbol.owner` всё ещё
    Kotlin-имя. Resolver должен: lookup по Kotlin-имени → найти
    `@DotnetName` / PascalCase-transform → emit правильное IL-имя.
  - Хранение: в `NameMapper` — map `<IrSimpleFunction> →
    dotnetName`. Пополняется при обходе деклараций, используется
    при эмиссии вызовов.
- **Когда делать:** полная реализация — POST-PoC (после стабилизации
  базовых фаз), т.к. тянет за собой reference-resolution refactor.

### 4. ADR

Создать `adr/0014-name-case-annotations.md` (имя 0010 занято —
`adr/0010-compiler-plugin-pe-backend.md`):
- Статус: Proposed (план, не реализовано).
- Контракт аннотаций (см. выше).
- Связь с ADR 0005 (verbatim — baseline; эти аннотации — opt-in).

## Приёмка

- `@KotlinToPascalCase`/`@DotnetFromPascalCase`/`@DotnetName`
  декларированы в `kotlin-dotnet-stdlib` (D-01).
- Компилятор-плагин распознаёт их наличие → `TODO("[F-01] ...")`.
- `docs/name-case-mapping.md` + `adr/0010` описывают дизайн.
- `just test-all` зелёный (тесты не используют эти аннотации).

## Заметки исполнителю

- **Только план + TODO-заглушки.** Не реализовывать преобразование.
- Аннотации — в D-01, не здесь. Здесь — компилятор-плагин (распознавание
  + TODO) и design doc.
- Пользователь явно сказал: «пока всю эту механику не делаем».
