# A-01: Логирование (общий фасад)

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** —
- **Статус:** TODO

## Контекст

В коде компилятор-плагина повторяется хардкод префикса `[kotlin-dotnet]`:

- `DotnetIrGenerationExtension.kt:23` — `System.err.println("[kotlin-dotnet] IR dump: ...")`
- `DotnetIrGenerationExtension.kt:25` — `System.err.println("[kotlin-dotnet] IR dump failed: ...")`
- `DotnetIrGenerationExtension.kt:39` — `System.err.println("[kotlin-dotnet] IL: ...")`
- `scripts/activate.sh:53` — `echo "[kotlin-dotnet] env activated"`
- `scripts/kotlinc-net.sh` — `[kotlinc-net] ...` (другой префикс, но та же идея)

Кроме дублирования, проблема в том, что `System.err.println` напрямую —
нет уровня, нет возможности отключить в прод-режиме, нет общего места
для переключения вывода (файл/консоль).

## Цель

Ввести общий фасад логирования в компилятор-плагине, убрать хардкод
префикса. Скрипты оставить как есть на первом этапе (см. A-06 для
общих частей скриптов).

## Задачи

1. Создать `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/Log.kt`
   — объект-фасад с методами `info`, `warn`, `error`. Все сообщения
   автоматически получают префикс `[kotlin-dotnet]`.
2. Контракт фасада:
   - `Log.info(msg: String)`
   - `Log.warn(msg: String)`
   - `Log.error(msg: String, throwable: Throwable? = null)`
   - Вывод: по умолчанию `System.err` (компилятор перехватывает stderr).
   - Уровень задаётся системным свойством `kotlin.dotnet.log.level`
     (INFO/WARN/ERROR; default INFO). Сейчас достаточно дёргать `System.err`
     напрямую, абстракцию держать тонкой.
3. В `DotnetIrGenerationExtension.kt` заменить все `System.err.println("[kotlin-dotnet] ...")`
   на `Log.info(...) / Log.error(...)`.
4. Сохранить текущий вывод (сообщения те же, префикс тот же — чтобы
   не сломать парсинг в скриптах/тестах).
5. Не вводить зависимость на slf4j/logback — компилятор-плагин должен
   оставаться тонким; фасад — ≤ 50 строк.

## Контракт (минимальный)

```kotlin
object Log {
    fun info(msg: String)
    fun warn(msg: String)
    fun error(msg: String, throwable: Throwable? = null)
}
```

## Приёмка

- `grep -rn '\[kotlin-dotnet\]' compiler-plugin/` → 0 совпадений вне `Log.kt`.
- `just test-all` зелёный.
- Вывод `just compile test-projects/03-hello/hello.kt` содержит те же
  строки, что и раньше (монтажно).

## Заметки исполнителю

- Не трогать скрипты в этой задаче — см. A-06.
- Не вводить левые зависимости (slf4j и т.д.).
- Файл `Log.kt` — единственное место, где живёт строка `"kotlin-dotnet"`.
