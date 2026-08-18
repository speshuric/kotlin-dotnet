# TODO — структурированный план выравнивания

Эта папка содержит полные тексты задач, сгруппированных по темам.
Индекс с рекомендуемой последовательностью — в этом файле ниже.
Файлы задач именуются `<NN>-<slug>.md`, где `NN` — номер внутри темы
(глобальный порядок выполнения задаётся таблицей ниже, а не номером файла).

## Как пользоваться

- **Перед началом задачи** агент/субагент читает только свой файл задачи +
  AGENTS.md + ADR, к которому задача привязана. Контекст должен быть
  минимальным (одна задача = один фокус). Не надо перечитывать весь проект.
- **Результат** — изменение кода в репозитории + коммит по явному указанию
  пользователя. TODO-файл при необходимости помечается `[DONE]` или ссылкой
  на коммит.
- **Фиксированные фазы** (Phase 9, 10, ...) приоритетнее «выравнивающих»
  задач, если только задача явно не помечена как «до фазы 9» (PRE).
- **Статусы задач** в таблице ниже: `TODO` (не начата), `WIP`, `DONE`,
  `BLOCKED`, `DEFERRED`.

## Разметка по фазам

- **PRE-9** — выравнивание, которое снижает риск Phase 9 и должно быть
  сделано до её начала. Сознательно держим список коротким: только то,
  что прямо мешает Phase 9 или ломает инварианты (логирование, контракт
  IlEmitter, каркас visitor-обработчиков с TODO).
- **PRE-10** — имеет смысл сделать после Phase 9, но до Phase 10 (классы),
  т.к. классы потянут `IlEmitter` в сторону instance-методов и общий
  интерфейс эмиттера.
- **POST-9** — можно делать после Phase 9, не блокируя Phase 10.
- **POST-PoC** — дизайн/исследование, которые сейчас давать рано
  (например, корутины, bootstrapping runtime), их нельзя трогать до
  стабилизации базовых фаз (10–12).

## Практики для исполнителей (production-ready + экономия контекста)

1. **Одна задача — один PR/коммит.** Если задача разрослась — выделить
   подтемы в отдельные файлы.
2. **Минимальный контекст исполнителя:** агент получает только свой
   TODO-файл + ссылки на конкретные файлы/ADR, которые нужно трогать.
   Не подавать весь AGENTS.md — только раздел, к которому относится задача.
3. **Контракт вместо реализации:** где возможно, описать интерфейс
   (Kotlin interface/sealed API) + тест-заглушки; реализация — отдельная
   задача. Так несколько агентов не наступают друг другу на ноги.
4. **Не invent-ить ADR:** каждое «грязное» решение фиксировать в `adr/`
   со статусом `Proposed`/`Accepted` и ссылкой на TODO-задачу.
5. **Регрессия обязательна:** после каждой задачи — `just test-all`
   зелёный. Новые инварианты — в тестовый проект (или unit-тесты
   компилятор-плагина, когда появятся).
