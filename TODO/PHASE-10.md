# Phase 10: Классы — `.class`, поля, `.ctor`, instance-методы, `newobj`

- **Статус:** выполнена (все задачи, включая stretch 10.7; сетка зелёная)
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

### 10.0 Наблюдения — фактические IR-формы (K2 v2.4.20-RC, пробы P1–P7)

Пробы: `build/tmp/probe10/probes/*.kt`, дампы: `build/tmp/probe10/out/<имя>/ir-dump-<main>.txt`
(не коммитятся). Ключевые факты:

1. **visitFile сегодня молча пропускает классы** (`decl !is IrSimpleFunction → continue`):
   проба P6 собралась без ошибок, класс просто выпал из сборки. После 10.2
   пропуск классов станет невозможен.
2. **Класс**: `CLASS name modality:FINAL|OPEN visibility:public superTypes:[...]`;
   `thisReceiver` = DispatchReceiver `<this>` (index 0). Базовый класс — первый
   супертип, не `kotlin.Any` и не интерфейс; интерфейсы — остальные (кроме Any).
   Интерфейс: `CLASS INTERFACE modality:ABSTRACT superTypes:[kotlin.Any]`.
3. **Primary ctor синтезирован K2 всегда** (в т.ч. `class Empty`). Тело:
   ```
   DELEGATING_CONSTRUCTOR_CALL '<ctor> [primary] declared in kotlin.Any|Base'
     ARG x: GET_VAR <ctor-param> ...        // слота <this>-аргумента НЕТ
   INSTANCE_INITIALIZER_CALL classDescriptor=<свой класс>   // no-op для нас
   ```
   Инициализаторы полей в теле ctor НЕ присутствуют — они висят на
   `FIELD PROPERTY_BACKING_FIELD → EXPRESSION_BODY`. Эмиттер обязан сам
   инжектировать в ctor (после delegating call): `ldarg.0; <init-expr>; stfld`.
   Инжекция — только в ctor без this-делегации (иначе двойная инициализация).
4. **Свойства**: чтение/запись извне идут через **вызовы дефолтных аксессоров**
   `CALL <get-x> origin=GET_PROPERTY`, `CALL <set-x> origin=EQ|PLUSEQ`,
   а НЕ через IrGetField/IrSetField. IrGetField/IrSetField встречаются только
   внутри тел самих аксессоров:
   - `<get-x>`: `RETURN(GET_FIELD x; receiver=GET_VAR this)`;
   - `<set-x>`: `SET_FIELD x(receiver=this, value=GET_VAR <set-?>)`.
   Решение: эмитить дефолтные аксессоры как обычные instance-методы
   (имя verbatim `<get-x>`); их вызовы идут через общий callvirt-путь,
   спец-case в emitCall не нужен.
5. **FAKE_OVERRIDE повсюду**: у каждого класса — equals/hashCode/toString от
   Any; при наследовании — ещё и все унаследованные члены базы (и
   `PROPERTY FAKE_OVERRIDE`). Все — skip по `origin == IrDeclarationOrigin.FAKE_OVERRIDE`.
6. **Формы вызовов**:
   - instance-call: dispatch receiver занимает `arguments[0]` (слот есть);
   - `CONSTRUCTOR_CALL` (newobj) и `DELEGATING_CONSTRUCTOR_CALL`: слота
     receiver НЕТ — только value-args. newobj/call-base формируем вручную:
     args + токен (без emitExpr receiver).
7. **Полиморфизм**: callee полиморфного вызова резолвится в объявление в
   базовом классе (`open fun sound declared in Animal`) → callvirt по ref на
   declaring-class даёт виртуальную диспетчеризацию бесплатно. Но при чтении
   унаследованного свойства (`lp.<get-x>`) callee = fake_override в
   производном классе → при построении ref спускаться по `overriddenSymbols`
   до реальной функции.
