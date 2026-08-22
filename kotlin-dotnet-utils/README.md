# kotlin-dotnet-utils — .NET-сторона проекта

Утилиты на C#/.NET, обслуживающие kotlin-dotnet. Всё, что исполняется
на стороне .NET (кроме `runtime/KotlinDotnetRuntime` — рантайма,
в который компилируется Kotlin-код), живёт здесь.

## verifier

C#-harness «ground truth»: открывает собранные нашим пайплайном
PE/DLL/EXE настоящим `System.Reflection.Metadata` (PEReader +
MetadataReader), печатает заголовки, таблицы метаданных и IL и
завершается ошибкой при невозможности разобрать образ.

Используется:
- тестом `05-pe-hello` (`scripts/build-test.sh 05-pe-hello`,
  см. также `just test 05-pe-hello`) — верификация EXE, собранного
  чисто Kotlin-кодом модуля dotnetutils;
- вручную для диагностики любого артефакта:

```bash
source scripts/activate.sh
cd kotlin-dotnet-utils/verifier
dotnet run --no-launch-profile -- <path-to-assembly>
```

При успехе печатает `VERIFIER OK`.
