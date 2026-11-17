#!/usr/bin/env bash

set -euo pipefail

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    printf 'Usage: bash scripts/set-local-sdk.sh [/path/to/Android/Sdk]\n'
    printf 'Default: $ANDROID_HOME, $ANDROID_SDK_ROOT, then $HOME/Android/Sdk\n'
    exit 0
fi

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$repo_root"

sdk_root="${1:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}}"
sdk_root="${sdk_root%/}"

if [ ! -d "$sdk_root" ]; then
    printf 'Android SDK directory not found: %s\n' "$sdk_root" >&2
    printf 'Usage: bash scripts/set-local-sdk.sh /path/to/Android/Sdk\n' >&2
    exit 1
fi

if [ ! -d "$sdk_root/platforms/android-35" ]; then
    printf 'Warning: %s does not contain platforms/android-35.\n' "$sdk_root" >&2
    printf 'Install it with Android Studio SDK Manager or sdkmanager.\n' >&2
fi

case "$sdk_root" in
    *$'\n'*)
        printf 'SDK path must not contain a newline.\n' >&2
        exit 1
        ;;
esac

printf 'sdk.dir=%s\n' "$sdk_root" > local.properties
printf 'Wrote local.properties -> %s\n' "$sdk_root"
printf 'This file is machine-local and ignored by Git.\n'
