# ADR 0006: `just` как единая точка входа для сборки

- **Дата:** 2026-08-17
- **Статус:** Accepted

## Контекст

Проект гетерогенный: Gradle (compiler plugin), kotlinc CLI, .NET SDK
(ilasm, dotnet, dotnet-ildasm), C# consumer. Изначально оркестрация
делалась через bash-скрипты в `scripts/` (`bootstrap.sh`, `build-test-add.sh`,
`install-sdks.sh`, `install-sources.sh`, `activate.sh`). Проблемы:

- Нет единой точки входа: новичку непонятно, с какого скрипта начинать.
- `build-test-add.sh` разрастается, дублирует логику env-активации.
- Нет инкрементальности: полный pipeline гоняется каждый раз.
- `make help`-стиля документации нет.

## Решение

Использовать **`just`** (justfile) как единый диспетчер сборки.

## Почему `just`

| Критерий | `just` | `make` | `Gradle` |
|---|---|---|---|
| Синтаксис | чистый, без tab-чувствительности | tab-чувствительный | громоздкий для не-JVM |
| Инкрементальность | нет нативной (через bash-проверки) | mtime-таргеты | только для Gradle-tasks |
| Кросс-платформенность | да (Windows тоже) | Windows = WSL/Git Bash | да |
| Гетерогенный pipeline | нативно (shebang-рецепты) | нативно | боль (Exec tasks, env) |
| Зависимости | да (`recipe: dep1 dep2`) | да | да |
| Документация | `just --list` | `make help` (через трюки) | `./gradlew tasks` |
| Наличие | ставится легко (`pacman -S just` и т.п.) | везде (base-devel) | тащим локально |

`just` уже установлен в системе (1.58.0). Не добавляет зависимости в проект
(не локализован в `.sdk/`, т.к. это инструмент диспетчера, а не SDK).

## Структура `justfile`

- **Окружение:** `bootstrap`, `sdks`, `sources`.
- **Сборка:** `plugin` (Gradle), `il` (kotlinc + plugin), `dll` (ilasm).
- **Тест:** `test` (dotnet run C# consumer).
- **Отладка:** `show-il`, `disasm`, `show-ir`, `list`.
- **Очистка:** `clean`, `clean-sdk`, `clean-sources`.

## Инкрементальность

`just` не делает mtime-инкрементальности нативно. Решение:

- **Тяжёлые шаги** (Gradle `:compiler-plugin:jar`, MSBuild `dotnet run`)
  инкрементальны сами по себе (`UP-TO-DATE`).
- **Лёгкие шаги** (`kotlinc`, `ilasm`) — mtime-проверки в bash-рецептах:
  ```bash
  if [ -f "$out" ] && [ "$out" -nt "$kt" ] && [ "$out" -nt "$jar" ]; then
      echo "[just] $out up-to-date"
      exit 0
  fi
  ```
  Пропускают пересборку если исходник и plugin JAR не менялись.

Альтернативы (на будущее, если станет тесно):
- Вынести файловые таргеты в `Makefile`, `just` как диспетчер.
- `scripts/needs-rebuild.sh target dep1 dep2...` хелпер.

## Что осталось в `scripts/`

- `install-sdks.sh`, `install-sources.sh` — вызываются из `just sdks`/`sources`.
- `activate.sh`, `deactivate.sh` — для интерактивной работы в шелле и
  для использования внутри `just`-рецептов (`source scripts/activate.sh`).

## Что удалено

- `scripts/bootstrap.sh` — заменён на `just bootstrap`.
- `scripts/build-test-add.sh` — заменён на `just test`.

## Последствия

- `just bootstrap && just test` — стандартный путь нового разработчика.
- `just --list` — документация рецептов.
- `scripts/` содержит только "библиотеку" для `just` и интерактивной работы.
- Gradle остаётся только для `:compiler-plugin` (вызывается из `just plugin`).
