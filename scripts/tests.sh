# scripts/tests.sh — source-only реестр тестов (данные о тестах).
#
# НЕ исполняется напрямую; подключается через
#   source "$PROJECT_ROOT/scripts/tests.sh"
#
# Предполагает, что common.sh уже подключен (PROJECT_ROOT, log_*).
#
# Тесты (6):
#   00-int-add    dll  test-projects/00-int-add/Arithmetic.kt → csharp-test (prints 5)
#   02-expr       dll  test-projects/02-expr/Expr.kt          → csharp-test (16 info lines)
#   03-hello      exe  test-projects/03-hello/hello.kt       → prints "Hello, .NET!"
#   04-loops      exe  test-projects/04-loops/Loops.kt        → prints 6 lines (10/10/5/25/42/x = 42)
#   04-loops-spec exe  test-projects/04-loops/spec/*.kt (4)   → each prints "OK"
#   05-pe-hello   exe  (без .kt) PE-образ собирается Gradle-тестом
#                      HelloWorldImageTests модуля dotnetutils (без ilasm),
#                      верифицируется запуском + kotlin-dotnet-utils/verifier

TEST_IDS="00-int-add 02-expr 03-hello 04-loops 04-loops-spec 05-pe-hello"

# test_exists <id> — exit 0 если тест есть, 1 если нет.
test_exists() {
    case "$1" in
        00-int-add|02-expr|03-hello|04-loops|04-loops-spec|05-pe-hello) return 0 ;;
        *) return 1 ;;
    esac
}

# test_kt <id> — путь(и) к .kt файлу(ам). Для 04-loops-spec — glob.
test_kt() {
    case "$1" in
        00-int-add) echo "test-projects/00-int-add/Arithmetic.kt" ;;
        02-expr)    echo "test-projects/02-expr/Expr.kt" ;;
        03-hello)   echo "test-projects/03-hello/hello.kt" ;;
        04-loops)    echo "test-projects/04-loops/Loops.kt" ;;
        04-loops-spec) echo "test-projects/04-loops/spec/*.kt" ;;
        05-pe-hello) echo "" ;;  # без исходника .kt — образ строит Gradle-тест
        *) return 1 ;;
    esac
}

# test_kind <id> — "dll" или "exe".
test_kind() {
    case "$1" in
        00-int-add|02-expr) echo "dll" ;;
        03-hello|04-loops|04-loops-spec|05-pe-hello) echo "exe" ;;
        *) return 1 ;;
    esac
}

# test_consumer <id> — путь к C# consumer-директории для dll-тестов,
# пусто для exe-тестов (относительно PROJECT_ROOT).
test_consumer() {
    case "$1" in
        00-int-add) echo "test-projects/00-int-add/csharp-test" ;;
        02-expr)    echo "test-projects/02-expr/csharp-test" ;;
        *) echo "" ;;
    esac
}

# test_desc <id> — короткое описание.
test_desc() {
    case "$1" in
        00-int-add)    echo "test_add(Int, Int): Int — минимальный pipeline (DLL + C# consumer)" ;;
        02-expr)       echo "16 выражений: арифметика, if/when, сравнения, Long/Double (DLL)" ;;
        03-hello)      echo "fun main() { println(\"Hello, .NET!\") } (EXE + runtime)" ;;
        04-loops)      echo "циклы while/do-while, break/continue, вызовы, интерполяция (EXE)" ;;
        04-loops-spec) echo "4 spec-теста while/do-while из kotlin/tests-spec (EXE, print OK)" ;;
        05-pe-hello)   echo "hello-world EXE, собранный чисто Kotlin'ом dotnetutils (без ilasm)" ;;
        *) return 1 ;;
    esac
}

# tests_list — напечатать "id  kind  description" строки.
tests_list() {
    local id
    for id in $TEST_IDS; do
        printf '%s\t%s\t%s\n' "$id" "$(test_kind "$id")" "$(test_desc "$id")"
    done
}

# resolve_selector <arg> — развернуть селектор в список id.
#   all       → все id
#   last      → id с самым свежим .il под build/<id>/ (или первый, если ничего нет)
#   <glob>    → id, подпадающие под glob (например 04*)
#   <exact>   → сам id (если валиден)
# Не найдено → exit 1 с ошибкой.
resolve_selector() {
    local arg="$1"
    case "$arg" in
        all)
            echo "$TEST_IDS"
            return 0
            ;;
        last)
            _resolve_last
            return $?
            ;;
        *)
            # Точный match?
            if test_exists "$arg"; then
                echo "$arg"
                return 0
            fi
            # Glob match по id?
            local matched=""
            local id
            for id in $TEST_IDS; do
                # shellcheck disable=SC2254
                case "$id" in
                    $arg) matched="$matched $id" ;;
                esac
            done
            if [ -n "$matched" ]; then
                echo "$matched"
                return 0
            fi
            log_error "no tests match selector '$arg'"
            return 1
            ;;
    esac
}

# _resolve_last — id с самым свежим .il под build/<id>/*.
# Если ни у кого нет build-директории с .il — вернуть первый id (00-int-add).
_resolve_last() {
    local best_id="00-int-add"
    local best_mtime=0
    local id
    for id in $TEST_IDS; do
        local dir="build/$id"
        local il
        if [ -d "$dir" ]; then
            # 04-loops-spec хранит .il в поддирах
            while IFS= read -r il; do
                if [ -f "$il" ]; then
                    local m
                    m=$(stat -c %Y "$il" 2>/dev/null || stat -f %m "$il" 2>/dev/null || echo 0)
                    if [ "$m" -gt "$best_mtime" ]; then
                        best_mtime="$m"
                        best_id="$id"
                    fi
                fi
            done < <(find "$dir" -maxdepth 2 -name '*.il' 2>/dev/null)
        fi
    done
    echo "$best_id"
}
