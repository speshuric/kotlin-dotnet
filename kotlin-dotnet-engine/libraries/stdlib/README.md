# kotlin-dotnet-stdlib (каркас)

Аналог `kotlin-stdlib-js` для таргета .NET: Kotlin-декларации, которые
компилятор плагина считает «родной» стандартной библиотекой.

**Статус:** каркас (этап A стратегии, `docs/stdlib-design.md`, ADR 0014).
К пользовательской компиляции не подключён; таблица перенаправлений
живёт в плагине (`StdlibMapping.kt`). Переключение на этот модуль как
источник деклараций — этап B (`-Xno-stdlib -cp …`), план в
`TODO/D-stdlib/D-03-stdlib-implementation-plan.md`.

## Раскладка

Пакеты копируют upstream `libraries/stdlib/{common,js}/src`:

- `kotlin/io` — println/print/readLine (шимы);
- `kotlin/text`, `kotlin/collections` — по мере покрытия;
- `kotlin/dotnet/annotations` — трансляционные аннотации компилятора
  (`@DotnetName` аналогичен `@JsName`; `@KotlinToPascalCase` /
  `@DotnetFromPascalCase` — файловые таргеты маппинга имён).

Аннотации — SOURCE-only инструкции компилятору, в метаданные сборки
не эмитируются.

## Сверка с апстримом

Минимальный состав деклараций сверять с апстримом v2.4.20-RC:
`.sources/kotlin/libraries/stdlib/common/src` + `js/src`, ориентир объёма —
каталоги `js-ir-minimal-for-test`, `jvm-minimal-for-test`.
