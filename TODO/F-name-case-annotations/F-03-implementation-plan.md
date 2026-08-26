# F-03: План реализации маппинга идентификаторов Kotlin ↔ .NET

- **Статус:** TODO (большой чек-лист; закрывается итеративно по мере
  роста фаз языка и кодогенератора)
- **Спека:** [`docs/identifier-mapping.md`](../../docs/identifier-mapping.md)
- **Зависимости:** F-01, F-02, Phase 10–13+, ADR 0005

## Архитектурный контекст

Сейчас `NameMapper` — stateless object: `methodName`/`className`/
`namespace` возвращают verbatim. `PeRecords` (`FieldRec`/`MethodRec`/
`ClassRec`) хранят уже готовые имена строк. Спецификация требует:

1. **Двухимённость** — каждый символ имеет *Kotlin-имя* (исходное) и
   *.NET-имя* (преобразованное или verbatim). Сейчас хранится только
   одно.
2. **Атрибут `KotlinIdentifierAttribute`** — на каждом экспортируемом
   символе; требует генерации CustomAttribute, которой пока нет в
   `PeIlEmitter` (DebuggableAttribute — единственный user).
3. **Эвристики round-trip** — camelCase↔PascalCase, префикс `I`, суффикс
   `Attribute` и т.д.
4. **Детектор коллизий** — безусловный, на этапе компиляции.
5. **`@DotnetName`** — аннотация-override (аналог `@JvmName`).
6. **Assembly-level атрибут-таблица** для namespace↔package маппинга.

Значительная часть плана упирается в отсутствие IR-сущностей, которых
компилятор пока не обрабатывает (object, companion, enum entries,
интерфейсы с префиксом I, value class, data class, annotations).
Поэтому план разбит на **слои**: инфраструктура, которая может быть
заложена уже сейчас, и прикладные правила, которые включаются по мере
появления соответствующих IR-узлов в кодогенераторе.

---

## Слой 0: инфраструктура (можно делать сейчас)

### 0.1 Двухимённость в модели данных

- [ ] Добавить `kotlinName: String` в `FieldRec`, `MethodRec`,
      `ClassRec` (наряду с существующим `name`, который становится
      .NET-именем). Где .NET-имя = Kotlin-имя (verbatim, текущее
      поведение), `kotlinName` = то же; где .NET-имя преобразовано —
      `kotlinName` хранит оригинал.
- [ ] `NameMapper` → расширить: `dotnetMethodName`/`dotnetClassName`/
      `dotnetNamespace` (преобразование) + `kotlinName` (возврат
      исходного). Пока `dotnet*` = verbatim; позже подключаются правила.
- [ ] `PeIlEmitter.beginMethod`/`beginClass`/`declareField` принимают
      ОБА имени; visitor передаёт `kotlinName` из IR и `dotnetName` из
      NameMapper.

### 0.2 Генерация CustomAttribute (обобщённая)

- [ ] Выделить `PeReferenceResolver.addAssemblyDebuggableAttribute`
      в обобщённый `addCustomAttribute(parent, ctorTypeRef, ctorSig,
      valueBlob)` — переиспользуется и для `KotlinIdentifierAttribute`,
      и для `@DotnetName`, и для будущих атрибутов.
- [ ] Добавить в `PeRecords` флаг `kotlinIdentifierEmitted: Boolean`
      на MethodRec/FieldRec/ClassRec — нужен, чтобы не дублировать
      атрибут при повторной эмиссии (страховка).

### 0.3 Runtime-тип `KotlinIdentifierAttribute`

