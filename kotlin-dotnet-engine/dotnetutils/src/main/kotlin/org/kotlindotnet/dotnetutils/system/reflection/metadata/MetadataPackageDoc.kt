/**
 * org.kotlindotnet.dotnetutils.system.reflection.metadata
 *
 * Port of the System.Reflection.Metadata write path (MetadataBuilder and its
 * surroundings; see .sources/dotnet-runtime/src/libraries/System.Reflection.Metadata)
 * to Kotlin.
 *
 * Goal: build .NET assemblies (PE DLL/EXE) directly from JVM code, without ilasm.
 * API names follow the original (the namespace system.reflection.metadata reflects
 * the dotnet namespace); naming conventions are Kotlin:
 * classes PascalCase, methods/properties lowerCamelCase, constants UPPER_SNAKE.
 *
 * Limitations (adr/0009):
 *  - pure kotlin-stdlib only; direct java.* imports are forbidden;
 *  - cut out: Edit-and-Continue, WinMD, Portable PDB / embedded PDB;
 *  - verification: unit tests plus a C# harness against real SRM, then
 *    a minimal MetadataReader in Kotlin.
 */
package org.kotlindotnet.dotnetutils.system.reflection.metadata
