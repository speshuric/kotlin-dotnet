# Паритет структур: System.Reflection.Metadata ↔ dotnetutils

Аудит соответствия между C#-типами апстрима
(`dotnet/runtime release/10.0`, `System.Reflection.Metadata`) и их
портами в `kotlin-dotnet-engine/dotnetutils`.

Зачем: при компиляции самого модуля в .NET планируется инструментальная
сверка нашего API с оригиналом. Конвенция «upstream struct → data/value
class» делает сверку механической: `data class` и `value class` будут
конвертированы в .NET-структуры, а сравнение по полям уже соответствует
семантике C#-структур.

## Правила конвенции

| Апстрим | Порт | Комментарий |
|---|---|---|
| `public/internal readonly struct` (иммутабельный value-holder) | `data class` | equals/hashCode по полям ≈ семантика struct |
| `readonly struct` с единственным полем (хэндлы, `SignatureHeader`) | `value class` | equals по значению встроен; для будущей конверсии в .NET — тоже struct |
| `class` (stateful-сервис, билдер, энкодер-курсор) | обычный `class` | поведение, не значение |
| `enum` | `enum class` | |
| `static class` | `object` | |

Исключения из правила «struct → data/value class» перечислены в §3 —
каждое с причиной.

## 1. Портированные структуры — реестр

### 1.1 Хэндлы (31 шт.) — `value class`

`AssemblyDefinitionHandle`, `AssemblyFileHandle`,
`AssemblyReferenceHandle`, `BlobHandle`, `ConstantHandle`,
`CustomAttributeHandle`, `DeclarativeSecurityAttributeHandle`,
`EntityHandle`, `EventDefinitionHandle`, `ExportedTypeHandle`,
`FieldDefinitionHandle`, `GenericParameterConstraintHandle`,
`GenericParameterHandle`, `GuidHandle`, `InterfaceImplementationHandle`,
`LabelHandle`, `ManifestResourceHandle`, `MemberReferenceHandle`,
`MethodDefinitionHandle`, `MethodImplementationHandle`,
`MethodSpecificationHandle`, `ModuleDefinitionHandle`,
`ModuleReferenceHandle`, `ParameterHandle`, `PropertyDefinitionHandle`,
`StandaloneSignatureHandle`, `StringHandle`, `TypeDefinitionHandle`,
`TypeReferenceHandle`, `TypeSpecificationHandle`, `UserStringHandle`.

Вердикт: ✅ паритет (equals по значению встроен в `value class`).
Плюс `SignatureHeader` — тоже `value class` ✅.

### 1.2 Строки таблиц метаданных (34 шт.) — `internal data class`

Все классы из `MetadataRows.kt`: `ModuleRow`, `AssemblyRefTableRow`,
`AssemblyRow`, `ClassLayoutRow`, `ConstantRow`, `CustomAttributeRow`,
`DeclSecurityRow`, `EventRow`, `EventMapRow`, `ExportedTypeRow`,
`FieldLayoutRow`, `FieldMarshalRow`, `FieldRvaRow`, `FieldDefRow`,
`FileTableRow`, `GenericParamConstraintRow`, `GenericParamRow`,
`ImplMapRow`, `InterfaceImplRow`, `ManifestResourceRow`, `MemberRefRow`,
`MethodImplRow`, `MethodSemanticsRow`, `MethodSpecRow`, `MethodRow`,
`ModuleRefRow`, `NestedClassRow`, `ParamRow`, `PropertyMapRow`,
`PropertyRow`, `TypeDefRow`, `TypeRefRow`, `TypeSpecRow`,
`StandaloneSigRow`. Вердикт: ✅ (исправлено в этом аудите — ранее были
обычные классы при задекларированных data classes).

### 1.3 Прочие value-holders — `data class`

