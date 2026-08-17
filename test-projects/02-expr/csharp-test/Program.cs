using org.example.expr;

// test_add
System.Console.WriteLine($"test_add(2, 3) = {ExprKt.test_add(2, 3)}");          // 5
// test_sub
System.Console.WriteLine($"test_sub(10, 4) = {ExprKt.test_sub(10, 4)}");        // 6
// test_mul
System.Console.WriteLine($"test_mul(3, 4) = {ExprKt.test_mul(3, 4)}");          // 12
// test_div
System.Console.WriteLine($"test_div(20, 5) = {ExprKt.test_div(20, 5)}");        // 4
// test_rem
System.Console.WriteLine($"test_rem(17, 5) = {ExprKt.test_rem(17, 5)}");        // 2
// test_neg
System.Console.WriteLine($"test_neg(7) = {ExprKt.test_neg(7)}");                // -7
// test_locals
System.Console.WriteLine($"test_locals(3, 4) = {ExprKt.test_locals(3, 4)}");    // (3+4)*2 = 14
// test_if
System.Console.WriteLine($"test_if(10) = {ExprKt.test_if(10)}");               // 10*2 = 20
System.Console.WriteLine($"test_if(3) = {ExprKt.test_if(3)}");                 // 3-1 = 2
// test_when
System.Console.WriteLine($"test_when(-5) = {ExprKt.test_when(-5)}");           // 5
System.Console.WriteLine($"test_when(0) = {ExprKt.test_when(0)}");             // 1
System.Console.WriteLine($"test_when(7) = {ExprKt.test_when(7)}");             // 7
// test_compare
System.Console.WriteLine($"test_compare(10, 5) = {ExprKt.test_compare(10, 5)}");   // True
System.Console.WriteLine($"test_compare(5, 10) = {ExprKt.test_compare(5, 10)}");   // False
// test_bool
System.Console.WriteLine($"test_bool(true, false) = {ExprKt.test_bool(true, false)}");   // (T && F) || !T = F || F = False
System.Console.WriteLine($"test_bool(false, true) = {ExprKt.test_bool(false, true)}");   // (F && T) || !F = F || T = True
// test_long
System.Console.WriteLine($"test_long(100, 200) = {ExprKt.test_long(100, 200)}");  // 300
// test_double
System.Console.WriteLine($"test_double(1.5, 2.5) = {ExprKt.test_double(1.5, 2.5)}");  // 4.0
// test_const
System.Console.WriteLine($"test_const() = {ExprKt.test_const()}");             // 42