6. **TODO/NotImplemented вместо молчания:** всё, что не реализовано,
   должно бросать `TODO("...")` (Kotlin) или `NotImplementedException`
   (C#) с указанием IR-узла/функции. Запрещён `return Unit`-заглушки,
   которые маскируют отсутствие реализации.
7. **Токен-экономия:** большие файлы резать по ответственности
   (`IlEmitter` → `IlEmitter` + `IlOpcode` enum + `IlMethod`/`IlAssembly`),
   но не по篇幅. Каждый файл — < ~300 строк, если растёт — дробить
   дальше по темам.
8. **Сначала контракт, потом хардкод:** при появлении магических строк
   (origin-имена, opcode-имена) — выносить в `enum`/`object` сразу,
   чтобы не плодить `when ("GT")` по коду.
9. **Логирование через общий фасад** (см. A-01), чтобы убрать
   `[kotlin-dotnet]` хардкод из каждого `System.err.println`.
10. **Коммиты — только по явному указанию пользователя** (AGENTS.md §14).

---

## Индекс задач (рекомендуемая последовательность)

Ниже — таблица «исполнитель → файл». Порядок строк = рекомендуемый
порядок запуска (но можно параллелить в пределах одной темы, если
задачи не зависят друг от друга — зависимости указаны в файлах).

| # | Тема/задача | Файл | Разметка | Зависимости | Статус |
|---|---|---|---|---|---|
| 1 | Логирование (фасад) | [A-01-logging.md](A-infrastructure/A-01-logging.md) | PRE-9 | — | TODO |
| 2 | DotnetIrGenerationExtension: имя файла, onFailure | [A-02-ir-generation-extension.md](A-infrastructure/A-02-ir-generation-extension.md) | PRE-9 | A-01 | TODO |
| 3 | IlEmitter: интерфейс + контракт AST | [A-03-il-emitter-contract.md](A-infrastructure/A-03-il-emitter-contract.md) | PRE-9 | — | TODO |
| 4 | IlEmitter: opcodes → enum | [A-04-il-opcodes-enum.md](A-infrastructure/A-04-il-opcodes-enum.md) | PRE-9 | A-03 | TODO |
| 5 | DotnetIrVisitor: убрать хардкод origin/stdlib | [A-05-visitor-hardcode.md](A-infrastructure/A-05-visitor-hardcode.md) | PRE-9 | A-01, A-04 | TODO |
| 6 | scripts review: вынести общие части | [A-06-scripts-review.md](A-infrastructure/A-06-scripts-review.md) | PRE-9 | A-01 | TODO |
| 7 | Visitor scaffolding: план-карта обработчиков Ir-узлов | [B-01-visitor-node-map.md](B-visitor-scaffolding/B-01-visitor-node-map.md) | PRE-9 (карта), POST-9 (реализация) | A-05 | TODO |
| 8 | TypeMapper: Unit vs void, рефакторинг | [C-01-typemapper-unit-void.md](C-typemapper/C-01-typemapper-unit-void.md) | PRE-10 | — | TODO |
| 9 | kotlin-dotnet-stdlib: каркас + неявный импорт | [D-01-stdlib-skeleton.md](D-stdlib/D-01-stdlib-skeleton.md) | PRE-10 (каркас), POST-9 (механика) | A-01, A-05 | TODO |
| 10 | StdlibResolver → разрешение через stdlib | [D-02-resolve-stdlib-call.md](D-stdlib/D-02-resolve-stdlib-call.md) | POST-9 | D-01 | TODO |
| 11 | IR: какая платформа/проходы (tailrec и др.) | [E-01-ir-platform-investigation.md](E-ir-platform/E-01-ir-platform-investigation.md) | PRE-9 (исследование), POST-9 (фикс) | — | TODO |
| 12 | PascalCase/camelCase: аннотации @kotlinToPascalCase / @dotnetFromPascalCase | [F-01-name-case-annotations.md](F-name-case-annotations/F-01-name-case-annotations.md) | POST-9 (план), POST-PoC (механика) | — | TODO |
| 13 | Дизайн: лямбды и захват контекста | [G-01-lambdas-capture.md](G-internal-design/G-01-lambdas-capture.md) | POST-9 (дизайн), POST-10 (реализация) | B-01, A-03 | TODO |
| 14 | Дизайн: итераторы (общий + специализированные по диапазонам/строкам) | [G-02-iterators.md](G-internal-design/G-02-iterators.md) | POST-9 (дизайн) | B-01 | TODO |
| 15 | Дизайн: hash-коллизии и районы, где напоремся | [G-03-hash-design.md](G-internal-design/G-03-hash-design.md) | POST-9 (дизайн) | — | TODO |
| 16 | Миграция тестов из kotlin-компилятора | [H-01-kotlin-test-migration.md](H-test-migration/H-01-kotlin-test-migration.md) | POST-PoC | Phase 9–12 done | TODO |

## Краткая карта тем

```
A. Infrastructure (PRE-9)         — логирование, IlEmitter-контракт, extension, scripts
B. Visitor scaffolding (PRE-9 map) — план-карта обработчиков Ir-узлов
C. TypeMapper (PRE-10)             — Unit↔void, рефакторинг
D. kotlin-dotnet-stdlib (PRE-10)   — каркас, неявный импорт, resolver
E. IR platform (PRE-9 research)    — какая платформа генерила IR (tailrec)
F. Name-case annotations (POST-9)  — PascalCase/camelCase через аннотации
G. Internal design (POST-9)        — лямбды, итераторы, hash
H. Test migration (POST-PoC)       — перенос тестов из kotlin-компилятора
```

## Связь с AGENTS.md

- Полный контекст проекта — `AGENTS.md`.
- Этот файл — источник правды для «что делать следующим».
- Ссылка на `TODO/README.md` добавлена в `AGENTS.md` § «План работ».