8. **Модальности** (для 10.7): `open fun` → modality OPEN; `override fun`
   сохраняет OPEN + список `overridden:`; final-класс → modality FINAL.
9. **Expression body** понижен фронтендом до `BLOCK_BODY + RETURN` —
   существующий TODO visitExpressionBody недостижим (оставить как есть).
10. **Типы**: типы пользовательских классов в сигнатурах (`probe.p1.Point`)
    требуют резолва TypeRef — TypeMapper.mapType сейчас даёт fallback
    "object" (достаточно для PoC-теста, но cilTypeOf-хелпер вводим сразу).

Следствия для задач 10.1–10.2 зафиксированы; ожидаемая таблица D4 выше —
подтверждена с поправками из пунктов 3–7.

## Задачи

- [x] **10.0 Наблюдения**: probe-файлы (property var/val, ctor с init,
      наследование, интерфейс), ir-dump'ы, фиксация форм в этом файле.
      *Результат: таблица IR-форм дополнена (см. «10.0 Наблюдения»).*
- [x] **10.1 IlEmitter + PeIlEmitter**: D2/D3 — классы, поля, методы,
      ctor; реестр `test_backends`.
- [x] **10.2 Visitor**: visitFile dispatch (top-level fn + classes),
      visitClass/IrField/IrConstructor/IrDelegatingConstructorCall/
      IrConstructorCall/IrGetField/IrSetField; instance-call path
      (callvirt, this=arg0, params+1); NameMapper.className verbatim.
- [x] **10.3 Тест-проект `05-classes`** (`test-projects/05-classes/Classes.kt`):
      Point/Counter/LabeledPoint/Circle(:Shape)/Animal+Dog(open/override)/Empty;
      ожидания вывода — в шапке файла и `_verify_pe`; verifier OK.
- [x] **10.4 Дефолтный ctor + синтез**: класс без явного ctor
      (Empty в 05-classes; K2 сам синтезирует primary — ветка эмиттера
      остаётся страховкой).
- [x] ~~**10.5 Статические члены**~~ — перенесено в Phase 13 (companion
      objects); по решению от 2026-08-23 статические члены в фазе не делались,
      ldsfld/stsfld не вводились.
- [x] **10.6 Регрессия**: все существующие тесты зелёные (7/7);
      dual-run для старых тестов сохранён; `05-classes` помечен
      `test_backends=pe`; `--no-test` путь для pe-only тестов собирает
      артефакт без прогона.
- [x] **10.7 open/override** (stretch, выполнен): `open class`/`open fun`/
      `override` → Virtual|NewSlot / Virtual|ReuseSlot флаги +
      callvirt полиморфизм (Animal/Dog в 05-classes).

## Итоги реализации — дополнительные находки (после 10.0)

1. **TypeAttributes.Public = 0x00000001, а не 0x6.**
   **Откуда взялся 0x6:** из плана этой же фазы (§D3): «instance public —
   `0x0086` (Public|HideBySig)». Там `Public = 0x6` — корректное значение
   **MethodAttributes** (маска MemberAccess 0x0007: Public=0x6). При
   вычислении флагов **класса** (`classFlags()`) константа была перенесена
   по аналогии без сверки с **TypeAttributes**, у которого маска
   Visibility тоже 0x0007, но кодировки другие (NotPublic=0x0,
   Public=0x1; 2–7 зарезервированы под Nested*). Флаги `0x…106` дают
   видимость NestedFamANDAssem → невалидную для топ-уровня.
   Контейнерный класс не пострадал случайно: его флаги `0x00100181`
   скопированы дословно из работающего кода M1.
   **Симптом обманчив**: SRM-ридер спокойно читает такой образ, а CLR
   ругается только при первой реальной загрузке типа («format is
   invalid»), поэтому первичный верификатор проблему не поймал.
   Это второй случай в проекте с масками атрибутов (в M1 был
   `MethodAttributes.Static = 0x10` вне access-mask) — вывод: значения
   флагов брать только из спецификации/апстрима, не «по памяти».
