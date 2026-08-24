# G-02: Дизайн — итераторы (общий + специализированные)

- **Тема:** G. Internal design
- **Разметка:** POST-9 (дизайн)
- **Зависимости:** B-01 (карта visitor)
- **Статус:** TODO (design doc)

## Контекст

Пользователь: «Нужен внутренний дизайн: итераторов в целом; частных
случаев итераторов (в первую очередь по числовым диапазонам — иначе
производительность жопой накроется, может еще по буквам в строке)».

Kotlin-циклы `for (x in collection)`:
- В Kotlin IR lowering разворачивает `for` в `while + iterator`:
  `val it = coll.iterator(); while (it.hasNext()) { val x = it.next(); ... }`.
- Для диапазонов (`0..10`) — может быть специализированный lowering
  (через `IrWhileLoop` с инкрементом, без iterator-объекта).
- Это зависит от IR-состояния (см. E-01) — что приходит к нам.

Производительность:
- Общий iterator (`System.Collections.Generic.IEnumerator<T>`) —
  allocation (если не value-type enumerator) + virtual calls
  (`MoveNext`, `Current`). Для горячего цикла `for (i in 0..1_000_000)`
  — катастрофа.
- Числовые диапазоны: должны lowering'уться в `IrWhileLoop` с
  примитивным счётчиком (int32, int64), без iterator-объекта.
- Строки: `for (c in "abc")` — через индекс (`str[i]`, `str.Length`),
  без `CharEnumerator`.

## Цель

Design doc: как маппить итераторы, какие специализации, IL-паттерны.

**Не** реализовывать — только дизайн.

## Задачи

### 1. Исследование IR-lowering'а циклов

В `.sources/kotlin/` (поиск `ForLoop`, `Range` lowering) и через
эксперимент:
```kotlin
// test-projects/04-loops/Loops.kt
fun sumRange(n: Int): Int {
    var s = 0
    for (i in 0..n) s += i
    return s
}
fun sumList(xs: List<Int>): Int {
    var s = 0
    for (x in xs) s += x
    return s
}
fun countChars(s: String): Int {
    var c = 0
    for (ch in s) c++
    return c
}
```
- Дамп IR для каждого.
- Проверить: `0..n` → `IrWhileLoop` с примитивным `i`, или
  `IrCall iterator()` + `IrWhileLoop`?
- `for (x in xs)` (List) → iterator-call?
- `for (ch in s)` (String) — `iterator()` или индекс?

### 2. Специализации (проект)

| Источник | IR-паттерн (ожидаемый) | IL-паттерн |
|---|---|---|
| `0..n` (IntRange) | `IrWhileLoop` с `IrVariable i` | `for`-loop: `ldloc i; ldc.i4.1; add; stloc i; ... cmp; br` |
| `0L..nL` (LongRange) | то же, int64 | long-инкремент |
| `for (x in list)` | `IrCall iterator()` + `IrWhileLoop hasNext/next` | `IEnumerator<T>` + `MoveNext`/`get_Current` |
| `for (c in string)` | `IrCall iterator()` или индекс | если iterator — `CharEnumerator`; если индекс — `ldlen`+`ldelem` |
| `for (x in array)` | `IrCall iterator()` или `IrWhileLoop` по `.size` | `ldelem.ref` / специализированный `ldelem.i4` |
| `for (x in IntArray)` | то же | `ldelem.i4` |

### 3. Оптимизации (проект)

- **Range specialization**: если IR содержит `IrWhileLoop` с
  инкрементом примитива (int/long) и границей — эмитить
  «C-style for» без iterator-объекта. Ключевая оптимизация.
- **Array index loop**: если `for (i in array.indices)` —
  `IrWhileLoop` по `.size` с `ldelem.*`.
- **String index loop**: если lowering даёт индекс-цикл —
  `ldlen` + `ldelem.i2` (char).
- **General iterator**: fallback для произвольного
  `Iterable<T>` — `IEnumerator<T>`.

### 4. IL-паттерны

- **C-style loop (range):**
  ```
  // for (i in 0..n)
  ldc.i4.0
  stloc i
  br IL_CHECK
  IL_BODY:
  // ... тело ...
  ldloc i
  ldc.i4.1
  add
  stloc i
  IL_CHECK:
  ldloc i
  ldarg n
  blt IL_BODY   // или cgt + brfalse
  ```
- **Iterator loop:**
  ```
  // for (x in xs)
  ldarg xs
  callvirt instance class [mscorlib]System.Collections.Generic.IEnumerator`1<!0> ...::GetEnumerator()
  stloc it
  // try-finally для Dispose (если IDisposable) — TODO (Phase 9?)
  IL_BODY:
  ldloc it
  callvirt instance bool class [mscorlib]System.Collections.IEnumerator::MoveNext()
  brfalse IL_END
  ldloc it
  callvirt instance !0 ...::get_Current()
  stloc x
  // ... тело ...
  br IL_BODY
  IL_END:
  ```
- `IrBreak` → `br IL_END`.
- `IrContinue` → `br IL_BODY` (или `IL_CHECK` для range).

### 5. Документ

Создать `docs/iterator-design.md` + (опц.) ADR [`0013-iterators-design.md`](../../adr/0013-iterators-design.md):
- Специализации (таблица).
- IL-паттерны (range / iterator / array / string).
- Оптимизации (range, array-index, string-index).
- TODO для реализации (Phase 9 — базовые loops; специализации —
  Phase 9+ / 11).

## Приёмка

- `docs/iterator-design.md` существует, описывает специализации.
- В B-01 карте `IrWhileLoop`, `IrDoWhileLoop`, `IrBreak`,
  `IrContinue`, `IrCall(iterator)` — ссылки на G-02.
- Экспериментальные дампы IR (для `sumRange`, `sumList`, `countChars`)
  приложены к doc или сохранены в `test-projects/04-loops/`.

## Заметки исполнителю

- Это **дизайн**, не реализация.
- Ключевой риск: если IR уже lowering'нул range в iterator-call
  (а не в примитивный while), то специализация не сработает —
  зафиксировать как TODO и/или investigate (E-01).
- Для Phase 9 можно начать с **общего** iterator-паттерна, а
  специализации добавить в Phase 9.x или 11.
