# A-03: IlEmitter — интерфейс + контракт «IL корректен»

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** — (но A-04 строит на этом)
- **Статус:** TODO

## Контекст

Файл: `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/IlEmitter.kt`.

Проблемы:

1. **`line` — публичный.** `DotnetIrVisitor` вызывает `emitter.line("add")`,
   `emitter.line("ret")`, и т.д. — это деталь реализации (текстовое
   представление IL), она не должна торчать наружу. Вскоре захочется
   две реализации эмиттера: текстовый (для `.il` файла) и бинарный
   (PE/CIL напрямую через `System.Reflection.Metadata` или подобное).
   Публичный `line("add")` намертво привязывает visitor к текстовому
   представлению.

2. **Нет контракта «IL корректен».** Сейчас можно сделать:
   ```kotlin
   emitter.beginStaticMethod(...)
   emitter.line("ldc.i4.1")
   // забыть endStaticMethod()
   ```
   — и emitter молча сгенерирует битый IL. Нет инварианта, что «каждый
   `beginX` закрыт `endX`, заголовок есть, тело сбалансировано».

3. **Хардкод внутри** (адресуется в A-04): опкоды — строки, названия
   методов — строки. Это отдельная задача (A-04); здесь — только
   интерфейс/контракт.

4. **Нет интерфейса.** `IlEmitter` — конкретный класс, `DotnetIrVisitor`
   зависит от класса, а не от абстракции. Для второй реализации
   (бинарный emitter) нужен общий интерфейс.

## Цель

Выделить интерфейс `IlWriter` (или `IlEmitter` — имя можно оставить,
но сделать `interface IlEmitter` + `class TextIlEmitter : IlEmitter`),
который:
- Не публикует `line`.
- Гарантирует контракт «IL корректен» (через типобезопасные методы
  + проверки в `TextIlEmitter`).
- Позволяет позже добавить `BinaryIlEmitter`.

## Задачи

1. Прочитать `IlEmitter.kt` и `DotnetIrVisitor.kt` — понять, какие
   методы реально нужны visitor.
2. Спроектировать интерфейс `IlEmitter`:
   ```kotlin
   interface IlEmitter {
       // Assembly/module
       fun assemblyHeader(assemblyName: String, withRuntime: Boolean)
       // Container class
       fun beginContainerClass(namespace: String, className: String)
       fun endContainerClass()
       // Method
       fun beginStaticMethod(name: String, returnType: String,
                              params: List<Pair<String, String>>,
                              isEntrypoint: Boolean = false)
       fun emitLocalsIfAny()
       fun endStaticMethod()
       fun resetMethodState()
       // Locals/labels
       fun declareLocal(cilType: String): Int
       fun newLabel(): String
       fun label(name: String)
       // Instructions — НЕ line, а типобезопасные:
       fun opcode(op: IlOpcode, vararg operands: String)  // см. A-04
       // Или, пока A-04 не сделан, временно:
       fun raw(line: String)  // @Deprecated, для миграции
   }
   ```
3. Реализовать `class TextIlEmitter : IlEmitter` — текущая логика, но
   `line` становится `private`.
4. Контракт «IL корректен»:
   - В `TextIlEmitter` вести стек контекстов: `TopLevel` →
     `ContainerClass` → `Method`. Каждый `beginX` пушит, `endX` —
     попает с проверкой, что парный.
   - `endContainerClass` без `beginContainerClass` → ошибка.
   - `opcode` вне метода → ошибка.
   - В `text()` (или `build()`) — проверить, что стек пустой
     (всё закрыто). Если нет — бросать `IllegalStateException`.
5. Мигрировать `DotnetIrVisitor`:
   - `emitter.line("add")` → `emitter.opcode(IlOpcode.ADD)` (после A-04)
     или `emitter.raw("add")` (временно, до A-04).
   - `emitter.line("IL_N:")` → `emitter.label(name)` (уже есть, но
     сделать частью интерфейса и сделать публичным методом).
6. **Не** делать бинарный emitter сейчас — только интерфейс. Заглушка
   `BinaryIlEmitter` — TODO в этом же файле, но отдельная задача
   (после Phase 10+).

## Приёмка

- `grep -n 'fun line' compiler-plugin/.../IlEmitter.kt` → `line` приватный
  (или убран в пользу `raw`/`opcode`).
- `DotnetIrVisitor` не содержит `emitter.line(` — только
  `emitter.opcode(...)` / `emitter.raw(...)` / типобезопасные методы.
- Тест на нарушение контракта: `beginStaticMethod` без `endStaticMethod`
  → `text()` бросает `IllegalStateException`.
- `just test-all` зелёный (IL на выходе тот же).

## Заметки исполнителю

- Это **архитектурная** задача — она перестраивает контракт. Делать
  внимательно, но не раздувать: 3 файла (`IlEmitter.kt` →
  `IlEmitter.kt` + `TextIlEmitter.kt` + возможно `IlContext.kt`).
- Не трогать опкоды как строки — это A-04. Здесь — только каркас
  интерфейса + контракт. `raw` — мост на время миграции.
- Лямбды `IlEmitter.() -> Unit` **не использовать** (ADR 0005
  упоминает проблему с `this` в visitor) — использовать явные
  `begin/end` пары.
