#!/usr/bin/env bash

set -u

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$repo_root"

readonly EXPECTED_OS="Ubuntu 24.04"
readonly MIN_JAVA_MAJOR=17
readonly SDK_PLATFORM_DIR="platforms/android-35"
readonly BUILD_TOOLS_DIR="build-tools/35.0.0"
readonly RED=$'\033[31m'
readonly GREEN=$'\033[32m'
readonly YELLOW=$'\033[33m'
readonly BOLD=$'\033[1m'
readonly RESET=$'\033[0m'

failures=0
warnings=0

ok() {
    printf '%s[ OK ]%s %s\n' "$GREEN" "$RESET" "$1"
}

warn() {
    printf '%s[WARN]%s %s\n' "$YELLOW" "$RESET" "$1"
    warnings=$((warnings + 1))
}

fail() {
    printf '%s[FAIL]%s %s\n' "$RED" "$RESET" "$1"
    failures=$((failures + 1))
}

section() {
    printf '\n%s%s%s\n' "$BOLD" "$1" "$RESET"
}

section "页声 Ubuntu 开发环境诊断"

if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    if [ "${ID:-}" = "ubuntu" ] && [ "${VERSION_ID:-}" = "24.04" ]; then
        ok "系统：${PRETTY_NAME:-$EXPECTED_OS}"
    else
        warn "当前系统为 ${PRETTY_NAME:-unknown}；项目以 $EXPECTED_OS 为基线"
    fi
else
    fail "无法读取 /etc/os-release"
fi

if [ "$(uname -m)" = "x86_64" ] || [ "$(uname -m)" = "aarch64" ]; then
    ok "架构：$(uname -m)"
else
    warn "未测试的 CPU 架构：$(uname -m)"
fi

section "Java"

java_command=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java_command="$JAVA_HOME/bin/java"
    ok "JAVA_HOME：$JAVA_HOME"
elif command -v java >/dev/null 2>&1; then
    java_command="$(command -v java)"
    warn "JAVA_HOME 未设置；将使用 PATH 中的 $java_command"
else
    fail "未找到 Java。安装 OpenJDK 17/21，或在 Android Studio 中配置 Gradle JDK"
fi

if [ -n "$java_command" ]; then
    version_output="$("$java_command" -version 2>&1)"
    version_line="$(printf '%s\n' "$version_output" | head -n 1)"
    major="$(
        printf '%s\n' "$version_line" |
            sed -E 's/.*version "([0-9]+).*/\1/' |
            head -n 1
    )"
    if [ "$major" = "1" ]; then
        major="$(
            printf '%s\n' "$version_line" |
                sed -E 's/.*version "1\.([0-9]+).*/\1/' |
                head -n 1
        )"
    fi
    if printf '%s' "$major" | grep -Eq '^[0-9]+$' && [ "$major" -ge "$MIN_JAVA_MAJOR" ]; then
        ok "$version_line"
    else
        fail "Java 主版本至少需要 $MIN_JAVA_MAJOR，当前：$version_line"
    fi
fi

section "Android SDK"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_root" ] && [ -r local.properties ]; then
    sdk_root="$(sed -n 's/^sdk[.]dir=//p' local.properties | head -n 1)"
fi
if [ -z "$sdk_root" ] && [ -d "$HOME/Android/Sdk" ]; then
    sdk_root="$HOME/Android/Sdk"
fi

if [ -n "$sdk_root" ] && [ -d "$sdk_root" ]; then
    ok "SDK 根目录：$sdk_root"
else
    fail "未找到 Android SDK。在 Android Studio SDK Manager 中安装 Android 15 (API 35)"
fi

if [ -n "$sdk_root" ] && [ -d "$sdk_root/$SDK_PLATFORM_DIR" ]; then
    ok "SDK Platform：API 35"
else
    fail "缺少 SDK Platform 35"
fi

if [ -n "$sdk_root" ] && [ -d "$sdk_root/$BUILD_TOOLS_DIR" ]; then
    ok "Build Tools：35.0.0"
else
    warn "缺少 Build Tools 35.0.0；Gradle 通常可自动选择兼容版本"
fi

if [ -n "$sdk_root" ] && [ -x "$sdk_root/platform-tools/adb" ]; then
    ok "ADB：$sdk_root/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    ok "ADB：$(command -v adb)"
else
    warn "ADB 未安装；只做编译时不影响，真机调试前需要安装"
fi

if [ -r local.properties ]; then
    configured_sdk="$(sed -n 's/^sdk[.]dir=//p' local.properties | head -n 1)"
    if [ -n "$configured_sdk" ]; then
        ok "local.properties：$configured_sdk"
    else
        warn "local.properties 存在但没有 sdk.dir"
    fi
else
    warn "没有 local.properties；可运行 scripts/set-local-sdk.sh"
fi

section "仓库与 Gradle"

if [ -r gradle/wrapper/gradle-wrapper.jar ]; then
    ok "Gradle Wrapper JAR 存在"
else
    fail "缺少 gradle/wrapper/gradle-wrapper.jar"
fi

if [ -x gradlew ]; then
    ok "gradlew 可执行"
else
    warn "gradlew 尚无执行位；运行 chmod +x gradlew，或使用 make 命令"
fi

if command -v git >/dev/null 2>&1; then
    ok "Git：$(git --version)"
    if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        current_branch="$(git branch --show-current 2>/dev/null || true)"
        ok "Git 分支：${current_branch:-detached}"
    fi
else
    warn "Git 未安装"
fi

section "模拟器与真机"

if [ -e /dev/kvm ]; then
    if [ -r /dev/kvm ] && [ -w /dev/kvm ]; then
        ok "/dev/kvm 可读写，可运行 x86_64 模拟器"
    else
        warn "/dev/kvm 存在但当前用户不可读写；将用户加入 kvm 组后重新登录"
    fi
else
    warn "没有 /dev/kvm；x86_64 模拟器会极慢或无法启动"
fi

if [ -r /etc/udev/rules.d/51-android.rules ]; then
    ok "已发现 Android udev 规则"
else
    warn "未发现 51-android.rules；如 adb 看不到手机，请按 Ubuntu 开发指南配置"
fi

section "资源"

if command -v nproc >/dev/null 2>&1; then
    ok "CPU 逻辑核心：$(nproc)"
fi
if command -v free >/dev/null 2>&1; then
    memory_gib="$(free -g | awk '/^Mem:/ {print $2}')"
    if [ -n "$memory_gib" ] && [ "$memory_gib" -lt 8 ]; then
        warn "内存 ${memory_gib} GiB；建议至少 8 GiB，模拟器开发建议 16 GiB"
    else
        ok "内存：${memory_gib:-unknown} GiB"
    fi
fi

available_kib="$(df -Pk . | awk 'NR == 2 {print $4}')"
if [ -n "$available_kib" ] && [ "$available_kib" -lt 10485760 ]; then
    warn "仓库分区剩余不足 10 GiB；Android SDK、Gradle 与模拟器会占用较多空间"
else
    ok "仓库分区剩余：$((available_kib / 1024 / 1024)) GiB"
fi

printf '\n%s结果：%s 个阻断项，%s 个警告%s\n' \
    "$BOLD" "$failures" "$warnings" "$RESET"

if [ "$failures" -gt 0 ]; then
    printf '先处理 FAIL 项，再执行完整构建。\n'
    exit 1
fi

printf '环境检查通过。下一次运行：make check && make build\n'
exit 0
