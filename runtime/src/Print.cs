namespace Kotlin.Runtime;

/// <summary>
/// Аналог kotlin.io.println / kotlin.io.print.
/// Компилятор генерирует вызовы к этому классу, а не к System.Console напрямую.
/// </summary>
public static class Print
{
    public static void println() => Console.WriteLine();

    public static void println(object? o) => Console.WriteLine(o);

    public static void println(string s) => Console.WriteLine(s);

    public static void println(int i) => Console.WriteLine(i);

    public static void println(long l) => Console.WriteLine(l);

    public static void println(double d) => Console.WriteLine(d);

    public static void println(float f) => Console.WriteLine(f);

    public static void println(bool b) => Console.WriteLine(b);

    public static void println(char c) => Console.WriteLine(c);

    public static void print(object? o) => Console.Write(o);

    public static void print(string s) => Console.Write(s);

    public static void print(int i) => Console.Write(i);

    public static void print(long l) => Console.Write(l);

    public static void print(double d) => Console.Write(d);

    public static void print(float f) => Console.Write(f);

    public static void print(bool b) => Console.Write(b);

    public static void print(char c) => Console.Write(c);
}
