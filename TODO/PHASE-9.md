# Phase 9: Циклы (while/do-while) + вызовы функций + строковая интерполяция

> **Статус:** план. Запуск — следующая сессия.
> **Скоуп:** `IrWhileLoop`, `IrDoWhileLoop`, `IrBreak`, `IrContinue`,
> пользовательские top-level `IrCall`, `IrStringConcatenation`.
> **Опционально:** `for` (требует итераторы, см. G-02).

## IR-наблюдения (дампы)

### while
```kotlin
while (x < 5) { x++ }
```
IR:
```
WHILE label=null origin=WHILE_LOOP
  condition: CALL ... origin=LT ...
  body: BLOCK ... (x++)
```
→ `IrWhileLoop` с `origin=WHILE_LOOP`, поля `condition` (IrExpression) +
`body` (IrExpression). Простая трансляция.

### do-while
IR: `IrDoWhileLoop` — аналогично, но condition проверяется после body.

### break
```kotlin
while (true) { if (i == 3) break; i++ }
```
IR: `BREAK label=null loop.label=null` (внутри body WHILE).
→ `IrBreak` — ссылается на loop через `IrBreak.loop` (symbol).
`label`/`loop.label` — для labeled break/continue.

### continue
→ `IrContinue` — аналогично.

### for (ОПЦИОНАЛЬНО)
```kotlin
for (i in 0..4) { s += i }
```
IR: lowering разворачивает в:
```
BLOCK origin=FOR_LOOP
  VAR FOR_LOOP_ITERATOR name:<iterator> type:kotlin.collections.IntIterator
    CALL ...origin=FOR_LOOP_ITERATOR ... IntRange::iterator()
  WHILE origin=FOR_LOOP_INNER_WHILE
    condition: CALL ...origin=FOR_LOOP_HAS_NEXT ... IntIterator::hasNext()
    body: BLOCK origin=FOR_LOOP_INNER_WHILE
      VAR FOR_LOOP_VARIABLE name:i ... IntIterator::next()
      ... тело цикла ...
```
→ `for` требует `IntIterator` (runtime, Phase 11) или специализированный
range-loop (G-02 дизайн). **Не делать в Phase 9 базовой.**

### Строковая интерполяция
```kotlin
val x = 42; "Hello, $name! x=${x}"
```
IR: `STRING_CONCATENATION type=kotlin.String` с children (CONST String +
GET_VAR). Каждый child — выражение, вычисляемое в `string`.
→ `IrStringConcatenation` — узел с `.arguments` (List<IrExpression>).
IL: эмитить каждый child → box в object → `call string [mscorlib]System.String::Concat(object[])`.
Или: если 2 string-операнда → `call string::Concat(string, string)`.

### Вызовы пользовательских функций
```kotlin
fun double(n: Int): Int = n * 2
val r = sum(double(3), double(4))
```
IR: `CALL '... double (n: kotlin.Int): kotlin.Int ...' origin=null`
с `symbol.owner` → `IrSimpleFunction` в том же файле.
→ Обычный `IrCall` с `origin=null`, но `symbol.owner` — не operator
(не `plus`/`minus`/...), не stdlib (`kotlin.io.*`).
IL: `call <returnType> <Namespace>.<FileKt>::<methodName>(<paramTypes>)`.

## Задачи (рекомендуемая последовательность)

### 9.1 IrWhileLoop → IL-цикл (ОБЯЗАТЕЛЬНО)
- `visitWhileLoop` (сейчас TODO `[B-01] IrWhileLoop — Phase 9`).
- IL-паттерн:
  ```
  br IL_COND
  IL_BODY:
    <body>
    br IL_COND
  IL_COND:
    <condition>
    brtrue IL_BODY
  IL_END:
  ```
  (или `brfalse IL_END` вместо `brtrue IL_BODY` — на усмотрение).
- Метки: `emitter.newLabel()` для `IL_BODY`, `IL_COND`, `IL_END`.
- Стек меток цикла: visitor должен знать `endLabel` (для break) и
  `condLabel`/`bodyLabel` (для continue). Создать структуру
  `LoopContext(val continueLabel: String, val breakLabel: String)`,
  стек `ArrayDeque<LoopContext>`.
- `collectLocals`: обновить, чтобы обрабатывал `IrWhileLoop`
  (condition + body).
- Тест: `while (x < 5) { x++ }` → корректный IL, `x == 5`.

### 9.2 IrDoWhileLoop → IL-цикл с условием в конце (ОБЯЗАТЕЛЬНО)
- `visitDoWhileLoop` (сейчас TODO).
- IL-паттерн:
  ```
  IL_BODY:
    <body>
    <condition>
    brtrue IL_BODY
  IL_END:
  ```
