# E-01: IR — какая платформа генерила IR (tailrec и др.)

- **Тема:** E. IR platform investigation
- **Разметка:** PRE-9 (исследование), POST-9 (фикс, если нужно)
- **Зависимости:** —
- **Статус:** TODO

## Контекст

Пользователь: «Меня смущает, что файл kotlin непонятно для какой
платформы генерил IR. В частности там же была разная обработка
tailrec».

`IrGenerationExtension` получает IR после lowering-проходов
фронтенда. Вопрос: **какой backend** целевой, и какие
backend-specific lowering'и уже применены?

- Kotlin/JS, Kotlin/Native, Kotlin/JVM — разные lowering-проходы.
- `tailrec` — tail-recursion optimization. В JVM-бэкенде
  превращается в loop (`tail.` prefix в имени). В JS/Native — может
  отличаться.
- Если IR пришёл «JVM-lowered», там могут быть JVM-специфичные
  артефакты (erasure, bridge methods, `tail.`-loops).

ADR 0003 зафиксировал «сценарий A» (типы сохранены) для `test_add`.
Но это не покрывает `tailrec` и другие lowering-проходы.

## Цель

1. Понять, **какой** IR мы получаем: целевая платформа, применённые
   lowering-проходы.
2. Зафиксировать, какие артефакты нам не нужны (JVM-specific) и как
   их распознать/обойти.
3. Проверить `tailrec` — приходит ли он как loop или как рекурсия.

## Задачи

1. **Исследование кода Kotlin-компилятора** (в `.sources/kotlin/`):
   - Как `IrGenerationExtension` вызывается относительно lowering-проходов?
   - Поиск `tailrec` в `compiler/ir/`: `LoweringKt`, `TailRecursionLowering`.
   - Проверить, есть ли `IrGenerationExtension` до или после
     `tailrec` lowering. Скорее всего — **до** (extension вызывается
     в `BackendWrappable`/`Phase`-pipeline, tailrec — часть
    Jvm/Native backend lowering).
   - Проверить `IrWhileLoop` с `origin=WHILE_LOOP` или подобным —
     если tailrec уже lowering'нут в loop, в IR будет `IrWhileLoop`.
2. **Эксперимент:**
   ```kotlin
   // test-projects/04-loops/Tailrec.kt (создать)
   tailrec fun sum(n: Int, acc: Int = 0): Int =
       if (n == 0) acc else sum(n - 1, acc + n)
   ```
   - Скомпилировать `just _gen-il Tailrec ...`.
   - Дамп IR (`build/ir-dump-*.txt` после A-02).
   - Посмотреть: `IrCall` (рекурсия) или `IrWhileLoop` (tailrec-loop)?
3. **Зафиксировать в ADR** (обновить 0003 или создать 0009):
   - Целевая платформа IR: **Kotlin/JVM? Kotlin/Common?** — проверить.
   - tailrec: рекурсия (не lowering) / loop.
   - Какие JVM-specific passes применены до нас (если есть).
4. **Если tailrec пришёл как loop** — visitor должен обработать
   `IrWhileLoop` (Phase 9), отдельного кейса для tailrec не нужно.
   - Если как рекурсия — обычный `call` (Phase 9), но это stack-overflow
     risk для deep recursion; зафиксировать TODO.
5. **Если IR содержит JVM-артефакты** (например, bridge methods,
   erased generics в неожиданных местах) — зафиксировать, какие, и
   как с ними бороться (ignore / remap).

## Приёмка

- ADR (0003 обновлён или 0009 создан) описывает:
  - Целевую «платформу» IR (что нижнет в `IrModuleFragment`).
  - tailrec: как приходит.
  - Список применённых lowering-проходов (хотя бы верхнеуровнево).
- Эксперимент с `tailrec` — в `test-projects/04-loops/` (можно
  создать как часть Phase 9 подготовки, но тест не обязан проходить —
  только IR-дамп для исследования).
- Если найдены JVM-артефакты — отдельная задача (E-02, создать TODO
  при необходимости).

## Заметки исполнителю

- Это **исследование**, не код. Результат — ADR +, возможно, TODO-задачи.
- Источники: `.sources/kotlin/compiler/ir/`, дамп IR из
  `test-projects/04-loops/`.
- Не пытаться «исправить» IR — только понять, что к нам приходит.
