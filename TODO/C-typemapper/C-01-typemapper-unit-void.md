# C-01: TypeMapper — Unit vs void, рефакторинг

- **Тема:** C. TypeMapper
- **Разметка:** PRE-10
- **Зависимости:** — (можно делать параллельно с A)
- **Статус:** TODO

## Контекст

Файл: `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/TypeMapper.kt`.

Пользователь: «TypeMapper весь какое-то говно, но там ещё и зашито
вечное противоречие kotlin и других ЯП (java, js) — у них void
специальный тип, а у kotlin Unit имеет честное одно значение».

Текущий код:
```kotlin
fun mapType(type: IrType): String = when {
    type.isInt() -> "int32"
    ...
    type.isUnit() -> "void"
    type.isAny() || type.isNullableAny() -> "object"
    type is IrSimpleType -> "object"
    else -> "object"
}
```

Проблемы:
1. **`Unit → void`** — семантически неверно. `void` в CIL — это
   «метод не возвращает значения», а `Unit` в Kotlin — тип с одним
   значением `Unit.INSTANCE`. Когда `Unit` используется как **значение**
   (например, `val x = println(1) // x: Unit`), маппинг в `void`
   ломается: нет «объекта Unit» на стеке. Когда `Unit` — **тип возврата
   метода**, `void` — ок.
2. **Falling back на `object`** — все неизвестные типы превращаются в
   `object`. Нет разницы между «не реализовано» и «честный Any».
3. **Нет nullable-маппинга** — `Int?` сейчас, видимо, идёт в `object`
   (через `isAny`? проверить). `Nullable<int32>` — Phase 12.
4. **`isAny() || isNullableAny()` → `object`** — ок для PoC, но
   смешивает Any и Any?.
5. Нет `IrDynamicType`, `IrErrorType` обработки.

## Цель

Разделить маппинг «тип возврата» и «тип значения», и сделать
различение `Unit` явным.

## Задачи

1. Разделить API:
   ```kotlin
   object TypeMapper {
       /** Тип возврата метода: Unit → void. */
       fun mapReturnType(type: IrType): String
       /** Тип значения/параметра: Unit → [Runtime]Kotlin.Runtime.Unit. */
       fun mapValueType(type: IrType): String
       /** Синоним для случаев, где контекст не критичен (deprecated). */
       fun mapType(type: IrType): String = mapValueType(type)
   }
   ```
2. `Unit`:
   - В `mapReturnType` → `void` (метод не возвращает).
   - В `mapValueType` → `[KotlinDotnetRuntime]Kotlin.Runtime.Unit`
     (объект-синглтон). Для этого добавить в runtime `Kotlin.Runtime.Unit`
     (C# статический класс с полем `INSTANCE`). См. D-01.
   - Когда `Unit` — выражение (например, `val x: Unit = println(1)`):
     visitor должен либо не загружать ничего (т.к. `println` → void),
     либо загружать `[Unit]INSTANCE`. Это отдельный кейс в visitor,
     не в TypeMapper. Зафиксировать TODO в B-01 карте.
3. Чёткий `when`:
   ```kotlin
   fun mapValueType(type: IrType): String = when {
       type.isInt() -> "int32"
       ...примитивы...
       type.isUnit() -> "[KotlinDotnetRuntime]Kotlin.Runtime.Unit"
       type.isAny() -> "object"
       type.isNullableAny() -> "object"  // separate for clarity
       type is IrSimpleType -> TODO("[C-01] type mapping for ${type::class.simpleName}")
       else -> TODO("[C-01] type mapping for unknown")
   }
   ```
   — убрать «silent fallback на object» для неизвестных; TODO с контекстом.
4. Nullable (`Int?`, `String?`):
   - `String?` → `string` (reference type, nullable reference type —
     PoC не проверяет NRT в runtime).
   - `Int?` → `valuetype [mscorlib]System.Nullable`1<int32>` — но это
     Phase 12. До этого — TODO.
5. Опечатки/неоднозначности: `isAny()` vs `isNullableAny()` — явно
   разделить.
6. Не трогать дженерики (Phase 12) — только TODO-заглушки.
7. Зафиксировать противоречие Unit/void в ADR (создать
   `adr/0007-unit-vs-void.md`):
   - `mapReturnType` → void (idиоматичный .NET).
   - `mapValueType` → Unit-object (когда Unit — значение).
   - Это **компромисс**: иногда компилятору Kotlin нужно грузить Unit
     на стек (если выражение с Unit дальше используется). На практике
     фронтенд часто опускает Unit-значения, но не всегда.

## Приёмка

- `TypeMapper` имеет `mapReturnType` и `mapValueType`, оба
  используются в visitor (`mapReturnType` — для сигнатуры метода,
  `mapValueType` — для параметров, локалок, полей).
- `mapType` оставлен как deprecated-алиас или убран (миграция visitor).
- `Unit` в позиции значения — TODO в B-01 (visitor) с пометкой
  «грузить Unit.INSTANCE если значение используется».
- `adr/0007-unit-vs-void.md` создан.
- `just test-all` зелёный (для текущих тестов Unit-как-значение не
  встречается; mapReturnType → void для main/println).
- `grep -n 'TODO.*C-01' TypeMapper.kt` показывает TODO для
  неизвестных типов (а не silent `object`).

## Заметки исполнителю

- Контекст: `TypeMapper.kt`, `DotnetIrVisitor.kt` (только вызовы
  `TypeMapper.mapType`), AGENTS.md §"Примитивные типы".
- Не реализовывать Nullable — Phase 12. Только TODO.
- `Kotlin.Runtime.Unit` в runtime (C#) — добавить в D-01, не здесь.
  Здесь — только ссылка на него в `mapValueType`.
