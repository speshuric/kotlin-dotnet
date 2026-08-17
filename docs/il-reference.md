# IL-текст (CIL assembly language) — краткий справочник

## Структура .il файла

```
.assembly extern mscorlib {}
.assembly extern KotlinDotnetRuntime {}
.assembly MyKotlinApp {}
.module MyKotlinApp.exe

.class public auto ansi MyKotlinApp.Program
       extends [mscorlib]System.Object
{
  .method public hidebysig static void Main(string[] args) cil managed
  {
    .entrypoint
    ldstr "Hello"
    call void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(string)
    ret
  }
  .method public hidebysig specialname rtspecialname
          instance void .ctor() cil managed
  {
    ldarg.0
    call instance void [mscorlib]System.Object::.ctor()
    ret
  }
}
```

## Ключевые IL-инструкции (шпаргалка)

- Загрузка: ldstr, ldc.i4, ldc.i8, ldc.r4, ldc.r8,
  ldloc, ldarg, ldnull, ldc.i4.0/1
- Сохранение: stloc, starg
- Арифметика: add, sub, mul, div, rem, neg
- Сравнение: clt, cgt, ceq, clt.un, cgt.un
- Ветвление: br, brtrue, brfalse, beq, bne, blt, bgt,
  ble, bge, switch
- Вызовы: call, callvirt, ret
- Объекты: newobj, ldfld, stfld, ldsfld, stsfld,
  isinst, castclass, box, unbox
- Массивы: newarr, ldelem, stelem, ldlen
- Исключения: throw, rethrow, leave, endfilter
- Дженерики: !0, !1 (type params), !!0 (method type params),
  box, unbox.any, constrained.
