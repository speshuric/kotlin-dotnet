# B-01: Visitor — план-карта обработчиков Ir-узлов

- **Тема:** B. Visitor scaffolding
- **Разметка:** PRE-9 (карта), POST-9 (реализация по фазам)
- **Зависимости:** A-05 (visitor без хардкода)
- **Статус:** TODO

## Контекст

`DotnetIrVisitor.kt` сейчас написан «по кейсам»: когда появляется новый
Ir-узел, в `visitElement` бросается `TODO`, и кто-то дописывает
`when`-ветку. Это работает для PoC, но:
- Нет карты «какие узлы мы обрабатываем, какие — TODO».
- Нет связи «узел → фаза» (например, `IrWhileLoop` — Phase 9,
  `IrClass` — Phase 10).
- Новые узлы добавляются реактивно, а не по плану.

Пользователь просит: «составить план-карту постепенного написания
обработчиков Ir-узлов именно в терминах узлов, а не кейсов, большая
часть сначала будет TODO/NotImplemented».

## Цель

Создать документ-карту: для каждого Ir-узла (декларации, выражения,
операторы, типы) указать:
- Имя узла (FQN класса IR).
- Обработан ли (Phase / TODO).
- Краткую семантику → IL-паттерн.
- Фазу, в которой планируется.

Карта — не код, а план. Но в коде должен появиться **scaffolding**:
каждый узел из карты имеет явный `override fun visitXxx` в visitor,
который либо реализован, либо бросает `TODO("[B-01] <узел> — Phase N")`.

## Задачи

### Часть 1: Карта (этот файл, обновляется)

Список Ir-узлов по категориям. Для каждого — статус.

#### Декларации (IrDeclaration)

| Узел | Статус | Фаза | Семантика → IL |
|---|---|---|---|
| `IrFile` | ✅ | 4 | `.assembly` + container class |
| `IrSimpleFunction` (top-level) | ✅ | 4 | static method |
| `IrSimpleFunction` (instance) | TODO | 10 | instance method (ldarg.0 = this) |
| `IrSimpleFunction` (extension) | TODO | 10 | static method, receiver = param 0 |
| `IrClass` | TODO | 10 | `.class` declaration |
| `IrConstructor` | TODO | 10 | `.ctor` (instance/static) |
| `IrField` | TODO | 10 | `.field` (instance/static) |
| `IrProperty` | TODO | 10 | property + get/set accessors |
| `IrVariable` | ✅ | 7 | `.locals` entry + `stloc` |
| `IrValueParameter` | ✅ | 4 | `ldarg.<index>` |
| `IrTypeParameter` | TODO | 12 | generic param `<T>` |
| `IrTypeAlias` | TODO | POST-PoC | — (skip) |
| `IrEnumEntry` | TODO | 13 | static field with init |
| `IrAnonymousInitializer` | TODO | 10 | instance init block |
| `IrEnumDeclaration` | TODO | 13 | `.class sealed extends Enum` or `.class ... enum` |
| `IrScript` | TODO | POST-PoC | JS-specific, skip for .NET |
| `IrModuleFragment` | n/a | — | обрабатывается в extension, не visitor |

#### Тела (IrBody)

| Узел | Статус | Фаза | Семантика |
|---|---|---|---|
| `IrBlockBody` | ✅ | 4 | sequence of statements |
| `IrExpressionBody` | TODO | 9 | `return <expr>` (для `= expr` функций) |
| `IrSyntheticBody` | TODO | 10+ | generated bodies (data class etc.) |

#### Выражения (IrExpression)

| Узел | Статус | Фаза | Семантика → IL |
|---|---|---|---|
| `IrConst` | ✅ | 7 | `ldc.*` / `ldstr` / `ldnull` |
| `IrCall` (operator) | ✅ | 7 | opcode (add/sub/...) |
| `IrCall` (stdlib) | ✅ | 8 | `call [Runtime]Kotlin.Runtime.*` |
| `IrCall` (user, top-level) | TODO | 9 | `call <File>Kt::method` |
| `IrCall` (instance) | TODO | 10 | `ldarg.0; callvirt` |
| `IrCall` (extension) | TODO | 10 | static call with receiver |
| `IrGetValue` | ✅ | 7 | `ldarg` / `ldloc` |
| `IrSetValue` | ✅ | 7 | `stloc` (locals only) |
| `IrSetValue` (field) | TODO | 10 | `stfld` |
| `IrReturn` | ✅ | 7 | `ret` |
| `IrWhen` | ✅ | 7 | `brfalse`/`br` chain |
| `IrBranch` | ✅ | 7 | part of IrWhen |
| `IrBlock` | ✅ | 7 | sequence |
| `IrVararg` | TODO | 11 | `params object[]` |
| `IrStringConcat` | TODO | 9 | `call string::Concat` (check lowering) |
| `IrSpreadElement` | TODO | POST-PoC | spread in vararg |
| `IrBreak` | TODO | 9 | `br <loopEnd>` |
| `IrContinue` | TODO | 9 | `br <loopIter>` |
| `IrWhileLoop` | TODO | 9 | `br`/`brfalse` loop with labels |
| `IrDoWhileLoop` | TODO | 9 | condition at bottom |
| `IrReturnableBlock` | TODO | POST-9 | non-local return from lambda |
| `IrThrow` | TODO | POST-9 | `throw` |
| `IrTry` | TODO | POST-9 | `.try`/`catch`/`finally` |
| `IrTryExpression` | n/a | — | merged into IrTry? (check) |
| `IrTypeOperatorCall` (AS) | TODO | POST-9 | `isinst` / `castclass` |
| `IrTypeOperatorCall` (AS_DYNAMIC) | TODO | POST-9 | `isinst` |
| `IrTypeOperatorCall` (INSTANCEOF) | TODO | POST-9 | `isinst` + push bool |
| `IrFunctionReference` | TODO | G-01 | delegate |
| `IrPropertyReference` | TODO | POST-PoC | — |
| `IrClassReference` | TODO | POST-PoC | `ldtoken` |
| `IrGetField` | TODO | 10 | `ldfld` / `ldsfld` |
| `IrSetField` | TODO | 10 | `stfld` / `stsfld` |
| `IrGetObjectValue` | TODO | 13 | `ldsfld <Object>_INSTANCE` |
| `IrGetEnumValue` | TODO | 13 | `ldsfld <EnumEntry>` |
| `IrInstanceInitializerCall` | TODO | 10 | `newobj` + `.ctor` |
| `IrNewInstance` | TODO | 10 | `newobj` |
| `IrNewArray` | TODO | 11 | `newarr` |
| `IrCall` (lambda invoke) | TODO | G-01 | callvirt on delegate |
| `IrFunctionExpression` | TODO | G-01 | lambda creation |
| `IrSuspendableExpression` | TODO | POST-PoC | coroutines — skip |
| `IrRawString` | TODO | POST-PoC | — |
| `IrErrorExpression` | TODO | n/a | compile error recovery, skip |

