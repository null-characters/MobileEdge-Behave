# MobileEdge-Behave

基于旧安卓手机的边缘计算行为监测系统。

## 项目简介

利用旧安卓手机作为边缘计算节点，通过摄像头采集和本地 AI 推理，实现办公室环境下的在岗/离岗状态监测与行为分析。所有数据处理均在本地完成，保护用户隐私。

## 核心功能

### V1.0 MVP
- 人形检测（俯视视角）
- 在岗/离岗状态判断（状态机）
- 状态变化日志记录
- 简单统计界面（当日数据）
- CSV 数据导出
- MIUI 后台持续运行

### 后续版本
- V1.5：在岗时长统计 + 离岗告警
- V2.0：工作/摸鱼行为分类
- V3.0：Web 仪表盘 + 多行为识别

## 技术栈

| 模块 | 技术方案 |
|------|----------|
| 开发语言 | Kotlin |
| UI 框架 | Jetpack Compose |
| 摄像头 | CameraX |
| 模型推理 | TensorFlow Lite + NNAPI |
| 数据存储 | Room (SQLite) |
| 后台服务 | Foreground Service |

## 硬件要求

**目标设备：红米 K60**
- 处理器：骁龙 8+ Gen 1
- RAM：12GB
- 存储：256GB
- 系统：MIUI 14+

**最低要求**
- Android 6.0+ (API 23+)
- 至少 2GB RAM
- 支持长期插电运行

## 项目结构

```
MobileEdge-Behave/
├── design/              # 设计文档
│   └── behavior-monitor-design.md
├── docs/                # 项目文档
│   └── device-info.md
└── README.md
```

## 文档

- [设计文档](design/behavior-monitor-design.md) - 完整的系统设计与技术方案
- [设备信息](docs/device-info.md) - 开发测试设备规格

## 隐私保护

- 所有图像处理在本地完成，不上传云端
- 只存储行为日志，不存储原始图像
- 不记录具体人员身份，只记录行为统计

## 许可证

MIT License
