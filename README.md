# 🎯 中国象棋 AI 助手 - Android 悬浮窗版

> 在 JJ 象棋中实时显示 AI 建议，基于 Pikafish 引擎

[![Build Status](https://github.com/li-xiao-kun/Chinese-chess/actions/workflows/build-android.yml/badge.svg)](https://github.com/li-xiao-kun/Chinese-chess/actions)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=flat&logo=android)](https://github.com/li-xiao-kun/Chinese-chess/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📱 快速下载（无需开发环境）

### 👉 [点击这里下载最新版 APK](https://github.com/li-xiao-kun/Chinese-chess/releases/latest)

**只需 3 步，5 分钟开始使用：**

1. **下载 APK**（约 10MB）
2. **安装到手机**（允许未知来源）
3. **配置服务器地址**（输入电脑 IP）

> 💡 **完全无需 Android Studio、JDK 等开发工具！**

---

## ✨ 功能特点

### 🎨 悬浮窗显示
- ✅ 实时 AI 着法建议
- ✅ 局势评估分数
- ✅ 自由拖动位置
- ✅ 渐变美观 UI

### 🚀 智能分析
- ✅ 基于 Pikafish 引擎
- ✅ 可调节分析深度
- ✅ 自动/手动刷新
- ✅ 实时状态显示

### 📱 完美兼容
- ✅ JJ 象棋 App
- ✅ 其他象棋应用
- ✅ Android 7.0+
- ✅ 无需 root

---

## 📥 下载与安装

### 方法一：直接下载（推荐）

1. **访问 Releases 页面**
   
   👉 https://github.com/li-xiao-kun/Chinese-chess/releases/latest

2. **下载文件**
   - `app-debug.apk` - 推荐，兼容性好（~10MB）

3. **安装到手机**
   - 允许"安装未知应用"
   - 点击 APK 安装
   - 允许悬浮窗权限

### 方法二：手机直接扫码

扫描下方二维码直接下载：

```
[ 二维码图片位置 ]
- 使用手机浏览器扫描
- 或微信"扫一扫"
```

### 方法三：微信/QQ 传输

1. 电脑下载 APK
2. 用微信/QQ 文件助手发送到手机
3. 手机打开文件安装

---

## 🚀 快速开始

### 第一步：启动电脑后台（1 分钟）

```bash
# 方式 1: 双击运行
启动服务_修复版.bat

# 方式 2: 命令行
python web_assistant.py

# 记下显示的 IP 地址，例如：
# Running on http://192.168.1.100:5000/
```

### 第二步：配置 Android App（1 分钟）

1. 打开手机上的"象棋AI助手"
2. 输入服务器地址：`http://192.168.1.100:5000`
3. 点击"启动悬浮窗"
4. 允许悬浮窗权限（首次需要）

### 第三步：开始下棋（立即）

1. 打开 JJ 象棋 App
2. 开始对局
3. 悬浮窗自动显示 AI 建议
4. 跟随建议下棋！

**总耗时：不到 3 分钟** ⚡

---

## 📊 系统要求

### 手机端
- Android 7.0 或更高版本
- 约 50MB 存储空间
- WiFi 网络连接

### 电脑端
- Windows / Mac / Linux
- Python 3.6+
- Pikafish 引擎（已包含）
- 与手机同一 WiFi 网络

---

## 📖 使用文档

### 用户文档
- [云端下载APK使用指南.md](./云端下载APK使用指南.md) - 详细下载安装说明
- [Android悬浮窗使用指南.md](./Android悬浮窗使用指南.md) - 功能使用说明
- [快速开始_Android悬浮窗.md](./快速开始_Android悬浮窗.md) - 快速上手

### 开发者文档
- [GitHub自动构建设置指南.md](./GitHub自动构建设置指南.md) - 云端构建配置
- [无需开发环境_云端方案总结.md](./无需开发环境_云端方案总结.md) - 技术方案
- [Android悬浮窗方案总结.md](./Android悬浮窗方案总结.md) - 实现细节

---

## 🎥 效果预览

### 悬浮窗界面

```
┌──────────────────┐
│ 象棋AI助手   🟢  │  ← 在线状态
│ 14:30:25        │  ← 当前时间
├──────────────────┤
│     炮8平5       │  ← AI 推荐着法
│   评估: +0.45   │  ← 局势评分
│ 深度: 18  14:30 │  ← 分析信息
├──────────────────┤
│ [🔄 刷新] [⚙️]  │  ← 操作按钮
└──────────────────┘
    ↑ 可拖动到任意位置
```

### 在 JJ 象棋中使用

```
📱 手机屏幕
┌─────────────────────────┐
│   JJ 象棋标题栏          │
├─────────────────────────┤
│  ┌───────────────┐      │ ← 悬浮窗
│  │ 炮8平5 +0.45  │      │
│  └───────────────┘      │
│                         │
│    [车][马][相]...      │ ← 棋盘
│        ...              │
└─────────────────────────┘
```

---

## ❓ 常见问题

<details>
<summary><b>Q: 安装时提示"未知来源"怎么办？</b></summary>

**A**: 需要允许安装未知应用：
- 小米：设置 → 隐私保护 → 特殊权限 → 安装未知应用
- 华为：设置 → 安全 → 更多安全设置 → 安装外部来源应用
- OPPO/vivo：设置 → 安全 → 安装未知应用
</details>

<details>
<summary><b>Q: 悬浮窗不显示怎么办？</b></summary>

**A**: 需要授予悬浮窗权限：
1. 长按 App 图标 → 应用信息
2. 权限管理 → 显示悬浮窗 → 允许
</details>

<details>
<summary><b>Q: 提示"网络错误"？</b></summary>

**A**: 检查以下项目：
1. 手机和电脑是否在同一 WiFi
2. 电脑是否已启动后台服务
3. 输入的 IP 地址是否正确
4. 防火墙是否阻止了 5000 端口
</details>

<details>
<summary><b>Q: 可以不用电脑吗？</b></summary>

**A**: 目前需要电脑运行 Pikafish 引擎（性能强大）。未来计划：
- [ ] 云端引擎版本（完全不需要电脑）
- [ ] 手机内置轻量级引擎
</details>

<details>
<summary><b>Q: 支持其他象棋 App 吗？</b></summary>

**A**: 支持！悬浮窗可以在任何 App 上显示，包括：
- ✅ JJ 象棋
- ✅ 天天象棋
- ✅ 其他象棋应用
- ✅ 浏览器象棋网站
</details>

---

## 🔧 技术架构

```
┌─────────────┐         WiFi          ┌─────────────┐
│  Android    │ ←──────────────────→  │   电脑端    │
│  悬浮窗 App │   HTTP REST API      │  Python后端 │
└─────────────┘                       └─────────────┘
      ↓                                      ↓
   WebView                            Pikafish 引擎
   (UI 渲染)                          (AI 计算)
```

### 核心组件
- **前端**：Android WebView + HTML/CSS/JS
- **后端**：Python Flask + REST API
- **引擎**：Pikafish (Stockfish for Xiangqi)
- **通信**：HTTP/JSON over WiFi

---

## 🛠️ 开发相关

### 本地构建（可选）

如果你想自己编译 APK：

```bash
# 1. 克隆仓库
git clone https://github.com/li-xiao-kun/Chinese-chess.git

# 2. 打开 Android Studio
# File → Open → 选择 android_app 目录

# 3. 构建
./gradlew assembleDebug

# 4. 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 云端自动构建

推送标签即可触发自动构建：

```bash
git tag -a v1.1.0 -m "新版本"
git push origin v1.1.0

# 5-10 分钟后，APK 自动出现在 Releases 页面
```

---

## 📈 版本历史

### v1.0.0 (2024-09-24)
- ✅ 初始发布
- ✅ Android 悬浮窗功能
- ✅ 实时 AI 建议
- ✅ 局势评估显示
- ✅ 可拖动悬浮窗
- ✅ 云端自动构建

### 未来计划
- [ ] 支持多候选着法显示
- [ ] 添加历史记录功能
- [ ] 支持主题切换
- [ ] 添加语音播报
- [ ] 优化网络重连机制
- [ ] 云端引擎版本

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 报告问题
- [提交 Bug](https://github.com/li-xiao-kun/Chinese-chess/issues/new?labels=bug)
- [功能建议](https://github.com/li-xiao-kun/Chinese-chess/issues/new?labels=enhancement)

### 参与开发
```bash
# 1. Fork 本仓库
# 2. 创建特性分支
git checkout -b feature/新功能

# 3. 提交更改
git commit -m "添加新功能"

# 4. 推送到分支
git push origin feature/新功能

# 5. 创建 Pull Request
```

---

## 📄 开源协议

本项目采用 MIT 协议开源。

---

## 🙏 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) - 强大的中国象棋引擎
- [Stockfish](https://stockfishchess.org/) - 原始国际象棋引擎
- [JJ 象棋](http://www.jj.cn/) - 测试平台

---

## 💬 联系方式

- GitHub Issues: https://github.com/li-xiao-kun/Chinese-chess/issues
- 项目主页: https://github.com/li-xiao-kun/Chinese-chess

---

## 📱 立即下载

**无需开发环境，2 分钟安装，立即使用！**

👉 [下载最新版 APK](https://github.com/li-xiao-kun/Chinese-chess/releases/latest)

---

**⭐ 觉得有用？给项目一个 Star 吧！**

Made with ❤️ by li-xiao-kun