- Тот же `LoopContext` (continue → `IL_BODY`, break → `IL_END`).
- Тест: `do { x++ } while (x < 5)` → корректный IL.

### 9.3 IrBreak / IrContinue (ОБЯЗАТЕЛЬНО)
- `visitBreak` / `visitContinue` (сейчас TODO).
- `IrBreak.loop` → символ целевого цикла; visitor ищет `LoopContext`
  для этого цикла в стеке.
- Unlabeled break/continue: верхний `LoopContext` стека.
- Labeled break/continue: искать по `loop.label` в стеке.
  - Пока: unlabeled — реализовать; labeled — TODO (требует метки на
    `IrWhileLoop.label`, отдельная задача).
- `break` → `br <breakLabel>`.
- `continue` → `br <continueLabel>`.
- Тест: `while (true) { if (i == 3) break; i++ }` → `i == 3`.

### 9.4 Вызовы пользовательских top-level функций (ОБЯЗАТЕЛЬНО)
- В `emitCall` (сейчас `IrCall` обрабатывает только operator + stdlib):
  добавить ветку — если `origin == null` и `symbol.owner` —
  `IrSimpleFunction` (не operator, не stdlib), то это пользовательский
  вызов.
- Маппинг: `symbol.owner` → `IrSimpleFunction`:
  - Имя: `NameMapper.methodName(function)` (verbatim).
  - Класс: `NameMapper.containerClass(file)` — но у нас нет прямого
    доступа к файлу из `IrSimpleFunction`. Использовать
    `IrSimpleFunction.parent` или `declarationContainer` → `IrFile`.
    Или: `symbol.owner.kotlinFqName` → разбор на namespace + className +
    methodName.
  - IL: `call <returnType> <namespace>.<containerClass>::<method>(<paramTypes>)`.
- Аргументы: `emitCallArguments` (как сейчас).
- Рекурсия: прямой `call` работает автоматически (статический call).
- `dispatchReceiverParameter` != null → instance-метод (Phase 10, TODO).
- Тест: `fun double(n: Int) = n*2; fun box() = double(5)` → `10`.

### 9.5 IrStringConcatenation → string.Concat (ОБЯЗАТЕЛЬНО)
- `visitStringConcatenation` (сейчас TODO).
- IR: `IrStringConcatenation` с `.arguments` (List<IrExpression>).
- IL-паттерн (простой): эмитить все arguments (каждый — boxed в object
  если не string), затем `call string [mscorlib]System.String::Concat(object[])`.
  - Для `object[]` — нужен `newarr object`, `stelem.ref` для каждого.
  - Или: для 2 аргументов — `call string::Concat(object, object)`
    (перегрузка без массива).
- Альтернатива: если все arguments — `IrConst<String>` или `IrGetValue`
  типа string — можно через `string.Concat(string, string, ...)`.
- Начать с `Concat(object[])` — универсально.
- `collectLocals`: обновить для `IrStringConcatenation`.
- Тест: `val x = 42; "Hello, $x!"` → `Hello, 42!`.

### 9.6 (ОПЦИОНАЛЬНО) for-loop через итераторы
- **НЕ делать в базовой Phase 9** — требует `IntIterator` (runtime,
  Phase 11) или специализированный range-loop (G-02).
- IR-дамп показывает: `for (i in 0..4)` → `BLOCK origin=FOR_LOOP` →
  `IntRange::iterator()` + `WHILE origin=FOR_LOOP_INNER_WHILE` +
  `hasNext()`/`next()`.
- Для базовой Phase 9: `visitBlock` с `origin=FOR_LOOP` → TODO с
  пометкой «требует итераторы (Phase 11) или range-specialization (G-02)».
