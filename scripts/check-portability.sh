#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$repo_root"

failures=0

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    failures=$((failures + 1))
}

ok() {
    printf '[ OK ] %s\n' "$1"
}

for script in gradlew scripts/*.sh; do
    if bash -n "$script"; then
        ok "Shell syntax: $script"
    else
        fail "Shell syntax: $script"
    fi
done

while IFS= read -r -d '' file; do
    if LC_ALL=C grep -q $'\r' "$file"; then
        fail "CRLF in portable text file: $file"
    fi
done < <(
    find . \
        -path './.git' -prune -o \
        -path './.gradle' -prune -o \
        -path '*/build' -prune -o \
        -type f \
        \( -name '*.kt' -o -name '*.kts' -o -name '*.sh' \
        -o -name '*.md' -o -name '*.xml' -o -name '*.yml' \
        -o -name '*.yaml' -o -name '*.toml' -o -name 'Makefile' \
        -o -name 'gradlew' -o -name '.gitattributes' -o -name '.editorconfig' \) \
        -print0
)

if [ -r gradle/wrapper/gradle-wrapper.jar ]; then
    ok "Gradle wrapper JAR present"
else
    fail "Gradle wrapper JAR missing"
fi

if [ -r .github/workflows/android-ci.yml ]; then
    ok "Ubuntu CI workflow present"
else
    fail "Ubuntu CI workflow missing"
fi

if [ "$failures" -gt 0 ]; then
    printf '\nPortability checks failed: %s\n' "$failures" >&2
    exit 1
fi

printf '\nPortability checks passed.\n'
