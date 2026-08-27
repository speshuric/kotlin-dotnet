# scripts/tests.sh — test registry: **1 test = 1 folder** in test-projects/.
#
# The test folder / properties-file format is docs/test-format.md (required
# reading before adding or changing tests).
#
# Not executed directly; sourced via
#   source "$PROJECT_ROOT/scripts/tests.sh"
#
# Expects common.sh to be sourced already (PROJECT_ROOT, log_*) and the
# current directory to be the project root (as the build/test/show scripts do).
#
# Test names are never hardcoded: a test = a subfolder of test-projects/
# containing test.properties. A folder without test.properties is not a test
# (it may hold service/auxiliary material).

TESTS_ROOT="test-projects"
PROPS_NAME="test.properties"

# test_ids — print the ids of all tests (one per line).
test_ids() {
    local d
    for d in "$TESTS_ROOT"/*/; do
        [ -n "$d" ] || continue
        [ -f "${d}${PROPS_NAME}" ] || continue # no properties — not a test
        basename "$d"
    done
}

# TEST_IDS — space-separated ids (for for-loops and error messages).
TEST_IDS="$(test_ids | paste -sd' ' -)"

# test_exists <id> — exit 0 if the test folder with test.properties exists.
test_exists() {
    [ -f "$TESTS_ROOT/$1/$PROPS_NAME" ]
}

# test_prop <id> <key> [default] — value of a key from test.properties.
#
# Rules: first `key=value` line; whitespace around the value is trimmed;
# an empty value is equivalent to a missing key; '\n' inside the value
# is expanded by the calling code via printf '%b'.
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

# --- Attribute accessors ---

# test_kind <id> — "exe" | "dll".
test_kind() {
    test_prop "$1" kind
}

# test_backends <id> — list of backends. The only permitted
# value is "pe" (the IL-text/ilasm path was removed, ADR 0012).
test_backends() {
    test_prop "$1" backends "pe"
}

# test_desc <id> — one-line description.
test_desc() {
    test_prop "$1" desc
}

# test_type <id> — "kotlin" (default) | "gradle-image".
test_type() {
    test_prop "$1" type "kotlin"
}

# test_consumer <id> — full path to the C# consumer, or empty.
test_consumer() {
    local rel
    rel="$(test_prop "$1" consumer "")" || { echo ""; return 0; }
    if [ -n "$rel" ]; then
        echo "$TESTS_ROOT/$1/$rel"
    else
        echo ""
    fi
}

# test_kt <id> — expanded source paths (one per line),
# relative to the project root. Error if the glob matches nothing.
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

# test_sources_glob <id> — raw source glob from the properties.
test_sources_glob() {
    test_prop "$1" sources "*.kt"
}

# test_dump_grep <id> — pattern that must appear in the ir-dump of
# the built artifact (grep -E, checked per source).
# Empty — check is skipped. Serves as acceptance for the IR-reading features
# (e.g. recognized-annotations sections) without changing program output.
test_dump_grep() {
    test_prop "$1" dump-grep ""
}

# test_own_stdlib <id> — "true" if the test is compiled against our own
# stdlib-jar (-no-stdlib -cp <our>.jar) instead of the stock one.
test_own_stdlib() {
    test_prop "$1" own-stdlib ""
}

# tests_list — print "id<TAB>kind<TAB>description".
tests_list() {
    local id k
    # shellcheck disable=SC2086  # intentional word-splitting
    for id in $TEST_IDS; do
        k="$(test_kind "$id" 2>/dev/null)" || k="$(test_type "$id")"
        printf '%s\t%s\t%s\n' "$id" "$k" "$(test_desc "$id")"
    done
}

# resolve_selector <arg> — expand a selector into a list of ids.
#   all       → all ids
#   last      → the id with the freshest .il under build/<id>/ (or the first one if none)
#   <glob>    → ids matching the glob (e.g. 04*)
#   <exact>   → that id itself (if valid)
# No match → exit 1 with an error.
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
            # Exact match?
            if test_exists "$arg"; then
                echo "$arg"
                return 0
            fi
            # Glob match over ids?
            local matched="" id
            # shellcheck disable=SC2086  # intentional word-splitting
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

# _resolve_last — the id with the freshest ir-dump-* under build/<id>/*.
# If no test has a build directory with an ir-dump, return the first id.
_resolve_last() {
    local best_id first_id best_mtime=0 id dir dump m
    # shellcheck disable=SC2086  # intentional word-splitting
    first_id=$(
        for id in $TEST_IDS; do echo "$id"; break; done
    )
    best_id="$first_id"
    # shellcheck disable=SC2086  # intentional word-splitting
    for id in $TEST_IDS; do
        dir="build/$id"
        [ -d "$dir" ] || continue
        # maxdepth 2: covers build/<id>/ir-dump-*.txt and per-source subfolders
        while IFS= read -r dump; do
            if [ -f "$dump" ]; then
                m=$(stat -c %Y "$dump" 2>/dev/null || stat -f %m "$dump" 2>/dev/null || echo 0)
                if [ "$m" -gt "$best_mtime" ]; then
                    best_mtime="$m"
                    best_id="$id"
                fi
            fi
        done < <(find "$dir" -maxdepth 2 -name 'ir-dump-*.txt' 2>/dev/null)
    done
    echo "$best_id"
}
