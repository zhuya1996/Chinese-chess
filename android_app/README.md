# 象棋 AI 助手 - Android 悬浮窗 App

## 项目说明

这是一个简单的 Android 悬浮窗应用，通过 WebView 连接电脑后台，实时显示 AI 分析结果。

## 功能特性

- ✅ 悬浮窗显示（可拖动、可最小化）
- ✅ 实时 AI 建议
- ✅ 自动刷新（3/5/10秒）
- ✅ 可调整搜索深度
- ✅ 美观的渐变 UI

## 快速开始

### 使用 Android Studio（推荐）

1. 打开 Android Studio
2. File → New → New Project
3. 选择 "Empty Activity"
4. 包名: `com.xiangqi.aiassistant`
5. 最低 SDK: API 24 (Android 7.0)
6. 完成创建后，替换以下文件

### 手动创建项目结构

```
android_app/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/xiangqi/aiassistant/
│   │       │   ├── MainActivity.java
│   │       │   ├── FloatingWindowService.java
│   │       │   └── SettingsActivity.java
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   └── activity_settings.xml
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   └── colors.xml
│   │       │   └── drawable/
│   │       │       └── ic_launcher.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
└── build.gradle
```

## 使用步骤

1. **启动电脑后台服务**
   ```bash
   python web_assistant.py
   ```

2. **安装 App 到手机**
   - 在 Android Studio 点击 Run
   - 或使用 `./gradlew installDebug`

3. **配置服务器地址**
   - 打开 App
   - 输入电脑 IP 地址（如 `192.168.1.100`）
   - 点击"启动悬浮窗"

4. **授权悬浮窗权限**
   - 系统会提示授权
   - 允许悬浮窗权限

5. **开始使用**
   - 打开 JJ 象棋
   - 悬浮窗自动显示 AI 建议

## 配置说明

### 修改服务器地址

编辑 `MainActivity.java`:
```java
private static final String DEFAULT_SERVER = "http://192.168.1.100:5000";
```

### 修改默认设置

编辑 `FloatingWindowService.java`:
```java
private static final int DEFAULT_DEPTH = 15;
private static final int DEFAULT_AUTO_REFRESH = 5000; // 5秒
```

## 权限说明

### 必需权限
- `SYSTEM_ALERT_WINDOW` - 悬浮窗显示
- `INTERNET` - 网络访问

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 常见问题

### Q: 悬浮窗不显示？
A: 检查是否授权了悬浮窗权限（设置 → 应用 → 特殊权限 → 显示在其他应用上层）

### Q: 显示"网络错误"？
A: 检查手机和电脑是否在同一 WiFi，检查服务器地址是否正确

### Q: AI 建议不更新？
A: 检查电脑后台引擎是否启动，浏览器访问 http://电脑IP:5000 测试

### Q: 如何关闭悬浮窗？
A: 从通知栏点击"停止悬浮窗"，或在 App 中点击"停止服务"

## 技术栈

- **语言**: Java
- **最低版本**: Android 7.0 (API 24)
- **目标版本**: Android 14 (API 34)
- **UI**: WebView + 自定义悬浮窗
- **网络**: 原生 HttpURLConnection

## 后续优化

- [ ] 支持 HTTPS
- [ ] 支持多个候选着法
- [ ] 支持形势评估图
- [ ] 支持历史记录
- [ ] 支持离线模式
- [ ] 支持自定义悬浮窗样式
- [ ] 支持语音播报

## 开源协议

GPL v3
