# B-01: Visitor — план-карта обработчиков Ir-узлов

- **Тема:** B. Visitor scaffolding
- **Разметка:** PRE-9 (карта), POST-9 (реализация по фазам)
- **Зависимости:** A-05 (visitor без хардкода)
- **Статус:** scaffolding выполнен (B-01). Карта узлов ниже актуальна;
  статусы ✅/TODO не изменились — добавлены только `visitXxx`-заглушки с
  `TODO("[B-01] ...")`. Заметки о несоответствии имён узлов и `visitXxx` —
  в конце соответствующих таблиц (см. «Заметки о маппинге узлов»).

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
| `IrSimpleFunction` (instance) | ✅ | 10 | instance method (ldarg.0 = this), callvirt |
| `IrSimpleFunction` (extension) | TODO | POST-10 | static method, receiver = param 0 |
| `IrClass` | ✅ | 10 | TypeDef: поля → ctor → методы; FAKE_OVERRIDE skip |
| `IrConstructor` | ✅ | 10 | `.ctor` + инжекция инициализаторов полей в primary |
| `IrField` | ✅ | 10 | FieldDef (private backing field), ldfld/stfld |
| `IrProperty` | ✅ | 10 | дефолтные аксессоры → instance-методы `<get-x>`/`<set-x>` |
| `IrVariable` | ✅ | 7 | `.locals` entry + `stloc` |
| `IrValueParameter` | ✅ | 4 | `ldarg.<index>`; ctor-параметры сдвинуты на +1 (Phase 10) |
| `IrTypeParameter` | TODO | 12 | generic param `<T>` |
| `IrTypeAlias` | TODO | POST-PoC | — (skip) |
| `IrEnumEntry` | TODO | 13 | static field with init |
| `IrAnonymousInitializer` | ✅-мин. | 10 | пустые — no-op; непустые — TODO Phase 10.x |
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
| `IrCall` (operator) | ✅ | 7 | opcode (add/sub/...); Phase 10: только для callee из kotlin.* |
| `IrCall` (stdlib) | ✅ | 8 | `call [Runtime]Kotlin.Runtime.*` |
| `IrCall` (user, top-level) | ✅ | 9 | `call <File>Kt::method` |
| `IrCall` (instance) | ✅ | 10 | `callvirt` (receiver = arguments[0]) |
| `IrCall` (extension) | TODO | POST-10 | static call with receiver |
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
| `IrGetField` | ✅ | 10 | `ldfld` (ldsfld — Phase 13) |
| `IrSetField` | ✅ | 10 | `stfld` (stsfld — Phase 13) |
| `IrGetObjectValue` | TODO | 13 | `ldsfld <Object>_INSTANCE` |
| `IrGetEnumValue` | TODO | 13 | `ldsfld <EnumEntry>` |
| `IrInstanceInitializerCall` | ✅ | 10 | no-op (аллокация в newobj на месте вызова) |
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

## Заметки о маппинге узлов → visitXxx (по итогам B-01)

Сверено с `IrVisitor.kt` (`.sources/kotlin/compiler/ir/ir.tree/gen/`).
Часть узлов из таблиц выше — концептуальные категории, а не отдельные
IR-классы. Их маппинг:

- **`IrEnumDeclaration`** → отдельного узла нет. Enum — это `IrClass` с
  enum-флагом (`IrClass` → `visitClass`). Scaffolding: `visitClass`
  TODO Phase 10 (enum-специфика — Phase 13, см. карту).
- **`IrNewInstance`** → `IrConstructorCall` (`visitConstructorCall`).
  Отдельного `IrNewInstance` в IR-дереве Kotlin нет.
- **`IrNewArray`** → отдельного узла нет. Создание массива идёт через
  `IrVararg` (`visitVararg`, Phase 11) или `IrConstantArray`
  (`visitConstantArray`). Scaffolding: `visitVararg` TODO Phase 11.
- **`IrStringConcat`** (карта) → `IrStringConcatenation`
  (`visitStringConcatenation`). Scaffolding: TODO Phase 9.
- **`IrTypeOperatorCall`** → `visitTypeOperator` (IrTypeOperatorCall).
  AS / AS_DYNAMIC / INSTANCEOF различаются по `operator` внутри узла.
- **`IrRawString`** → в IR-дереве Kotlin v2.4.20 такого узла нет
  (`IrRawString`/`visitRawString` отсутствуют). Scaffolding не добавлен.
- **`IrInstance`/`IrSimpleFunction` (instance/extension)** → тот же
  `IrSimpleFunction` (`visitSimpleFunction`); отличие — по наличию
  `dispatchReceiverParameter`. Различение в `emitSimpleFunction`.
- **`IrCall` (operator/stdlib/user/instance/extension)** → один узел
  `IrCall` (`visitCall`); категория определяется по `origin` и
  `symbol.owner`. Различение в `emitCall`.
- **`IrSetValue` (field)** → тот же `IrSetValue` (`visitSetValue`);
  отличие — по `symbol.owner` (`IrVariable` vs `IrField`). Различение в
  `emitSetValue`. В K2 IR присваивание полям идёт через `IrSetField`
  (в т.ч. в телах дефолтных сеттеров); ветка IrField в `emitSetValue`
  оставлена как defensive-TODO.
- **`IrDelegatingConstructorCall`** → `visitDelegatingConstructorCall`
  (Phase 10). Слота receiver нет: `this` подаётся явно `ldarg.0`.
  Отсутствовал в scaffolding B-01 — добавлен в Phase 10.
- **`IrConstructorCall`** → `visitConstructorCall` (Phase 10): args +
  NEWOBJ, слота receiver нет.
- **Статусы Phase 10**: см. TODO/PHASE-10.md §«Итоги реализации».