#### Операторы / origins (в IrCall)

| Origin | Статус | Фаза | IL |
|---|---|---|---|
| `PLUS`, `MINUS`, `TIMES`, `DIV`, `PERC` | ✅ | 7 | add/sub/mul/div/rem |
| `GT`, `LT`, `GE`, `LE` | ✅ | 7 | cgt/clt + ceq |
| `EQEQ`, `EQEQEQ` | ✅ | 7 | ceq |
| `ANDAND`, `OROR` (как IrWhen) | ✅ | 7 | brfalse/brtrue |
| `UNARY_MINUS` | ✅ | 7 | neg |
| `UNARY_PLUS` | ✅ | 7 | nop |
| `NOT` | ✅ | 7 | ldc.i4.0 + ceq |
| `AND`, `OR`, `XOR`, `SHL`, `SHR`, `USHR` | ✅ | 7 | and/or/xor/shl/shr/shr.un |
| `RANGE_TO` | TODO | 9 | — (check lowering: iterator or Range class) |
| `IN` / `NOT_IN` | TODO | 9 | `Contains` call (runtime or BCL) |
| `IMPLICIT_COERCION` | TODO | POST-9 | conv.* |
| `EQUALS` (= override) | ✅-ish | 7 | via EQEQ? check |
| `SAFE_CALL` (?.) | TODO | 10 | null-check + branch |

### Часть 2: Scaffolding в коде

1. В `DotnetIrVisitor` добавить `override fun visitXxx` для **каждого**
   узла из карты, даже если TODO. Вместо `visitElement` с общим TODO —
   специфичный TODO с фазой:
   ```kotlin
   override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?) {
       TODO("[B-01] IrWhileLoop — Phase 9")
   }
   ```
2. Группировать `override` по категории (декларации, тела, выражения),
   с разделителями-комментариями.
3. Для уже-реализованных узлов — оставить текущую логику, но
   убедиться, что она в `visitXxx` (а не в приватных `emitXxx`,
   недоступных для фреймворка). Сейчас `visitSimpleFunction` делегирует
   в `emitSimpleFunction` — ок, но убедиться, что все `visitXxx`
   есть.
4. `visitElement` оставить как последний fallback для совсем
   неизвестного (но он должен быть почти пустым после scaffolding).

### Часть 3: План реализации по фазам

- **Phase 9:** `IrWhileLoop`, `IrDoWhileLoop`, `IrBreak`, `IrContinue`,
  user top-level `IrCall`, `IrStringConcat` (проверить lowering),
  `IrExpressionBody`, `RANGE_TO` (после проверки, во что lowering
  разворачивает), `IN`/`NOT_IN`.
- **Phase 10:** `IrClass`, `IrConstructor`, `IrField`, `IrProperty`,
  `IrNewInstance`, `IrGetField`, `IrSetField`, instance `IrCall`,
  extension `IrCall`, `IrAnonymousInitializer`, `IrInstanceInitializerCall`.
- **Phase 11:** `IrNewArray`, `IrVararg`, array-specific `IrCall`
  (`IntArray(size)`, `.size`, `.get`, `.set`).
- **Phase 12:** `IrTypeParameter`, generic `IrCall`/`IrClass`,
  `IrTypeOperatorCall` (AS/INSTANCEOF).
- **Phase 13+:** `IrEnumDeclaration`, `IrEnumEntry`, `IrGetObjectValue`,
  `IrGetEnumValue`, data-class synthetic (equals/hashCode/toString).
- **POST-PoC (G-01):** `IrFunctionExpression`, `IrFunctionReference`,
  lambda invoke `IrCall`.
- **POST-PoC:** `IrTry`, `IrThrow`, coroutines.

## Приёмка (карта + scaffolding)

- `DotnetIrVisitor.kt` содержит `override fun visitXxx` для всех узлов
  из карты (по категориям).
- `visitElement` — fallback, почти не вызывается на типичном IR.
- Для каждого TODO-узла сообщение содержит `[B-01]` и номер фазы.
- `just test-all` зелёный (существующие тесты не сломаны).
- Карта в этом файле обновлена после каждого изменения (статусы ✅/TODO).

## Заметки исполнителю

- Это **карта**, не реализация. Главное — покрытие `visitXxx`-ами с
  TODO, чтобы при появлении узла в дампе можно было найти его в
  visitor и понять фазу.
- Имена IR-классов можно подсмотреть в `.sources/kotlin/compiler/ir/`
  (`tree/impl/` — `Ir*Impl`).
- Не реализовывать узлы заранее — только TODO-заглушки.