- **Если делать:** нужен `IntIterator` в runtime (C#) + маппинг
  `IntRange` → runtime-класс. Это объёмнее, чем остальная Phase 9.
- Рекомендация: отложить на Phase 9.x или Phase 11.

### 9.7 Тестовый проект 04-loops/ (ОБЯЗАТЕЛЬНО)
- Создать `test-projects/04-loops/Loops.kt` с кейсами:
  ```kotlin
  package org.example.loops

  fun test_while(): Int {
      var x = 0
      while (x < 10) { x++ }
      return x  // 10
  }

  fun test_do_while(): Int {
      var x = 0
      do { x++ } while (x < 10)
      return x  // 10
  }

  fun test_break(): Int {
      var i = 0
      while (true) { if (i == 5) break; i++ }
      return i  // 5
  }

  fun test_continue(): Int {
      var s = 0
      for (i in 0..10) {  // ← если for не реализован, через while
          if (i % 2 == 0) continue
          s += i
      }
      return s  // сумма нечётных 1+3+5+7+9 = 25
  }

  fun double(n: Int): Int = n * 2
  fun test_call(): Int {
      return double(21)  // 42
  }

  fun test_concat(): String {
      val x = 42
      return "x = $x"  // "x = 42"
  }

  fun main() {
      println(test_while())
      println(test_do_while())
      println(test_break())
      println(test_call())
      println(test_concat())
  }
  ```
- `test_continue` — если `for` не реализован, переписать через `while`.
- C# consumer (как в 00-int-add/02-expr) или EXE (как 03-hello).
- `just test-04-loops` — добавить в justfile.
- `just test-all` — обновить зависимость.

### 9.8 Перенос spec-тестов while/do-while (ОБЯЗАТЕЛЬНО, минимальный)
- Источник: `.sources/kotlin/compiler/tests-spec/testData/codegen/box/linked/statements/loop-statements/`.
- Подходящие (без коллекций/классов):
  - `while-loop-statement/p-1/pos/2.1.kt` — `while(cond){ x++; if(x==5) cond=false }` → OK.
  - `while-loop-statement/p-1/pos/2.2.kt` — `while(cond){ x++; cond=false }` → OK.
  - `do-while-loop-statement/p-1/pos/3.1.kt` — `do { x++ } while(false)` → OK.
  - `do-while-loop-statement/p-1/pos/3.2.kt` — (прочитать, аналогично).
- **НЕ переносить** break/continue/range spec-тесты — они используют
  `listOf`/`mutableListOf` (требует коллекции, Phase 11+).
- Формат адаптации:
  - `fun box(): String { ... return "OK"/"NOK" }` → наш runner.
  - Скопировать в `test-projects/04-loops/spec/` с сохранением имени.
  - Runner: `just test-04-loops-spec` (или включить в `test-04-loops`).
- Это 4 файла — минимальная регрессионная сетка для while/do-while.

## Зависимости от других задач

- **B-01** (выполнено) — scaffolding `visitWhileLoop`/`visitDoWhileLoop`/
  `visitBreak`/`visitContinue`/`visitStringConcatenation` уже есть с TODO.
- **A-04** (выполнено) — `IlOpcode` enum (`BR`, `BRTRUE`, `BRFALSE`,
  `RET`, `CALL`).
- **A-03** (выполнено) — `IlEmitter` interface + `TextIlEmitter` со
  стеком контекстов (можно добавлять метки через `newLabel()`/`label()`).
- **A-05** (выполнено) — `IrOrigins` (нужен `WHILE_LOOP` и др.).
- **G-02** (не выполнено, дизайн) — для `for` через итераторы.
- **D-02** (не выполнено) — для `IrStringConcatenation` если
  использовать `string.Concat` через stdlib-resolver (но можно напрямую
  `call [mscorlib]System.String::Concat`).

## Приёмка

1. `./gradlew :compiler-plugin:jar` — без ошибок.
2. `just test-all` — зелёный (00-int-add, 02-expr, 03-hello + 04-loops).
3. `just test-04-loops` — зелёный (все кейсы из 9.7).
4. Spec-тесты while/do-while (9.8) — все 4 возвращают "OK".
5. IR-дампы: `build/ir-dump-*.txt` для 04-loops содержат `WHILE_LOOP`,
   `DO_WHILE_LOOP`, `BREAK`, `STRING_CONCATENATION` (подтверждение, что
   IR до нас доходит в ожидаемом виде).
6. `for` — остаётся TODO (не падает, а бросает `TODO("[B-01] for-loop — Phase 11/G-02")`).

## Заметки исполнителю

- **Контекст:** `DotnetIrVisitor.kt`, `IlEmitter.kt`/`TextIlEmitter.kt`
  (из `il/` подпакета), `IlOpcode.kt`. ADR 0003 (IR-состояние).
  IR-дампы (сохранены в `build/ir-dump-*.txt`).
- **Не переделывать архитектуру** — добавлять обработчики в существующий
  `DotnetIrVisitor`, использовать `IlEmitter` API (`newLabel`, `label`,
  `opcode`, `br`, `brtrue`, `brfalse`, `ldloc`, `stloc`).
- **LoopContext** — простая data class + `ArrayDeque<LoopContext>` поле
  в visitor. Не выносить в отдельный файл (пока).
- **Строковая интерполяция** — начать с `Concat(object[])` (универсально).
  Оптимизация (inline concat для 2 строк) — POST-Phase 9.
- **Коммиты — только по явному указанию пользователя** (AGENTS.md §14).
