# Phase 10: Классы — `.class`, поля, `.ctor`, instance-методы, `newobj`

- **Статус:** план на утверждение
- **Контекст:** основной фазовый путь (AGENTS.md «Дальнейший план»).
  Предшественники: Phase 9 (циклы/функции/интерполяция), порт dotnetutils
  (I0–I8 + M1), интеграция PE-бэкенда как дефолта (S1–S6, ADR 0010).
- **После фазы** (не вместе): удаление `TextIlEmitter` + ilasm-ветки
  (см. REMAINING-ROADMAP §C).

## Цель

Минимальные пользовательские классы: объявление класса с полями и
методами, конструкторы (в т.ч. синтез дефолтного), создание экземпляров
(`newobj` + вызов `.ctor`), instance-методы (`callvirt`), доступ к полям
(`ldfld`/`stfld`, `ldsfld`/`stsfld`), наследование `extends`
(использование унаследованных членов) и `implements` (пустые интерфейсы).

Не входит в фазу: свойства с кастомными аксессорами, `open`/`override`
(stretch, см. 10.7), companion objects / object declarations (Phase 13+),
дженерики (Phase 12), nested/local классы, data/sealed/enum классы.

## Ключевые решения

### D1. Двухбэкендная стратегия: pe-only для новых тестов

`TextIlEmitter` помечен @Deprecated и будет удалён сразу после фазы.
Поэтому функциональность классов реализуется **только в `PeIlEmitter`**;
il-путь замораживается на возможностях Phase 9. В реестре тестов
(`scripts/tests.sh`) появляется `test_backends <id>` → `pe` для новых
тестов; dual-backend прогон S5 пропускает il-ветку для них.

Следствие: `IlEmitter` интерфейс расширяется минимально необходимым для
visitor'а набором семантических операций; TextIlEmitter получает
заглушки `TODO("removed after Phase 10")`, чтобы не писать мёртвый код.

### D2. Расширение IlEmitter (семантические операции)

```kotlin
fun beginClass(namespace: String, name: String,
               baseTypeCil: String?, interfaces: List<String>,
               flags: UInt /* по умолчанию Public|AutoLayout|BeforeFieldInit */)
fun endClass()
fun declareField(cilType: String, name: String, isStatic: Boolean): Int
fun beginMethod(name: String, returnType: String,
                params: List<Pair<String, String>>,
                isStatic: Boolean, isEntrypoint: Boolean = false) // унификация;
// beginStaticMethod остаётся делегатом (isStatic = true)
fun beginConstructor(params: List<Pair<String, String>>) // specialname rtspecialname
```

Новые опкоды в `IlOpcode`: `LDFLD`, `STFLD`, `LDSFLD`, `STSFLD`
(операнд — текстовый member-ref, тот же паттерн, что `CALL`;
`PeIlEmitter` парсит `type Ns.Class::field`). `NEWOBJ` уже есть.

### D3. Метаданные (PeIlEmitter)

- Множественные классы на файл: `endClass` запоминает диапазон методов
  [first..last] класса; TypeDef'ы добавляются после всех тел (как сейчас),
  fieldList/methodList — корректные диапазоны на каждый класс.
- Поля: `addFieldDefinition(flags=Public, name, sig)`; сигнатура поля —
  `BlobEncoder.field().type()` (BlobEncoders!); fieldList-диапазоны.
- Флаги методов: instance public — `0x0086` (Public|HideBySig);
  ctor — `0x1886` (+SpecialName|RTSpecialName); отклонение от kotlinc
  (virtual+final) зафиксировать в шапке файла — для callvirt не критично.
- Базовый тип: BCL/stdlib → TypeRef `[System.Runtime]`; собственный класс
  той же сборки → TypeRef со scope Module (решение задокументировать;
  альтернатива — TypeDef напрямую, если класс известен эмиттеру).
- Интерфейсы: `addInterfaceImplementation(typeDef, typeRef)`.
- Синтез дефолтного `.ctor`: если у класса нет ни одного ctor — добавить
  `public .ctor()` c `ldarg.0; call instance Base::.ctor(); ret`.

### D4. Visitor (IR)

