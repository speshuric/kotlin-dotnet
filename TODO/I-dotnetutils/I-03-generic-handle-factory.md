# I-03: Универсальная фабрика типизированных хэндлов («на подумать»)

- **Тема:** I. dotnetutils
- **Разметка:** POST-I4 (созреть до внедрения в compiler-plugin)
- **Статус:** TODO (идея, отложена по итогам ревью итерации токенов)

## Суть

В `MetadataTokens` 25 однотипных фабрик вида:

```kotlin
fun methodDefinitionHandle(rowNumber: Int): MethodDefinitionHandle =
    MethodDefinitionHandle.fromRowId(toRowId(rowNumber))
```

Идея: сделать `fromRowId` универсальной — extension/generic с типом-маркером,
чтобы вся конструкция свелась к одной функции.

## Рассмотренные варианты

1. **Marker-interface на компаньонах**
   (`interface EntityHandleFactory<T> { fun fromMaskedRowId(rowNumber: Int): T }`,
   компаньоны хэндлов его реализуют) + generic-функция.
   Минусы: правки всех 25 файлов хэндлов; портовая машинерия попадает в
   публичный API, увеличивая расхождение с оригиналом SRM.
2. **Factory-lambda параметр**
   (`inline fun <T> rowIdHandle(rowNumber: Int, factory: (Int) -> T)`).
   Экономии почти нет — вызов всё равно перечисляет фабрику.
3. **Reflection над компаньонами** — исключено (производительность + запрет
   java.reflect зависимостей по духу «чистого kotlin»).

## Решение

Отложено. Повторение содержится в одном файле, список фабрик фиксирован
набором таблиц метаданных и не растёт. Вернуться при **внедрении
dotnetutils в compiler-plugin**: станет видно, какие фабрики реально
нужны компилятору, и не окажется ли нужным API вида
`token -> EntityHandle` вместо типизированных фабрик вообще.

Связанные: [I-01](I-01-metadata-builder.md), план IMPLEMENTATION-PLAN.md.