- [ ] Добавить класс `KotlinIdentifierAttribute` в
      `KotlinDotnetRuntime` (C#, `Kotlin.Runtime.*`):
      `[AttributeUsage(AttributeTargets.All)]` class with
      `string OriginalName` + `int SchemaVersion = 1`.
- [ ] AssemblyRef на KotlinDotnetRuntime уже эмитится всегда —
      TypeRef/MemberRef на атрибут строятся по тем же путям, что
      `Kotlin.Runtime.Print`.

### 0.4 Детектор коллизий camelCase→PascalCase

- [ ] В `PeIlEmitter.endContainerClass` (когда все имена известны):
      собрать все .NET-имена методов/свойств внутри одного класса
      (или типа-контейнера), проверить, нет ли пар, различающихся
      только регистром первой буквы. Если есть — ошибка компиляции
      (fail-fast), с указанием обоих исходных Kotlin-имён.
- [ ] Пока это применимо только к top-level функциям (контейнер
      `<File>Kt`) и методам пользовательских классов (Phase 10).
      По мере появления object/companion/enum — расширять.

### 0.5 `@DotnetName` — аннотация (каркас)

- [ ] Зарегистрировать annotation class `DotnetName` в компиляторе
      (compiler-plugin registration или stub в runtime). Пока не
      обрабатывается IR — просто задел.
- [ ] В `NameMapper` — функция `resolveDotnetName(declaration): String?`
      (читает `@DotnetName` из IR-аннотаций, если есть). Возвращает
      override-имя или null.
- [ ] В NameMapper: если `@DotnetName` присутствует, оно заменяет
      .NET-имя; `kotlinName` остаётся исходным.

---

## Слой 1: прикладные правила — по мере появления IR-сущностей

### 1.1 camelCase→PascalCase для методов/свойств (Phase 9–10, есть)

- [ ] `NameMapper.dotnetMethodName`: `camelCase` → `PascalCase`
      (только первая буква). Если имя уже `PascalCase` или начинается
      с `_` — не менять.
- [ ] `NameMapper.dotnetPropertyName`: то же.
- [ ] Эмиссия `KotlinIdentifierAttribute(originalName = kotlinName)` на
      каждом методе/свойстве, где .NET-имя ≠ Kotlin-имени.
- [ ] Обновить `DotnetIrVisitor`: вызовы `NameMapper.methodName` →
      `dotnetMethodName`; `callRef`/`fieldRef`-строки, которые visitor
      строит для `PeIlEmitter`, должны использовать .NET-имена.
- [ ] **Тесты**: existing 7 tests (output unchanged — методы в тестах
      уже `main`/`test_add` → `Main`/`Test_add`; C# consumer ожидает
      `test_add`? — проверить, не сломает ли; consumer ссылается по
      имени → потребуется синхронизация csproj HintPath или expect).

### 1.2 Интерфейсы: префикс `I` (Phase 10, есть)

- [ ] `NameMapper.dotnetClassName` для интерфейса: prepend `I` если
      ещё нет. Условие: `IrClass` с `kind == CLASS_INTERFACE`
      (или `flags and INTERFACE_FLAG`).
- [ ] `KotlinIdentifierAttribute(originalName)` на TypeDef.
- [ ] Round-trip импорт: если имя начинается с `I` + PascalCase и есть
      атрибут → снять `I`; без атрибута — эвристика.

### 1.3 Константы UPPER_SNAKE→PascalCase (Phase 9–10, есть)

- [ ] `NameMapper.dotnetFieldName` для `const val` / `IrField` с
      `isConst`: `UPPER_SNAKE` → `PascalCase`.
- [ ] `KotlinIdentifierAttribute(originalName = UPPER_SNAKE)`.

### 1.4 Enum entries (Phase 13, TODO)

- [ ] `NameMapper.dotnetEnumEntryName`: всегда `PascalCase`.
- [ ] `KotlinIdentifierAttribute(originalName)` с исходным регистром.
- [ ] Зависимость: enum class в кодогенераторе (Phase 13).

### 1.5 Annotation classes: суффикс `Attribute` (Phase 13, TODO)

- [ ] `NameMapper.dotnetClassName` для annotation class: append
      `Attribute` если ещё нет.
- [ ] `KotlinIdentifierAttribute(originalName)` без суффикса.
- [ ] Зависимость: annotation class в кодогенераторе.

### 1.6 object / companion object (Phase 13, TODO)

- [ ] `object Foo` → TypeDef `Foo` + static field `Instance` (singleton
      accessor). `KotlinIdentifierAttribute` на TypeDef с исходным
      именем.
- [ ] `companion object` → TypeDef `Companion` (или явное имя) +
      `Instance` + static-redirect методы на охватывающем классе.
- [ ] Имя `Instance` — фиксированное, не подлежит конвертации.
      Коллизия с member `Instance` → tie-break (переименование
      accessor).
- [ ] Зависимость: object/companion в кодогенераторе (Phase 13).

### 1.7 value class / data class (Phase 13+, TODO)

- [ ] `value class` → `readonly struct`; `data class` (val-only,
      без циклов) → `readonly record struct`. `data class` с `var` →
      `sealed record`.
- [ ] Самоссылающееся поле struct → индирекция (ссылочная обёртка).
- [ ] `KotlinIdentifierAttribute` на TypeDef.
- [ ] Зависимость: struct-генерация в кодогенераторе (Phase 13+).

### 1.8 Namespace→PascalCase (Phase 9, есть, но отложено)

- [ ] `NameMapper.dotnetNamespace`: capitalize каждый сегмент.
- [ ] Assembly-level `KotlinIdentifierAttribute`-таблица: список пар
      `namespace → kotlin package` (один CustomAttribute на сборку,
      value = blob с массивом пар, или отдельный класс-атрибут).
- [ ] Round-trip: атрибут-таблица → исходный путь; без — эвристика
      lowercase (lossy для составных сегментов).
- [ ] Влияет на все test-артефакты — координированный переключатель.

---

## Слой 2: round-trip импорт .NET→Kotlin (POST-PoC)

- [ ] `MetadataReader`-обход сборки: для каждого TypeDef/MethodDef/
      FieldDef проверить наличие `KotlinIdentifierAttribute` → взять
      `originalName`; иначе применить эвристику (снять `I`, снять
      `Attribute`, PascalCase→camelCase).
- [ ] Companion-дискриминатор: в v0.01 — эвристика по имени `Companion`;
      в будущем — бит-дискриминатор в схеме атрибута v2.
- [ ] Redirect-статики игнорируются (по structural-признаку: метод на
      охватывающем классе, тело = прямой `call` на companion-метод).
- [ ] Тесты round-trip: собрать Kotlin → .NET → прочитать назад →
      сравнить имена с исходными.

---

## Слой 3: переключатели и CLS (POST-PoC)

- [ ] Assembly-level флаги `DisableKotlinToDotnetNameConversions` /
      `DisableDotnetToKotlinNameConversions` (черновые имена).
- [ ] `[CLSCompliant(true)]` — решение: заявлять или нет (отдельно от
      детектора коллизий, который работает безусловно).
- [ ] Координация границ «модуль» Kotlin ↔ «сборка» .NET (подтвердить
      совпадение в модели компилятора для `internal`).

---

## Приёмка по слоям

| Слой | Критерий готовности |
|---|---|
| 0 | Двухимённость в модели; `KotlinIdentifierAttribute` эмитится на методах с преобразованным именем; детектор коллизий работает; `@DotnetName` читается из IR (если есть); runtime-класс `KotlinIdentifierAttribute` собран |
| 1 | Все правила из таблицы спеки, для которых есть IR-поддержка, имплементированы и покрыты тестами (output .NET-сборки проверяется verifier'ом + pdbcheck; C# consumer обновлён) |
| 2 | Round-trip .NET→Kotlin восстанавливает исходные имена (через атрибут или эвристику); тесты round-trip зелёные |
| 3 | Переключатели работают; CLS-решение принято и зафиксировано в ADR |