2. **Ссылки на свои типы — прямые TypeDef (предвычисленные хэндлы).**
   Варианты ссылки на класс той же сборки:
   - TypeDefOrRef → TypeDef напрямую;
   - TypeRef с ResolutionScope = nil (ECMA допускает «тип из текущего модуля»).
   Сначала был реализован второй вариант; отладка Phase 10 шла параллельно
   с багом №1, и на него пало подозрение. **Эксперимент после исправления
   №1 показал: NIL-scope TypeRef корректно резолвится net10 CLR**
   (пробы наследования/полей/локалов — зелёные при включённом варианте).
   Тем не менее оставлен первый вариант (прямые TypeDef), потому что:
   - это каноническая форма — csc/ilasm ссылаются на типы текущего модуля
     именно через TypeDefOrRef→TypeDef, nil-scope TypeRef компиляторами
     не эмитится вообще;
   - MethodDef-вызовы всё равно требовали предвычисленных хэндлов
     (разрешение «вперёд» без второго прохода IR), так что предвычисление
     TypeDef ничего не стоит;
   - меньше строк в TypeRef/меньше косвенности при резолве MemberRef.
3. **Индексы параметров конструктора сдвинуты на +1**: у IrConstructor нет
   this-параметра в IR, но в IL arg0 = this (`emitGetValue`, shift).
4. **Двойной dispatch receiver**: общий префикс `emitCallArguments`
   перед операторным `when` эмитил receiver ещё раз до `emitInstanceCall`
   → лишнее значение на стеке → InvalidProgram. Теперь аргументы для
   пользовательских вызовов эмитит сам обработчик. (Позднее обнаружение —
   следствие того, что до 05-classes не было теста с вызовом метода
   экземпляра через переменную; покрыто.)
5. **Коллизии operator-имён**: пользовательский `fun inc()` ловился
   операторной веткой Phase 9 (`c.inc()` → `c + 1`). Операторные имена
   обрабатываются как IL-опкоды только если callee объявлен в `kotlin.*`.
   Системное решение — проектирование мэппинга имён
   ([TODO/F-name-case-annotations/F-02-name-mapping-design.md](F-name-case-annotations/F-02-name-mapping-design.md)).
6. **Override обязан быть Virtual даже в sealed/final классе** — иначе метод
   скрывает слот базы и полиморфизм через базовую переменную ломается
   (open в final-классе → дефолтные флаги, переопределять некому).
7. **Verifier расширен** детальным дампом (attrs/extends/диапазоны/
   TypeRef-scope) — он и нашёл расхождения с эталонами ilasm/Roslyn
   (`build/tmp/probe10/ref/`). Урок: сравнение с эталонным образом
   (ilasm/csc) локализует проблему быстрее ручного чтения байтов.

## Приёмка

1. `just test 05-classes` зелёный (backend=pe), verifier OK. ✅
2. Все прежние тесты зелёные (обе ветки там, где применимо) — 7/7. ✅
3. `dotnet-ildasm` показывает корректные .class/.field/.method/.ctor
   (включая virtual/newslot у open-методов). ✅
4. Round-trip: verifier (настоящий SRM) открывает каждый pe-артефакт
   сетки, включая 05-classes с 6 typedef'ами, полями и диапазонами. ✅
   (Gradle-round-trip I8 dotnetutils не расширялся: обратной зависимости
   на compiler-plugin нет; SRM-покрытие даёт verifier.)

## Риски

| Риск | Митигация |
|---|---|
| K2 lowering свойств неожиданной формы | 10.0 наблюдения до написания кода |
| methodList/fieldList диапазоны при нескольких классах | единый проход endClass-регистрации; round-trip проверка диапазонов |
| this-параметр сдвигает индексы аргументов | централизованно в emitSimpleFunction (offset = isStatic ? 0 : 1) |
| базовые типы вне сборки (Any) | System.Object через [System.Runtime] — путь уже проверен M1 |
