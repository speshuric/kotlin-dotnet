# ADR 0003: Состояние IR на перехвате плагина

- **Дата:** 2026-08-17
- **Статус:** Accepted

## Контекст

AGENTS.md §"Критический вопрос: в каком состоянии IR до нас доходит?"
формулирует дилемму: выполняются ли JVM-специфичные lowering-проходы
(type erasure, bridge methods) до того, как `IrGenerationExtension`
получит IR?

Сценарии:
- **A (хороший):** IR сохраняет полные type arguments. Erasure — только в
  JVM codegen.
- **B (плохой):** JVM-specific lowering стирает типы в IR до нас.
- **C (рабочий):** IrGenerationExtension позволяет выбрать фазу перехвата
  до erasure-проходов.

## Решение

**Сценарий A** (предварительно).

## Наблюдения (Phase 4)

Для исходника:
```kotlin
package org.example.math
public fun test_add(a: Int, b: Int) = a + b
```

`IrModuleFragment.dump()` (Kotlin 2.4.20-RC) выдаёт:
```
MODULE_FRAGMENT name:<main>
  FILE fqName:org.example.math fileName:.../Arithmetic.kt
    FUN name:test_add visibility:public modality:FINAL returnType:kotlin.Int
      VALUE_PARAMETER kind:Regular name:a index:0 type:kotlin.Int
      VALUE_PARAMETER kind:Regular name:b index:1 type:kotlin.Int
      BLOCK_BODY
        RETURN type=kotlin.Nothing from='public final fun test_add ...'
          CALL 'public final fun plus (other: kotlin.Int): kotlin.Int [operator] declared in kotlin.Int' type=kotlin.Int origin=PLUS
            ARG <this>: GET_VAR 'a: kotlin.Int ...' type=kotlin.Int origin=null
            ARG other: GET_VAR 'b: kotlin.Int ...' type=kotlin.Int origin=null
```

Наблюдения:
1. Пакет `org.example.math` сохранён как `fqName` файла — **verbatim namespace** работает напрямую.
2. Имя функции `test_add` сохранено как есть — verbatim method name OK.
3. `kotlin.Int` присутствует как тип (не стёрт в `Object`) — типы примитивов видны.
4. `plus` — это `kotlin.Int.plus(other: kotlin.Int): kotlin.Int` (operator из stdlib). IR содержит полный site вызова (даже для примитивов).
5. `origin=PLUS` — это маркер, что call — оператор `+`, а не произвольный вызов.
6. Параметры доступны по `index` (`a=0`, `b=1`) — соответствует `ldarg.0`, `ldarg.1`.
7. `RETURN type=kotlin.Nothing` — тип return-узла Nothing (это не тип возврата функции; функция имеет `returnType:kotlin.Int`).

## Влияние на дженерики

В этом PoC-цикле (`test_add`) дженериков нет. Для полной проверки сценария
A/B/C нужно в будущем протестировать `fun <T> identity(x: T): T = x` и
`List<Int>` — но это Phase 8+.

Предварительный вывод: **сценарий A** (IR сохраняет полные типы) —
согласуется с тем, что Kotlin/JS и Kotlin/Native работают с IR без erasure.

## Использование в кодогенераторе (Phase 5)

- `IrFile.fqName` → namespace (`org.example.math`).
- `IrFile.fileEntry.name` → basename → `<Name>Kt` класс (Arithmetic.kt → ArithmeticKt).
- `IrSimpleFunction.name` → имя метода (`test_add`).
- `IrValueParameter.index` → `ldarg.<index>`.
- `IrCall.origin == PLUS` → `add` opcode (для Int).
- `IrConst` → `ldc.<type>`.
- `IrReturn` → вычислить expr, `ret`.