Сначала **наблюдения** (10.1): скомпилировать probe-файлы через kotlinc
без плагина, снять ir-dump'ы и зафиксировать фактические формы узлов:
как K2 представляет property с backing field (getter/setter вызовы vs
IrGetField/IrSetField), где оказывается инициализация полей (SET_FIELD
в теле ctor?), как выглядит IrDelegatingConstructorCall (base vs this),
форма IrConstructorCall (NEWOBJ+token в одном узле).

Ожидаемые узлы к реализации:

| IR | Действие |
|---|---|
| `visitClass` | emit class: fields → ctors → methods |
| `IrField` (backing field) | declareField |
| `IrConstructor` | beginConstructor + тело; `IrDelegatingConstructorCall` → base/this `.ctor` |
| `IrSimpleFunction` instance | beginMethod(isStatic=false); `this` = arg0, параметры сдвинуты на 1 |
| `IrConstructorCall` | NEWOBJ token + аргументы (стек: args, затем newobj создаёт объект) |
| `IrGetField`/`IrSetField` | ldarg receiver? (receiver уже на стеке из emitExpr) → ldfld/stfld ref |
| `IrGetObjectValue` | POST — object declarations Phase 13 |
| `IrAnonymousInitializer` | inline в каждый ctor после delegating call (init-блоки) — минимум: поддержка пустых |

Приёмка null-semantics: instance-вызовы только через `callvirt`
(null-check бесплатно).

## Задачи

- [ ] **10.0 Наблюдения**: probe-файлы (property var/val, ctor с init,
      наследование, интерфейс), ir-dump'ы, фиксация форм в этом файле.
      *Результат: таблица IR-форм дополнена.*
- [ ] **10.1 IlEmitter + PeIlEmitter**: D2/D3 — классы, поля, методы,
      ctor; реестр `test_backends`.
- [ ] **10.2 Visitor**: visitFile dispatch (top-level fn + classes),
      visitClass/IrField/IrConstructor/IrDelegatingConstructorCall/
      IrConstructorCall/IrGetField/IrSetField; instance-call path
      (callvirt, this=arg0, params+1); NameMapper.className verbatim.
- [ ] **10.3 Тест-проект `05-classes`**:
      ```kotlin
      class Point(val x: Int, val y: Int) {
          fun sum(): Int = x + y
      }
      class Counter {
          var count: Int = 0
          fun inc(): Int { count += 1; return count }
      }
      class LabeledPoint(x: Int, y: Int, val label: String) : Point(x, y)
      interface Shape { ... } // implements, пустой
      fun main() { ... println(p.sum()); println(c.count) ... }
      ```
      Ожидания вывода зафиксированы; verifier OK.
- [ ] **10.4 Дефолтный ctor + синтез**: класс без явного ctor.
- [ ] **10.5 Статические члены**: static поле/метод класса
      (`companion`-независимо, явный статический член PoC-класса) —
      ldsfld/stsfld/call static.
- [ ] **10.6 Регрессия**: все существующие тесты зелёные; dual-run для
      старых тестов сохранён; `05-classes` помечен `test_backends=pe`.
- [ ] **10.7 (stretch) open/override**: `open class`/`open fun`/`override`
      → Virtual|NewSlot/ReuseSlot флаги + callvirt полиморфизм. Если
      IR/K2-флаги потребуют глубже — перенести в Phase 13 без стыда.

## Приёмка

1. `just test 05-classes` зелёный (backend=pe), verifier OK.
2. Все прежние тесты зелёные (обе ветки там, где применимо).
3. `dotnet-ildasm` показывает корректные .class/.field/.method.
4. Round-trip тест I8 покрывает новый образ (typedefs/methods ranges).

## Риски

| Риск | Митигация |
|---|---|
| K2 lowering свойств неожиданной формы | 10.0 наблюдения до написания кода |
| methodList/fieldList диапазоны при нескольких классах | единый проход endClass-регистрации; round-trip проверка диапазонов |
| this-параметр сдвигает индексы аргументов | централизованно в emitSimpleFunction (offset = isStatic ? 0 : 1) |
| базовые типы вне сборки (Any) | System.Object через [System.Runtime] — путь уже проверен M1 |
