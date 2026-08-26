# Architecture Decision Records (ADR)

Формат: [Michael Nygard, adr.github.io](https://adr.github.io/).

| N | Дата | Статус | Тема |
|---|------|--------|------|
| 0001 | 2026-08-17 | Proposed | Pipeline: IR → IL-текст → ilasm → .NET-сборка |
| 0002 | 2026-08-17 | Accepted | Runtime-библиотека KotlinDotnetRuntime |
| 0003 | 2026-08-17 | Accepted | Состояние IR на перехвате плагина |
| 0004 | 2026-08-17 | Proposed | Версии: Kotlin 2.4.20-RC, .NET 10 |
| 0005 | 2026-08-17 | Proposed | Мэппинг имён Kotlin ↔ .NET |
| 0006 | 2026-08-17 | Accepted | `just` как единая точка входа для сборки |
| 0007 | 2026-08-17 | Accepted | Output-директория плагина через CLI-опцию `output.dir` |
| 0008 | 2026-08-19 | Accepted | Рефакторинг `justfile` — per-test layout, реестр тестов, dispatch-скрипты |
| 0009 | 2026-08-21 | Accepted | Порт write-path System.Reflection.Metadata на Kotlin (модуль dotnetutils) |
| 0010 | 2026-08-22 | Accepted | Прямая генерация PE в compiler-plugin (замена IL-текста + ilasm) |
| 0011 | 2026-08-22 | Accepted | Минимальный MetadataReader в dotnetutils (round-trip самопроверка) |
| 0012 | 2026-08-24 | Implemented | Удаление IL-текстового пути (ilasm); единственный вывод — PE (реализация: Phase 10.9) |
| 0013 | 2026-08-24 | Placeholder | Итераторы: общий дизайн (заглушка под задачу G-02) |
| 0014 | 2026-08-27 | Accepted | Стратегия stdlib для таргета .NET (гибрид A→B, перенаправления через аннотации на шимах) |
