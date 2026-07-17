# 设备信息

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| V1.0 | 2026-07-17 | 初稿 |
| V1.1 | 2026-07-17 | 补充完整设备信息 |

---

## 目标设备

### 基本信息

| 项目 | 值 |
|------|-----|
| 型号 | 红米 K60 (23013RK75C) |
| 品牌 | Redmi |
| 制造商 | Xiaomi |
| 设备代号 | mondrian |
| 主板代号 | taro |

### 硬件规格

| 项目 | 规格 |
|------|------|
| 处理器 | 骁龙 8+ Gen 1 |
| CPU 核心数 | 8 核 |
| CPU 最高频率 | 1.8 GHz（当前读取） |
| GPU | Adreno 730 |
| GPU API | OpenGL ES 3.2 |
| RAM | 11.4 GB（总量）/ 5.5 GB（可用） |
| 存储 | 224 GB（总量）/ 20 GB（可用） |

### 屏幕信息

| 项目 | 值 |
|------|-----|
| 物理分辨率 | 1440 × 3200 |
| 当前分辨率 | 1080 × 2400 |
| 物理密度 | 560 dpi |
| 当前密度 | 420 dpi |

### 软件环境

| 项目 | 版本 |
|------|------|
| Android 版本 | 15 |
| SDK 版本 | 35 |
| MIUI 版本 | V816 |

### ADB 连接信息

| 项目 | 值 |
|------|-----|
| 连接方式 | USB (Type-C) |
| ADB 路径 | `~/Library/Android/sdk/platform-tools/adb` |

---

## 开发环境

| 项目 | 说明 |
|------|------|
| 开发机 | macOS (darwin) |
| Android SDK 路径 | `/Users/fengbing/Library/Android/sdk` |

---

## 设备能力评估

### NPU/GPU 加速

骁龙 8+ Gen 1 支持：
- ✅ NNAPI 硬件加速
- ✅ Hexagon DSP
- ✅ Adreno 730 GPU 计算
- ✅ OpenGL ES 3.2

### 推理性能预估

| 模型 | 预估延迟 | 备注 |
|------|----------|------|
| EfficientDet-Lite0 | < 20ms | NNAPI 加速 |
| MoveNet Lightning | < 15ms | NNAPI 加速 |

---

## 注意事项

### MIUI 后台保活

MIUI 系统后台管理激进，需要：
- Foreground Service 常驻通知
- 引导用户关闭电池优化
- 引导用户开启自启动权限
- 设置后台运行"无限制"

### 散热建议

当前电池温度 36.8°C，长期运行需注意：
- 智能采样策略降低功耗
- 监控温度，超过 45°C 时降低帧率
- 保持良好通风环境

### 调试命令

```bash
# 查看 logcat 日志
adb logcat

# 安装 APK
adb install app-debug.apk

# 推送文件到设备
adb push local_file /sdcard/

# 从设备拉取文件
adb pull /sdcard/remote_file ./

# 查看实时温度
adb shell dumpsys battery | grep temperature

# 查看内存使用
adb shell cat /proc/meminfo | grep -E 'MemTotal|MemAvailable'
```
