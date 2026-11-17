# Ubuntu 24.04 开发与调试指南

本指南面向第二台 Ubuntu 24.04 电脑。应用代码没有 Windows 专属依赖，Android Studio、Gradle、ADB 和模拟器都应在**原生 Ubuntu** 中运行。

## 1. 基线

| 项目 | 推荐值 |
| --- | --- |
| 系统 | Ubuntu 24.04 LTS x86_64 |
| Android Studio | 最新稳定版 |
| Gradle JDK | Android Studio 自带 JBR，或 OpenJDK 17/21 |
| Android SDK Platform | API 35 |
| Build Tools | 35.0.0 |
| 最低测试设备 | Android 8.0 / API 26 |
| 目标测试设备 | Android 15 / API 35 |
| 内存 | 8 GiB 可构建；16 GiB 推荐模拟器开发 |
| 磁盘 | 至少预留 20 GiB |

仓库的 Kotlin `jvmTarget` 固定为 17，但 Gradle 可以由 JDK 17 或 JDK 21 运行。不要使用低于 JDK 17 的版本。

## 2. 从 Windows 切到 Ubuntu

优先通过 Git 克隆或 `git pull`，不要只复制工作区中的构建产物：

```bash
git clone https://github.com/toyosatomimi98/practice.git
cd practice
chmod +x gradlew scripts/*.sh
```

仓库包含 `.gitattributes` 和 `.editorconfig`，会把 Kotlin、脚本、Markdown、XML 和 CI 配置规范为 LF。若通过 ZIP 拷贝，仍应执行 `chmod`。

Gradle 缓存、`local.properties`、`build/` 和应用运行数据不会进入仓库，也不需要从 Windows 搬运。Ubuntu 首次构建会重新下载依赖。

## 3. 安装 Android Studio

推荐从 Android 官方下载页安装 Android Studio，不使用发行版中可能过旧的 Android 工具包。

首次启动后进入：

`Settings → Languages & Frameworks → Android SDK`

安装：

- Android SDK Platform 35。
- Android SDK Build-Tools 35.0.0。
- Android SDK Platform-Tools。
- Android Emulator。
- 至少一个 API 35 系统镜像；兼容性测试再增加 API 26 镜像。

然后在 `Settings → Build, Execution, Deployment → Build Tools → Gradle` 中选择 Android Studio 自带 JBR，或 `/usr/lib/jvm/java-17-openjdk-amd64`。

## 4. 纯命令行 SDK 方式

如果只需要构建，不安装 Android Studio：

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk git curl unzip make
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
```

从 Android 官方 `commandlinetools-linux` 下载包解压到：

```text
$HOME/Android/Sdk/cmdline-tools/latest/
```

把工具加入当前 shell：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

安装 SDK：

```bash
sdkmanager --licenses
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

建议将三个 `export` 写入 `~/.bashrc`，重新登录后生效。

## 5. 配置仓库 SDK 路径

`local.properties` 是机器本地文件，已被 Git 忽略。可以自动生成：

```bash
bash scripts/set-local-sdk.sh "$HOME/Android/Sdk"
```

或手工创建：

```properties
sdk.dir=/home/<user>/Android/Sdk
```

不要提交 `local.properties`。

## 6. 首次检查

```bash
bash scripts/check-portability.sh
bash scripts/ubuntu-doctor.sh
```

诊断脚本会检查：

- Ubuntu 版本与 CPU 架构。
- Java 主版本与 `JAVA_HOME`。
- API 35、Build Tools、ADB 和 `local.properties`。
- Gradle Wrapper、Git 与执行位。
- KVM、USB udev 规则、内存和磁盘空间。

阻断项必须处理；ADB、KVM 和 Build Tools 警告在只做编译时可能不影响。

## 7. 构建与测试

仓库提供与 Windows 等效的 Make 入口：

```bash
sudo apt install -y make  # 使用最小化 Ubuntu 安装时需要
make portable
make test
make lint
make build
make apk
```

或直接使用 Gradle：

```bash
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

若执行位尚未由 Git 正确带回，`make` 会自动使用 `sh ./gradlew`，不依赖可执行位。

APK 位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 8. 真机调试

```bash
adb devices -l
make devices
```

Linux 首次连接 Android 手机通常需要 udev 规则。对于自己确认过厂商 ID 的设备，可以创建 `/etc/udev/rules.d/51-android.rules`，例如：

```text
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"
```

然后：

```bash
sudo udevadm control --reload-rules
sudo udevadm trigger
```

重新插拔设备，并在手机上确认 USB 调试授权。不要把不认识的厂商 ID 直接复制进系统规则。

## 9. 模拟器

```bash
ls -l /dev/kvm
sudo apt install -y cpu-checker
kvm-ok
```

若当前用户不能访问 `/dev/kvm`：

```bash
sudo usermod -aG kvm "$USER"
```

注销并重新登录后再启动模拟器。BIOS/UEFI 中需要开启虚拟化。

Ubuntu 24.04 默认 Wayland 通常可运行 Android Emulator；若出现输入、缩放或图形异常，可在登录界面临时选择 Xorg 验证。

## 10. 不建议在 WSL 内跑 Android 模拟器

可以使用 WSL 编辑代码或运行部分 Gradle 单元测试，但完整 Android 调试推荐原生 Ubuntu：

- Android Emulator 需要 KVM 与 GPU/图形环境，在 WSL 中配置复杂且不稳定。
- USB 真机需要额外转发，ADB 重连和设备授权容易中断。
- Android Studio 的模拟器、Logcat 与布局检查器在原生桌面更可靠。

## 11. 自动 CI

新增 [Ubuntu 24.04 Android CI](../.github/workflows/android-ci.yml)。推送到 GitHub 后会执行：

1. Java 17。
2. API 35、Build Tools 35.0.0 和 Platform-Tools。
3. 单元测试。
4. Android Lint。
5. Debug APK。
6. 上传 APK artifact。

CI 不能替代 API 26/35 模拟器和鸿蒙兼容真机验收，但它能持续检查 Ubuntu 构建脚本、Kotlin 编译和 Lint。

## 12. 常见问题

### `Permission denied: ./gradlew`

```bash
chmod +x gradlew
```

或者直接使用 `make test` / `sh ./gradlew ...`。

### `SDK location not found`

```bash
bash scripts/set-local-sdk.sh "$HOME/Android/Sdk"
```

### `Unsupported class file major version`

在 Android Studio 的 Gradle JDK 中选择 JDK 17 或 21，不要混用更旧的系统 Java。

### `adb` 看不到手机

先执行 `adb devices -l`，再检查 `lsusb`、USB 调试授权和 udev 规则。

### 模拟器启动很慢

检查 `/dev/kvm`、BIOS 虚拟化、内存和系统镜像架构。不要在没有 KVM 时继续大量界面调试。

### 文件在 Windows 能编译，Ubuntu 找不到

Ubuntu 文件系统区分大小写。按 CI 的编译错误修正 import、资源名或路径大小写，不要在 Linux 上创建仅大小写不同的重复文件。

## 13. 当前连续性边界

- 代码、Gradle 配置、资源、文档和视觉资产已按跨平台方式组织。
- Shell 启动器、Ubuntu 检查、SDK 配置、Make 入口和 Ubuntu CI 已加入仓库。
- 当前生成文档的电脑仍无 Android SDK，因此不能声称 APK 已构建。
- 第一次在 Ubuntu 执行 `make check && make build` 时，可能需要修正 Compose、PDFBox 或 WorkManager 的具体 API 编译差异，并在 [开发日志](development-log.md) 记录。