| Тип | Апстрим | Вердикт |
|---|---|---|
| `Blob` | `public readonly struct Blob` | ✅ (ручные equals/hashCode заменены генерированными; семантика идентична — поле массива сравнивается по ссылке, как в C#) |
| `ReservedBlob<T>` | `public readonly struct ReservedBlob<T>` | ✅ |
| `MethodBodyStreamEncoder.MethodBody` | `public readonly struct MethodBody` | ✅ |
| `ControlFlowBuilder.BranchInfo` | private struct | ✅ (computed-свойства допустимы внутри data class) |
| `ControlFlowBuilder.ExceptionHandlerInfo` | private struct | ✅ |
| `BlobContentId` | `public readonly struct BlobContentId` | ✅ |
| `ExceptionRegion` | `public readonly struct ExceptionRegion` (reader-side) | ✅ |
| `AssemblyVersion` | **`System.Version` — это class**, не struct | ⚠️ осознанное отклонение: иммутабельный data class покрывает нужные поля major/minor/build/revision |

### 1.4 Собственные типы без апстримного аналога

`AssemblyInfo`, `AssemblyRefInfo`, `ModuleInfo`, `ExceptionRegionInfo`,
`Section`, `DirectoryEntry`, `CoffHeader`, `CorHeader`, `PEHeader`
(reader-side I8 / порт `System.Reflection.PortableExecutable`) — все
`data class`; заголовки в апстриме являются классами, но у нас это
иммутабельные value-holders без поведения (отклонение безопасно).

## 2. Классы, совпадающие с апстримом (не структуры)

`MetadataBuilder`, `MetadataRootBuilder`, `MetadataSizes`,
`SerializedMetadata` (в апстриме `internal sealed class`),
`ControlFlowBuilder`, `InstructionEncoder`, `SwitchInstructionEncoder`,
`MethodBodyStreamEncoder`, `ManagedTextSection`, `PEReader` (lite),
`PEHeaderBuilder`, `PEDirectoriesBuilder`, `MetadataReader` (lite),
`BlobDictionary` (апстрим `internal sealed class`). Вердикт: ✅ паритет.

## 3. Исключения: апстримные структуры, портированные классами

Категория «поведение, не значение» — данные-поля есть, но смысл типа —
фасад над изменяемым состоянием; равенство по полям бессмысленно.

| Наш класс | Апстрим | Причина исключения |
|---|---|---|
| `BlobReader`, `BlobWriter` | readonly struct | курсоры с мутабельной позицией |
| `*Encoder` (23 шт.: `ScalarEncoder`, `VectorEncoder`, `ParametersEncoder`, `ParameterTypeEncoder`, `ReturnTypeEncoder`, `SignatureTypeEncoder`, `CustomModifiersEncoder`, `LiteralsEncoder`, `LiteralEncoder`, `NamedArgumentsEncoder`, `NamedArgumentTypeEncoder`, `FixedArgumentsEncoder`, `PermissionSetEncoder`, `CustomAttributeArrayTypeEncoder`, `CustomAttributeElementTypeEncoder`, `FieldTypeEncoder`, `MethodSignatureEncoder`, `LocalVariablesEncoder`, `LocalVariableTypeEncoder`, `ArrayShapeEncoder`, `CustomAttributeNamedArgumentsEncoder`, `BlobEncoder`, `CustomAttributeNamedArgumentsEncoder`) | readonly struct | фасады над `BlobBuilder`: методы-энкодеры, состояние = builder+offset; никогда не сравниваются |
| `BlobDictionary.Entry` | private struct | содержит `ByteArray` — генерируемый equals был бы ссылочным по массиву (как в C#), но сравнение Entry нигде не нужно |

## 4. Методика проверки

```bash
# апстримные структуры
grep -rhoE '(public|internal|private)?\s*(readonly )?struct [A-Za-z_]*' \
  $SRM_SRC | sort -u

# наши декларации
grep -rnE '^(internal |public )?(data |value |sealed )?class [A-Z]' \
  kotlin-dotnet-engine/dotnetutils/src/main/kotlin/
```

Сверка — по таблицам выше. Новые типы при расширении порта добавляются
в этот документ (разделы 1–3) в том же коммите.
