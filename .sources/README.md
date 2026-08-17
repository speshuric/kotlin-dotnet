# .sources/ — локальные исходники для референса

Этот каталог **gitignored** (кроме этого README). Сюда устанавливаются
shallow-клоны исходников для чтения и грепа во время разработки.

## Что тут лежит

| Каталог | Источник | Версия |
|---|---|---|
| `kotlin/` | https://github.com/JetBrains/kotlin | тег `v2.4.20-RC` |
| `dotnet-runtime/` | https://github.com/dotnet/runtime | ветка `release/10.0` |

## Установка

```bash
source scripts/activate.sh
scripts/install-sources.sh
```

## Обновление

```bash
git -C .sources/kotlin pull
git -C .sources/dotnet-runtime pull
```

## Зачем нужно

- `.sources/kotlin/compiler/ir/` — IR-дерево, бэкенды (jvm, js, wasm) как референс.
- `.sources/dotnet-runtime/src/libraries/` — BCL исходники для runtime-маппинга.
