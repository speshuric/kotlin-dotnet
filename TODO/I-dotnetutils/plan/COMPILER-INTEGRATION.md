# Compiler Integration Plan: PE-бэкенд в compiler-plugin (без ilasm)

- **Статус:** на утверждении
- **ADR:** 0010 (пишется параллельно)
- **Контекст:** порт dotnetutils завершён (I0–I7 + M1, см.
  REMAINING-ROADMAP.md); это устранение архитектурного долга — отход от
  основного фазового пути (PHASE-10 зарезервирована под классы).

## Цель

`compiler-plugin` генерирует .NET DLL/EXE напрямую через
`org.kotlindotnet.dotnetutils.system.reflection`
(MetadataBuilder + InstructionEncoder + ManagedPEBuilder), минуя стадию
IL-текста и внешний процесс ilasm. Старый путь сохраняется как fallback.

## Принципы

1. **Параллельный путь.** Новый бэкенд включается CLI-флагом
   `plugin:kotlin.dotnet:backend=pe`; дефолт (`il`) не меняется до
   полной стабилизации. `kotlinc-net.sh` выбирает шаг по флагу.
2. **Малые вертикальные срезы.** Каждый шаг заканчивается зелёным e2e
   через существующую тестовую сетку; «сначала всё перенесём» — запрещено.
3. **Переиспользование.** `NameMapper`, `TypeMapper`, `StdlibResolver`,
   `DotnetIrVisitor` (логика обхода IR) переиспользуются; заменяется
   только эмиттер и добавляется построитель метаданных.
4. **Отклонения фиксируются** в ADR-0010 и шапках файлов.

## Декомпозиция

### S1. Вертикальный срез: hello-world через PE из плагина

Перенести логику фикстуры `HelloWorldImage` (тест 05-pe-hello) в
compiler-plugin:

- `DotnetCommandLineProcessor`: новая опция `backend` (`il` | `pe`);
- новый класс `PeBackendEmitter` (или пакет `pe/`): строит
  MetadataBuilder (Module, Assembly, AssemblyRef System.Runtime /
  KotlinDotnetRuntime, TypeRef → Print, MemberRef println(object),
  TypeDef `<File>Kt`, MethodDef main) + IL через InstructionEncoder
  (ldstr / call / ret) + ManagedPEBuilder;
- `DotnetIrGenerationExtension`: при backend=pe маршрутизирует в новый
  эмиттер;
- `kotlinc-net.sh`: при backend=pe пропускает шаг ilasm.

**Критерий:** `kotlinc-net.sh -- -P plugin:kotlin.dotnet:backend=pe
test-projects/03-hello/hello.kt` даёт EXE, который запускается и
проходит verifier. Тест 03-hello зелёный обоими путями.

### S2. Вызовы и арифметика: 00-int-add

- пользовательские top-level функции → MethodDef + call;
- параметры/возврат Int; локальные переменные → LocalVariableSignature
  (BlobEncoders!) + ldloc/stloc;
- сигнатуры функций через BlobEncoders вместо рукописных блобов.

**Критерий:** `just test 00-int-add` зелёный с backend=pe
(C#-consumer печатает 5).

### S3. Ветвления и выражения: 02-expr

- if/when, сравнения → ControlFlowBuilder + LabelHandle (вместо
  текстовых меток);
- Long/Double, константы, унарные/бинарные операторы.

**Критерий:** `just test 02-expr` зелёный с backend=pe
(16 строк, C#-consumer).

### S4. Циклы: 03-hello уже покрыт; 04-loops + spec

- while/do-while/break/continue через ControlFlowBuilder;
- break/continue во вложенных циклах — стек LabelHandle'ов.

**Критерий:** `just test 04-loops` и `just test 04-loops-spec`
зелёные с backend=pe.

### S5. Инфраструктура

- `scripts/tests.sh` / `build-test.sh`: прогон каждого теста на обоих
  бэкендах (id остаётся один; внутри — два прогона);
- verifier вызывается для pe-артефактов автоматически.

**Критерий:** `just test all` гоняет обе ветки; отчёт разделяет их.

### S6. Переключение дефолта и зачистка

- backend=pe становится дефолтом;
- TextIlEmitter + ilasm-шаг помечаются deprecated, затем удаляются
  (отдельный коммит);
- обновление AGENTS.md/README.

**Критерий:** все e2e зелёные без ilasm; ilasm нужен только для
04-loops-spec? — нет, spec-тесты тоже на новом пути.

## Риски / открытые вопросы

| Риск | Митигация |
|---|---|
| Порядок строк MetadataBuilder (validateOrder) | ловится на S1–S2, ошибки диагностируемы |
| Спецмассивы (Phase 11) отсутствуют в обоих путях | не блокирует; на новом пути проще (SZArray encoder) |
| Имена, совпадающие с CIL-опкодами | проблема ilasm-текста исчезает |
| Двойная поддержка путей затянется | дедлайн: S6 сразу после S4; S5 параллельно |

## Вне скоупа (не трогаем)

Классы/поля/OOP (PHASE-10+), спецмассивы как фича, корутины,
readLine/std-библиотека сверх текущего runtime.
