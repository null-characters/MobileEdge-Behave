# 开发环境信息

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| V1.0 | 2026-07-21 | 初稿 |

---

## 环境概览

| 组件 | 状态 | 版本/详情 |
|------|------|----------|
| 操作系统 | ✅ | macOS Darwin 25.5.0 (arm64) |
| Java (JDK) | ✅ 已安装 | OpenJDK 17.0.15 |
| Android Studio | ✅ 已安装 | /Applications/Android Studio.app |
| Android SDK | ✅ 已安装 | ~/Library/Android/sdk |
| ADB | ✅ 已安装 | 37.0.0 |
| Gradle | ⚠️ 未全局安装 | 项目使用 Gradle Wrapper |

---

## JDK 环境

```
版本: OpenJDK 17.0.15
路径: /opt/homebrew/bin/java
供应商: Homebrew
```

---

## Android SDK

### SDK 路径

```
~/Library/Android/sdk
```

### 已安装组件

#### Build Tools

| 版本 |
|------|
| 34.0.0 |
| 35.0.0 |
| 36.0.0 |
| 36.1.0 |

#### Platforms

| 版本 | API Level |
|------|-----------|
| android-34 | 34 |
| android-35 | 35 |
| android-36 | 36 |
| android-36.1 | 36.1 |

#### 其他组件

| 组件 | 说明 |
|------|------|
| platform-tools | ADB 等工具 |
| emulator | Android 模拟器 |
| cmdline-tools | 命令行工具 |
| sources | Android 源码 |
| system-images | 系统镜像 |

---

## 测试设备

### 红米 K60

| 属性 | 值 |
|------|-----|
| 设备 ID | e5c9e845 |
| 型号 | 23013RK75C |
| 代号 | mondrian |
| 连接方式 | USB |
| 状态 | 已连接 |

详细设备信息见 [device-info.md](./device-info.md)

---

## 环境变量

### 已配置的环境变量

```bash
# Android SDK
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

### ADB 路径

```bash
~/Library/Android/sdk/platform-tools/adb
```

---

## 项目开发要求

根据 `docs/standards/skills/architecture.md`：

| 要求 | 当前环境 | 状态 |
|------|----------|------|
| minSdk | 23 (Android 6.0) | ✅ 支持 |
| targetSdk | 35 (Android 15) | ✅ 已安装 android-35 |
| Kotlin | 1.9+ | ✅ Android Studio 内置 |
| JDK | 17+ | ✅ OpenJDK 17 |

---

## 常用命令

### ADB 调试

```bash
# 查看已连接设备
~/Library/Android/sdk/platform-tools/adb devices -l

# 查看 App 日志
~/Library/Android/sdk/platform-tools/adb logcat | grep "BehaviorMonitor"

# 安装 APK
~/Library/Android/sdk/platform-tools/adb install app-debug.apk

# 查看设备温度
~/Library/Android/sdk/platform-tools/adb shell dumpsys battery | grep temperature

# 导出数据库
~/Library/Android/sdk/platform-tools/adb pull /data/data/<package>/databases/ ./
```

### Gradle 构建

```bash
# 使用 Gradle Wrapper 构建
./gradlew assembleDebug

# 清理构建
./gradlew clean

# 运行单元测试
./gradlew test

# 安装到设备
./gradlew installDebug
```

---

## 环境验证清单

- [x] JDK 17+ 已安装
- [x] Android Studio 已安装
- [x] Android SDK 已配置
- [x] ADB 可用
- [x] targetSdk (android-35) 已安装
- [x] 测试设备已连接
- [x] 设备已启用 USB 调试
