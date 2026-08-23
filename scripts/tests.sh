# scripts/tests.sh — реестр тестов: **1 тест = 1 папка** в test-projects/.
#
# Формат папки теста и файла свойств — docs/test-format.md (обязателен к
# прочтению при добавлении/изменении тестов).
#
# НЕ исполняется напрямую; подключается через
#   source "$PROJECT_ROOT/scripts/tests.sh"
#
# Предполагает, что common.sh уже подключен (PROJECT_ROOT, log_*) и что
# текущая директория — корень проекта (так делают build/test/show-скрипты).
#
# Имена тестов нигде не хардкодятся: тест = подпапка test-projects/,
# содержащая test.properties. Папка без test.properties тестом не считается
# (может хранить служебные/вспомогательные материалы).

TESTS_ROOT="test-projects"
PROPS_NAME="test.properties"

# test_ids — напечатать id всех тестов (по одному на строку).
test_ids() {
    local d
    for d in "$TESTS_ROOT"/*/; do
        [ -n "$d" ] || continue
        [ -f "${d}${PROPS_NAME}" ] || continue # без свойств — не тест
        basename "$d"
    done
}

# TEST_IDS — id через пробел (для for-циклов и сообщений об ошибках).
TEST_IDS="$(test_ids | paste -sd' ' -)"

# test_exists <id> — exit 0 если папка теста с test.properties существует.
test_exists() {
    [ -f "$TESTS_ROOT/$1/$PROPS_NAME" ]
}

# test_prop <id> <key> [default] — значение ключа из test.properties.
#
# Правила: первая строка `key=value`; пробелы по краям значения обрезаются;
# пустое значение эквивалентно отсутствующему ключу; '\n' внутри значения
# раскрывается вызывающим кодом через printf '%b'.
test_prop() {
    local id="$1" key="$2" def="${3-}"
    local f="$TESTS_ROOT/$id/$PROPS_NAME" v
    if ! test_exists "$id"; then
        log_error "test_prop: $f not found (см. docs/test-format.md)" >&2
        return 1
    fi
    v="$(sed -n "s/^${key}=//p" "$f" | head -n 1)"
    # trim leading/trailing whitespace
    v="${v#"${v%%[![:space:]]*}"}"
    v="${v%"${v##*[![:space:]]}"}"
    if [ -n "$v" ]; then
        echo "$v"
        return 0
    fi
    if [ $# -ge 3 ]; then
        echo "$def"
        return 0
    fi
    return 1
}

# --- Аксессоры атрибутов ---

# test_kind <id> — "exe" | "dll".
test_kind() {
    test_prop "$1" kind
}

# test_backends <id> — список бэкендов ("il", "pe", "il pe").
test_backends() {
    test_prop "$1" backends "il pe"
}

# test_desc <id> — однострочное описание.
test_desc() {
    test_prop "$1" desc
}

# test_type <id> — "kotlin" (по умолчанию) | "gradle-image".
test_type() {
    test_prop "$1" type "kotlin"
}

# test_consumer <id> — полный путь к C#-consumer'у или пусто.
test_consumer() {
    local rel
    rel="$(test_prop "$1" consumer "")" || { echo ""; return 0; }
    if [ -n "$rel" ]; then
        echo "$TESTS_ROOT/$1/$rel"
    else
        echo ""
    fi
}

# test_kt <id> — раскрытые пути исходников (по одному на строку),
# относительно корня проекта. Ошибка, если glob не совпал ни с чем.
test_kt() {
    local id="$1" d="$TESTS_ROOT/$1" g f any=""
    g="$(test_sources_glob "$id")" || return 1
    shopt -s nullglob
    for f in "$d"/$g; do
        [ -f "$f" ] || continue
        echo "$f"
        any=1
    done
    shopt -u nullglob
    if [ -z "$any" ]; then
        log_error "test_kt: no sources match '$g' in $d" >&2
        return 1
    fi
}

# test_sources_glob <id> — сырой glob исходников из свойств.
test_sources_glob() {
    test_prop "$1" sources "*.kt"
}

# tests_list — напечатать "id<TAB>kind<TAB>description".
tests_list() {
    local id k
    # shellcheck disable=SC2086  # намеренный word-split
    for id in $TEST_IDS; do
        k="$(test_kind "$id" 2>/dev/null)" || k="$(test_type "$id")"
        printf '%s\t%s\t%s\n' "$id" "$k" "$(test_desc "$id")"
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
            local matched="" id
            # shellcheck disable=SC2086  # намеренный word-split
            for id in $TEST_IDS; do
                # shellcheck disable=SC2254
                case "$id" in
                    $arg) matched="$matched $id" ;;
                esac
            done
            if [ -n "$matched" ]; then
                echo "${matched# }"
                return 0
            fi
            log_error "no tests match selector '$arg'"
            return 1
            ;;
    esac
}

# _resolve_last — id с самым свежим .il под build/<id>/*.
# Если ни у кого нет build-директории с .il — вернуть первый id.
_resolve_last() {
    local best_id first_id best_mtime=0 id dir il m
    # shellcheck disable=SC2086  # намеренный word-split
    first_id=$(
        for id in $TEST_IDS; do echo "$id"; break; done
    )
    best_id="$first_id"
    # shellcheck disable=SC2086  # намеренный word-split
    for id in $TEST_IDS; do
        dir="build/$id"
        [ -d "$dir" ] || continue
        # maxdepth 2: ловит build/<id>/*.il и per-source подпапки
        while IFS= read -r il; do
            if [ -f "$il" ]; then
                m=$(stat -c %Y "$il" 2>/dev/null || stat -f %m "$il" 2>/dev/null || echo 0)
                if [ "$m" -gt "$best_mtime" ]; then
                    best_mtime="$m"
                    best_id="$id"
                fi
            fi
        done < <(find "$dir" -maxdepth 2 -name '*.il' 2>/dev/null)
    done
    echo "$best_id"
}
