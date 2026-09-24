# 📱 APK 直接下载 - 无需开发环境

## 🚀 一键下载，立即使用

### 最新版本下载

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/你的用户名/Chinese-chess/releases/latest)

**点击上方按钮直接下载最新版 APK** 👆

---

## 📥 下载步骤

### 方法一：GitHub Releases（推荐）

1. 访问：https://github.com/你的用户名/Chinese-chess/releases
2. 点击最新版本
3. 在 **Assets** 下载 `app-debug.apk`
4. 安装到手机

### 方法二：手机直接下载

用手机浏览器扫描下方二维码（需要你生成）：

```
┌───────────────────┐
│                   │
│   [QR Code 这里]   │
│                   │
└───────────────────┘
```

### 方法三：微信/QQ 传输

1. 电脑访问 [Releases](https://github.com/你的用户名/Chinese-chess/releases)
2. 下载 `app-debug.apk` 到电脑
3. 用微信/QQ 文件传输助手发送到手机
4. 手机打开文件安装

---

## ⚡ 5 分钟上手指南

### 第一步：安装 APK（1分钟）
- 允许"安装未知来源应用"
- 点击 APK 文件安装
- 允许悬浮窗权限

### 第二步：启动电脑后台（1分钟）
```bash
# 双击运行
启动服务_修复版.bat

# 记下 IP 地址，例如：
# Running on http://192.168.1.100:5000/
```

### 第三步：配置 App（1分钟）
- 打开"象棋AI助手"
- 输入服务器地址：`http://你的电脑IP:5000`
- 点击"启动悬浮窗"

### 第四步：开始下棋（2分钟）
- 打开 JJ 象棋
- 悬浮窗自动显示 AI 建议
- 跟随建议下棋！

---

## 📊 系统要求

### 手机
- ✅ Android 7.0 或更高
- ✅ 约 50MB 存储空间
- ✅ 连接 WiFi

### 电脑
- ✅ Windows/Mac/Linux
- ✅ Python 3.6+
- ✅ 连接同一 WiFi

---

## 🎯 版本选择

| 文件名 | 大小 | 说明 | 推荐 |
|--------|------|------|------|
| `app-debug.apk` | ~10MB | 测试版，兼容性好 | ⭐⭐⭐⭐⭐ |
| `app-release-unsigned.apk` | ~5MB | 发布版，部分手机可能无法安装 | ⭐⭐⭐ |

**建议**：优先下载 `app-debug.apk`

---

## ❓ 常见问题

<details>
<summary><b>Q: 安装时提示"未知来源"？</b></summary>

**A**: 正常现象，需要允许：
1. 设置 → 安全 → 允许安装未知应用
2. 或在安装提示时点击"设置" → "允许此来源"
</details>

<details>
<summary><b>Q: 找不到 Releases 页面？</b></summary>

**A**: 第一次需要手动创建：
```bash
git tag v1.0.0
git push origin v1.0.0
```
等待 5-10 分钟后刷新页面。
</details>

<details>
<summary><b>Q: 下载速度很慢？</b></summary>

**A**: GitHub 在国内较慢，可以：
- 使用代理/VPN
- 让朋友帮忙下载后传给你
- 使用 GitHub 加速镜像
</details>

<details>
<summary><b>Q: 每次更新都要重新下载吗？</b></summary>

**A**: 不需要！只有以下情况才更新：
- 修复了重要 Bug
- 添加了新功能
- 性能优化

正常使用一次安装即可。
</details>

---

## 🌟 功能预览

### 悬浮窗界面
```
┌──────────────────┐
│ 象棋AI助手   🟢  │
│ 14:30:25        │
├──────────────────┤
│     炮8平5       │  ← AI 推荐着法
│   评估: +0.45   │  ← 局势评估
│ 深度: 18  14:30 │  ← 分析信息
├──────────────────┤
│ [🔄 刷新] [⚙️]  │
└──────────────────┘
```

### 在 JJ 象棋中使用
```
手机屏幕
┌─────────────────────────┐
│   JJ 象棋               │
│  ┌───────────────┐      │ ← 悬浮窗
│  │ 炮8平5 +0.45  │      │
│  └───────────────┘      │
│                         │
│    [车][马][相]...      │ ← 棋盘
│        ...              │
└─────────────────────────┘
```

---

## 📚 完整文档

- [云端下载APK使用指南.md](./云端下载APK使用指南.md) - 详细下载说明
- [Android悬浮窗使用指南.md](./Android悬浮窗使用指南.md) - 功能使用说明
- [使用指南.md](./使用指南.md) - 整体项目文档

---

## 💬 反馈与支持

- 🐛 遇到问题？提交 [Issue](https://github.com/你的用户名/Chinese-chess/issues)
- 💡 有建议？提交 [Feature Request](https://github.com/你的用户名/Chinese-chess/issues/new)
- ⭐ 觉得好用？给项目一个 Star！

---

## 🎉 立即下载

**点击下载最新版 APK**：
👉 [Releases 页面](https://github.com/你的用户名/Chinese-chess/releases/latest)

**无需任何开发环境，2 分钟安装，5 分钟上手！** 🚀

---

## 📈 版本历史

- `v1.0.0` (2024-09-24) - 初始版本
  - ✅ 悬浮窗显示
  - ✅ AI 着法建议
  - ✅ 实时局势评估
  - ✅ 可拖动位置

更多版本信息见 [Releases](https://github.com/你的用户名/Chinese-chess/releases)

---

**Made with ❤️ by 象棋AI助手团队**
