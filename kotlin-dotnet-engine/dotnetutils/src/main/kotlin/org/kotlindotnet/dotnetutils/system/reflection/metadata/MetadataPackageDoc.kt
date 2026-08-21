/**
 * org.kotlindotnet.dotnetutils.system.reflection.metadata
 *
 * Порт write-path System.Reflection.Metadata (MetadataBuilder и окружение,
 * см. .sources/dotnet-runtime/src/libraries/System.Reflection.Metadata) на Kotlin.
 *
 * Цель: создание .NET-сборок (PE DLL/EXE) напрямую из JVM-кода, без ilasm.
 * Имена API следуют оригиналу (namespace system.reflection.metadata отражает
 * dotnet-неймспейс), соглашения именования — Kotlin:
 * классы PascalCase, методы/свойства lowerCamelCase, константы UPPER_SNAKE.
 *
 * Ограничения (adr/0009):
 *  - только чистый kotlin-stdlib; прямые import java.* запрещены;
 *  - отсечено: Edit-and-Continue, WinMD, Portable PDB / embedded PDB;
 *  - верификация: юнит-тесты + C#-harness на настоящем SRM, затем
 *    минимальный MetadataReader на Kotlin.
 */
package org.kotlindotnet.dotnetutils.system.reflection.metadata
