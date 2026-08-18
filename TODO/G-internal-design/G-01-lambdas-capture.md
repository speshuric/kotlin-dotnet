# G-01: Дизайн — лямбды и захват контекста

- **Тема:** G. Internal design
- **Разметка:** POST-9 (дизайн), POST-10 (реализация)
- **Зависимости:** B-01 (карта visitor), A-03 (IlEmitter-контракт)
- **Статус:** TODO (design doc)

## Контекст

Пользователь: «Нужен внутренний дизайн (неочевидные): лямбд, в
частности с захватом контекста».

Kotlin-лямбды (`{ x -> x + 1 }`, `fun foo(f: (Int) -> Int) = f(1)`)
в IR представлены как:
- `IrFunctionExpression` — лямбда как выражение.
- Внутри — `IrSimpleFunction` (анонимная функция, `isInline = false`
  обычно).
- Захват переменных из внешнего контекста — через
  `IrFunctionExpression.dispatchReceiver` / `extensionReceiver` /
  `captured` поля.

На .NET:
- Без inline-лямбд — это **delegate** (`System.Func<...>`,
  `System.Action<...>` или custom delegate).
- Захват контекста — **closure class** (как в C# compiler): для
  каждой лямбды генерится `class <>c__DisplayClassN` с полями для
  захваченных переменных, и лямбда-метод становится instance-методом
  этой closure-класса.
- Альтернатива: `System.Runtime.CompilerServices.Closure` (не
  публичный, неудобно).

Это сложно и не входит в PoC базовые фазы. Но **дизайн** нужен
заранее, чтобы не переписывать visitor, когда дойдём.

## Цель

Design doc: как маппить Kotlin-лямбды на .NET, какие IR-узлы
участвуют, какой IL-паттерн.

**Не** реализовывать — только дизайн.

## Задачи

### 1. Исследование IR-представления лямбд

В `.sources/kotlin/` и через эксперимент:
```kotlin
// test-projects/XX-lambda/Lambda.kt
fun apply(f: (Int) -> Int, x: Int): Int = f(x)
fun main() { println(apply({ it + 1 }, 10)) }
```
- Дамп IR: как выглядит `IrFunctionExpression`?
- Какие `dispatchReceiverParameter` / `extensionReceiverParameter` /
  `valueParameters` у лямбды?
- Как захват отражён в IR? (captured vars — поля на лямбда-функции,
  или на closure-объекте?)

### 2. IL-паттерн (проект)

- **Closure class** (если есть захват):
  ```
  .class auto ansi beforefieldinit '<>c__DisplayClass0'
      extends [mscorlib]System.Object
  {
      .field public int32 capturedVar
      .method public instance int32 '<Lambda>b__1_0'(int32 x) cil managed
      { ... }
  }
  ```
- **Delegate type**: для `(Int) -> Int` — `class [mscorlib]System.Func`2<int32, int32>`.
- **Construction**: `newobj` closure class, `stfld` захваченные
  vars, `newobj` delegate с closure как `instance`, `ldftn` lambda
  method.
- **Invocation**: `callvirt Invoke(...)`.

Без захвата — можно статический метод + `ldnull`/`ldftn` + delegate
(no closure class).

### 3. Дизайн для visitor

- `IrFunctionExpression` → создание closure class + delegate.
- `IrCall` с lambda-инвокe → `callvirt Invoke`.
- `IrFunctionReference` → `ldftn` + delegate.
- Захват: обойти лямбду, найти `IrGetValue` ссылок на outer-scope
  variables, превратить их в поля closure, подменить `ldloc` на
  `ldfld`.

### 4. Связь с inline-функциями

- `inline fun` — фронтенд инлайнит (AGENTS.md §«Что НЕ
  реализуется»). К нам в IR уже развёрнутый код, лямбд нет.
  - Но `noinline` / `crossinline` — лямбды остаются. Зафиксировать.
- Если лямбда передаётся в inline-функцию, но не инлайнится —
  обычный delegate.

### 5. Coroutines — не здесь

- `suspend` лямбды — POST-PoC (корутины). В дизайне упомянуть, что
  не покрывается.

### 6. Документ

Создать `docs/lambda-design.md` + (опц.) ADR `0011-lambdas.md`:
- IR-представление (после исследования).
- IL-паттерн (closure class + delegate).
- Связь с inline.
- TODO для реализации (Phase G-01, после 10).

## Приёмка

- `docs/lambda-design.md` существует, описывает IR→IL паттерн.
- В B-01 карте `IrFunctionExpression`, `IrFunctionReference`,
  lambda-invoke `IrCall` помечены как «design: G-01».
- Реализации нет (TODO-заглушки в visitor).

## Заметки исполнителю

- Это **дизайн**, не реализация.
- Контекст: `.sources/kotlin/compiler/ir/tree/`, экспериментальный
  дамп IR, `docs/il-reference.md` (delegate IL).
