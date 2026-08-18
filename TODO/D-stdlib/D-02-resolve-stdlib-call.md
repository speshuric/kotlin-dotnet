# D-02: StdlibResolver — разрешение вызовов через stdlib/runtime

- **Тема:** D. kotlin-dotnet-stdlib
- **Разметка:** POST-9
- **Зависимости:** D-01 (каркас stdlib)
- **Статус:** TODO

## Контекст

Файл: `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/StdlibResolver.kt`.

Сейчас:
```kotlin
fun resolveStdlibCall(call: IrCall, returnTypeCil: String,
                      paramCilTypes: List<String>): String? {
    val fn = call.symbol.owner as? IrSimpleFunction ?: return null
    val fqName = fn.kotlinFqName.asString()
    return when (fqName) {
        "kotlin.io.println" -> "void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println($paramSig)"
        "kotlin.io.print"   -> "void [KotlinDotnetRuntime]Kotlin.Runtime.Print::print($paramSig)"
        else -> null
    }
}
```

Проблемы:
1. **Таблица хардкожена в коде.** Каждый новый stdlib-вызов
   (`readLine`, `listOf`, `arrayOf`, ...) требует правки resolver.
2. **Нет перегрузок по типу.** `println(int)` и `println(string)` —
   разные runtime-методы (см. `Print.cs`), но resolver сейчас
   подставляет CIL-тип аргумента и полагается на то, что в runtime
   есть перегрузка. Это работает для println (есть все перегрузки),
   но для, например, `listOf` — нет.
3. **Нет связи с kotlin-dotnet-stdlib (D-01).** Resolver —
   самостоятельная таблица, а должен (в будущем) ходить по
   IR-декларациям stdlib.
4. `needsRuntime` — дублирует логику `resolveStdlibCall` (`fqName ==
   "kotlin.io.println" || fqName == "kotlin.io.print"`).

Пользователь: «resolveStdlibCall — см. выше kotlin-dotnet-stdlib
должен решать это».

## Цель

Рефакторинг `StdlibResolver`:
- Внешняя таблица (data-driven) fqName → runtime-сигнатура.
- Чёткое разрешение перегрузок по типам аргументов.
- Путь к будущему: когда появится kotlin-dotnet-stdlib с реальным
  IR, resolver ходит по IR, а не по таблице.
- Вынести проверку «это stdlib-вызов?» в `isStdlibCall`.

## Задачи

1. Сделать `StdlibResolver` data-driven:
   ```kotlin
   object StdlibResolver {
       private data class StdlibEntry(
           val fqName: String,
           val runtimeClass: String,   // e.g. "Kotlin.Runtime.Print"
           val runtimeMethod: String,  // e.g. "println"
           val returnCil: String        // e.g. "void"
       )
       private val TABLE = listOf(
           StdlibEntry("kotlin.io.println", "Kotlin.Runtime.Print", "println", "void"),
           StdlibEntry("kotlin.io.print",   "Kotlin.Runtime.Print", "print",   "void"),
           // ... расширяется в Phase 11+ (readLine, listOf, ...)
       )
       fun isStdlibCall(call: IrCall): Boolean
       fun resolveStdlibCall(call: IrCall, returnTypeCil: String,
                             paramCilTypes: List<String>): String?
   }
   ```
2. Разрешение перегрузок: для `println` — подставлять `paramCilTypes`
   в сигнатуру (как сейчас, работает, т.к. runtime имеет все
   перегрузки). Для будущих (где перегрузок нет) — TODO-стратегия
   (generic `object`-перегрузка или runtime-диспетч).
3. `isStdlibCall` — единая точка проверки, убрать дублирование с
   `needsRuntime`. `needsRuntime` — deprecated alias или удалить,
   мигрировать вызовы (A-05 уже делегирует сюда).
4. Для не-stdlib-вызовов (возврат null) — сейчас visitor падает с
   TODO (см. A-05). Оставить так; расширять таблицу по фазам.
5. Подготовить хук для будущей интеграции с kotlin-dotnet-stdlib:
   - Если `IrSimpleFunction` имеет аннотацию `@DotnetName(...)` —
     использовать имя из аннотации для runtimeMethod.
   - Пока — TODO (аннотации не реализованы, F-01/D-01).
6. Зафиксировать в ADR 0002 (обновить) или 0008: стратегия resolver
   — таблица (сейчас), IR-walk (будущее, после bootstrapping).

## Приёмка

- `StdlibResolver` — data-driven таблица, не `when` с хардкодом.
- `isStdlibCall` — единая проверка, `needsRuntime` убран/ deprecated.
- `just test-all` зелёный (println/print работают как раньше).
- Добавить тестовый кейс (в `02-expr` или новый `04-loops`) на вызов
  `print(...)` (чтобы покрыть вторую ветку таблицы).

## Заметки исполнителю

- Контекст: `StdlibResolver.kt`, `DotnetIrVisitor.kt` (где
  вызывается), `runtime/src/Print.cs` (перегрузки).
- Не реализовывать все stdlib-функции — только рефакторинг таблицы.
  Расширение — Phase 11+.
- Если `@DotnetName` не реализован (D-01/F-01) — оставить TODO в
  resolver, не падать.
