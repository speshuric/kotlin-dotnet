# J-01: Декомпозиция PeIlEmitter

- **Тема:** J. PE-бэкенд компилятора
- **Разметка:** POST-10.9 (после удаления TextIlEmitter)
- **Зависимости:** Phase 10 (текущее состояние), Phase 10.9 (удаление
  ilasm-ветки упростит контракт IlEmitter и уберёт двойную семантику)
- **Статус:** DONE (2026-08-26)

## Итог

`PeIlEmitter.kt` (958 строк) разбит на 8 файлов в `compiler/pe/`:

| Файл | Строк | Ответственность |
|---|---|---|
| `PeIlEmitter.kt` | 410 | контракт [IlEmitter], порядок вставки строк, оркестрация endContainerClass |
| `PeRecords.kt` | 59 | FieldRec/MethodRec/ClassRec, константы |
| `MemberRefParser.kt` | 137 | чистый парсинг call/field/type-ref'ов |
| `PeSignatures.kt` | 118 | сигнатуры, CIL-типовой DSL (resolver инъекцией) |
| `PeBodyEncoder.kt` | 108 | Op-иерархия + кодирование тел |
| `PeTypeDefWriter.kt` | 144 | синтез дефолтных ctor, диапазоны групп, TypeDef-строки |
| `PeReferenceResolver.kt` | 154 | резолв операндов, кэши TypeRef/AssemblyRef |
| `PeImageWriter.kt` | 46 | PE-сериализация |

Доступ к модели из resolver'а — инъекция колбэков
(findOwnMethod/describeOwnMethods/findOwnTypeDefRow). Приёмка:
grep-критерии (парсер не знает ModelRec — 0 совпадений; сигнатуры не знают
парсер — 0), полная сетка 7/7 зелёная, дампы dotnet-ildasm образов
03-hello/04-loops/05-classes побайтово идентичны до/после (без учёта MVID).

## Контекст

`PeIlEmitter.kt` вырос до ~1000 строк и продолжает расти с каждой фазой
(Phase 11 добавит массивы/строки, 12 — дженерики, 13 — companion/enums).
Сейчас в одном классе смешаны как минимум пять ответственностей:

1. **Модель типов файла** — `ClassRec`/`MethodRec`/`FieldRec`, порядок
   групп (контейнер → классы), отложенная финализация
   (`endContainerClass`: преаллокация хэндлов → тела → FieldDef →
   MethodDef → TypeDef с диапазонами).
2. **Парсинг текстовых member-ref'ов** — `parseCallRef`/`parseTypeName`/
   `fieldRefToken` (формат `ret [instance ][[Asm]]Ns.T::name(args)`),
   разрешение своих методов через предвычисленные хэндлы
   (`findOwnMethod`, `ownTypeHandle`).
3. **Построение сигнатур** — `buildSignature`/`fieldSignature`/
   `applyCilType` (+ маппинг примитивов, пользовательские типы).
4. **Буфер опкодов и кодирование тел** — `Op`-иерархия,
   `encodeBody`, лейблы, локалы.
5. **Сериализация образа** — `writeAssemblyTo`, assembly/module refs,
   кэши TypeRef/AssemblyRef.

## Что сделать

Спроектировать разбиение (предварительно):

- `PeClassModel` / `PeTypeRegistry` — группы типов, диапазоны, синтез
  дефолтного ctor, предвычисление хэндлов;
- `MemberRefParser` — текстовые ссылки call/field + резолв в токены;
- `SignatureWriter` — обёртка над BlobEncoders с нашим CIL-типовым DSL;
- тело-энкодер (`MethodBodyBuffer`) — Op-буфер + encodeBody;
- `PeAssemblyWriter` — сборка корня метаданных и PE.

Порядок шагов: сначала выделить чистые функции без состояния
(парсер, сигнатуры), затем модель типов, последним — буфер опкодов.

Ограничения:

- Контракт [IlEmitter] не расширять без нужды (после 10.9 пересмотреть:
  beginStaticMethod/endStaticMethod → единые begin/endMethod);
- Поведение побайтово не менять: сетка тестов + verifier должны остаться
  зелёными без правок ожиданий;
- Каждое выделение — отдельный коммит, тестируемый на полной сетке.

## Приёмка

- Ни один файл pe-бэкенда не превышает ~400 строк;
- `grep`-критерии: парсинг ссылок не знает о ModelRec; построение
  сигнатур не знает о парсере;
- Полная сетка зелёная, образы побайтово идентичны до/после
  (проверяется сравнением артефактов на фиксированном исходнике — MVID
  исключить из сравнения или фиксировать).
