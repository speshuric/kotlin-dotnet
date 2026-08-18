# A-04: IlEmitter — опкоды в enum

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** A-03 (контракт IlEmitter)
- **Статус:** TODO

## Контекст

В `DotnetIrVisitor.kt` опкоды разбросаны как строки:

```kotlin
"plus" -> emitter.line("add")
"minus" -> emitter.line("sub")
"times" -> emitter.line("mul")
...
emitter.line("ldc.i4.0")
emitter.line("ceq")
emitter.line("cgt")
emitter.line("clt")
...
emitter.line("ldarg.${owner.indexInParameters}")
emitter.line("stloc.$idx")
emitter.line("ldloc.$idx")
emitter.line("ret")
emitter.line("br $endLabel")
emitter.line("brfalse $nextLabel")
```

Это:
- Хардкод строк (опечатка = молчаливый битый IL, `ilasm` поймает, но
  поздно и без контекста).
- Нет автодополнения, нет исчерпывающего `when`.
- Смешивание «опкод» + «операнд» (`ldarg.0` — это опкод с встроенным
  операндом, `ldc.i4 42` — опкод + операнд). Сейчас это одна строка,
  что мешает второй реализации (бинарный emitter: `ldarg.0` → байт
  `0x06`, `ldarg 5` → `0x09 0x05`).

## Цель

Вынести IL-опкоды в `enum class IlOpcode` (или sealed), чтобы:
- `when` был исчерпывающим.
- Опечатки ловились на этапе компиляции.
- Был один источник правды для «какой опкод у сложения».
- Позже `BinaryIlEmitter` мог маппить `IlOpcode → байт`.

## Задачи

1. Составить список всех опкодов, используемых в `DotnetIrVisitor` и
   `IlEmitter` (сейчас). Группы:
   - **Арифметика:** `add`, `sub`, `mul`, `div`, `rem`, `neg`.
   - **Логические/битовые:** `and`, `or`, `xor`, `not`(через `ldc.i4.0`+`ceq`).
   - **Сдвиги:** `shl`, `shr`, `shr.un`.
   - **Сравнения:** `ceq`, `cgt`, `clt`.
   - **Загрузка констант:** `ldc.i4`, `ldc.i4.0..8`, `ldc.i4.M1`,
     `ldc.i8`, `ldc.r4`, `ldc.r8`, `ldnull`, `ldstr`.
   - **Загрузка/сохранение:** `ldarg`, `ldarg.0..3`, `ldloc`, `stloc`,
     `ldloc.0..3`, `stloc.0..3`.
   - **Управление потоком:** `br`, `brtrue`, `brfalse`, `ret`.
   - **Вызовы:** `call`, `callvirt`, `newobj` (последний — TODO).
2. Создать `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/IlOpcode.kt`:
   ```kotlin
   enum class IlOpcode(val il: String) {
       ADD("add"),
       SUB("sub"),
       MUL("mul"),
       DIV("div"),
       REM("rem"),
       NEG("neg"),
       AND("and"),
       OR("or"),
       XOR("xor"),
       SHL("shl"),
       SHR("shr"),
       SHR_UN("shr.un"),
       CEQ("ceq"),
       CGT("cgt"),
       CLT("clt"),
       RET("ret"),
       BR("br"),
       BRTRUE("brtrue"),
       BRFALSE("brfalse"),
       CALL("call"),
       CALLVIRT("callvirt"),
       NEWOBJ("newobj"),
       LDNULL("ldnull"),
       LDSTR("ldstr"),
       // ldc/ldarg/ldloc/stloc — с операндом, см. ниже
       LDC_I4("ldc.i4"),
       LDC_I4_0("ldc.i4.0"),
       // ... и т.д.
       LDARG("ldarg"),
       LDARG_0("ldarg.0"),
       // ...
       LDLOC("ldloc"),
       STLOC("stloc"),
       // ...
       ;
   }
   ```
3. Решить, как представлять «опкод + операнды»:
   - Вариант 1: `emitter.opcode(IlOpcode.LDC_I4, "42")` → `.il` строка
     `ldc.i4 42`.
   - Вариант 2: отдельные методы `emitter.ldcI4(42)`, `emitter.ldarg(0)`.
   - **Рекомендация:** Вариант 2 для часто-используемых типизированных
     (ldc, ldarg, ldloc, stloc), Вариант 1 для остальных. Так visitor
     остаётся читаемым, а опечатки в операндах ловятся типом.
4. Мигрировать `DotnetIrVisitor`:
   - `emitter.line("add")` → `emitter.opcode(IlOpcode.ADD)`.
   - `emitter.line("ldc.i4.0")` → `emitter.ldcI4(0)` (метод сам выберет
     `ldc.i4.0` vs `ldc.i4 0`).
   - `emitter.line("ldarg.${idx}")` → `emitter.ldarg(idx)`.
   - и т.д.
5. Убедиться, что `when (symbolName)` в `emitCall` становится
   исчерпывающим (или с `else -> TODO`).
6. Сохранить эвристику коротких форм (`ldc.i4.0` для 0..8) — но
   инкапсулировать в `TextIlEmitter`, не в visitor.

## Приёмка

- `grep -n 'emitter.line' compiler-plugin/.../DotnetIrVisitor.kt` → 0
  (или только `emitter.raw`, если A-03 оставил мост).
- `IlOpcode.kt` существует, содержит все используемые опкоды.
- `just test-all` зелёный, IL на выходе идентичен.
- В `when` по `IlOpcode` нет `else` (или только с TODO).

## Заметки исполнителю

- Не пытаться описать **все** CIL-опкоды — только то, что используется.
  Расширять по мере появления новых (Phase 9+).
- Короткие формы (`ldc.i4.0`...`ldc.i4.8`, `ldarg.0`...`ldarg.3`,
  `ldloc.0`...`ldloc.3`) — инкапсулировать в emitter, visitor не должен
  знать про них.
- Это задача-мост к `BinaryIlEmitter`: после enum-опкодов бинарный
  emitter — маппинг `IlOpcode → байт` + сериализация операндов.
