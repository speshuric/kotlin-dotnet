# H-01: Миграция тестов из kotlin-компилятора

- **Тема:** H. Test migration
- **Разметка:** POST-PoC (после Phase 9–12)
- **Зависимости:** Phase 9 (loops+funcs), Phase 10 (classes), Phase 11 (arrays), Phase 12 (nullable+generics)
- **Статус:** TODO

## Контекст

Пользователь: «Нужно запланировать (после PoC фаз) перенос
диагностических тестов из самого проекта компилятора kotlin в наш
проект».

JetBrains/kotlin содержит большой набор тестов в
`.sources/kotlin/compiler/tests-spec/` (spec-тесты) и
`.sources/kotlin/compiler/testData/` (IR/codegen tests). Это —
огромная база, покрывающая все конструкции языка.

Перенос их в наш проект — способ:
- Валидировать codegen на широком спектре Kotlin-кода.
- Найти угловые случаи, которых мы не预见ли.
- Получить регрессионную сетку.

Но:
- Тесты Kotlin-компилятора заточены под JVM/JS/Native бэкенды.
- Часть из них — FIR-тесты (frontend), неактуальны для нас.
- Часть — codegen tests с JVM-assertions (`.class`-формат, `BytecodeListing`),
  бесполезны для .NET.
- Тестов много (десятки тысяч) — нужен отбор.

## Цель

План миграции: какие тесты переносить, в каком формате, как
запускать, как отмечать TODO/unsupported.

**Не** делать сейчас — только план.

## Задачи

### 1. Инвентаризация типов тестов в kotlin

- `compiler/testData/` — IR/codegen, в т.ч. `ir/`, `codegen/`,
  `box/`.
- `libraries/stdlib/testData/` — stdlib-тесты.
- `compiler/tests-spec/` (spec-test format) — новые spec-тесты
  (Kotlin language spec).
- Для каждого: формат (`.kt` + `.txt` golden / metadata),
  runner-класс, backend-target.

### 2. Отбор

- **Брать:** spec-tests (language-level, backend-agnostic),
  IR-structure tests (если они проверяют IR-дамп — можно
  переиспользовать для сравнения).
- **Не брать:** JVM bytecode golden tests, JS-source tests, Native
  tests, FIR-only tests.
- **Отбор по темам:** циклы, функции, классы, дженерики — по фазам.
  Каждой фазе — свой набор тестов.

### 3. Адаптация формата

- Kotlin-тесты — формат `*.kt` + ожидание (golden output или
  компиляция-success/failure). Для .NET нужно:
  - `*.kt` — исходник.
  - `*.expected` — ожидаемый вывод (stdout) ИЛИ
    `*.expected.il-fragment` (проверка IL).
  - Runner: `kotlinc-net.sh` + `dotnet` run + сравнение.
- Создать `test-projects/kotlin-compiler-tests/` (или
  `tests/imported/`), с подкаталогами по фазам.
- Для неподдержанных конструкций — `@Ignore`-marker (test runner
  skip), не падать.

### 4. Runner

- Простой bash/Kotlin-runner: для каждого `*.kt` —
  `kotlinc-net.sh → dotnet → compare stdout`.
- Интеграция в `justfile`: `just test-imported` (или
  `just test-regression`).
- CI-friendly (exit code, summary).

### 5. Стратегия миграции по фазам

- **После Phase 9 (loops+funcs):** импортировать spec-тесты на
  if/when/loops/functions (без классов). ~100–200 тестов.
- **После Phase 10 (classes):** классы, конструкторы, inheritance.
- **После Phase 11 (arrays):** массивы, ranges.
- **После Phase 12 (nullable+generics):** nullable, дженерики.
- Каждой фазе — свой коммит-пакет с тестами.

### 6. CI / regression

- Импортированные тесты — часть `just test-all`.
- Для unsupported — `@Ignore` с TODO-ссылкой на фазу.
- Golden-файлы — в git, обновляются при изменении codegen (только
  осознанно).

### 7. Документ

Создать `docs/test-migration.md`:
- Источник тестов (`.sources/kotlin/`).
- Критерии отбора.
- Формат адаптации.
- Runner.
- Стратегия по фазам.

## Приёмка (когда дело дойдёт)

- `tests/imported/` (или `test-projects/kotlin-compiler-tests/`)
  существует с подкаталогами по фазам.
- `just test-imported` запускает импортированные тесты.
- Процент passing — растёт по фазам; unsupported — `@Ignore`.

## Заметки исполнителю

- Это **план**, не исполнение. Не начинать миграцию до Phase 12.
- Не тащить **все** тесты — только spec + тематические подборки.
- Размер: kotlin-compiler testdata — гигантский; осторожно с
  контекстом (не читать всё в один агент — делегировать по
  подкаталогам).
