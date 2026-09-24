# JJ 象棋 AI 助手集成说明

## 📱 功能概述

本项目支持通过 **Android 屏幕识别** 在 JJ 象棋 App 中使用 AI 分析功能。

### 工作原理
```
手机(JJ象棋) → ADB → 电脑截图 → OpenCV识别 → Web界面同步 → AI分析 → 建议着法
```

---

## 🚀 快速开始

### 方法一：使用脚本（推荐）

1. **检查环境**
   ```bash
   双击：检查ADB环境.bat
   ```

2. **启动服务**
   ```bash
   双击：启动服务_修复版.bat
   ```

3. **使用识别**
   - 浏览器打开 http://localhost:5000
   - 切换到"识别助手" Tab
   - 点击"启动 JJ 识别"
   - 完成四角校准
   - 自动识别并获取 AI 建议

### 方法二：命令行

```bash
# 1. 检查 ADB
adb devices

# 2. 启动 Web 服务
python web_assistant.py

# 3. 测试识别功能
python 测试手机识别.py
```

---

## 📋 前置条件

### 必需
- ✅ Android 手机（JJ 象棋 App）
- ✅ USB 数据线
- ✅ ADB 工具（Android Debug Bridge）
- ✅ Python 依赖：numpy, opencv-python, Pillow

### 可选
- 📱 无线 ADB（WiFi 连接）
- 🎨 多种棋盘皮肤支持

---

## 📦 安装步骤

### 1. 安装 ADB

**Windows**:
```bash
# 下载 platform-tools
https://developer.android.com/studio/releases/platform-tools

# 解压到 C:\adb
# 添加到 PATH 环境变量
```

**验证安装**:
```bash
adb version
```

### 2. 安装 Python 依赖

```bash
pip install numpy opencv-python Pillow
```

已包含在 `requirements.txt` 中。

### 3. 配置手机

1. 开启 USB 调试：
   - 设置 → 关于手机 → 连续点击版本号7次
   - 设置 → 开发者选项 → USB 调试

2. 连接电脑：
   - USB 连接
   - 授权 USB 调试

3. 验证连接：
   ```bash
   adb devices
   ```

---

## 🎮 使用流程

### 标准流程

```
1. 启动 Web 服务
   └─ 双击 启动服务_修复版.bat

2. 启动引擎
   └─ Web 界面点击"启动引擎"

3. 打开 JJ 象棋
   └─ 手机进入对局界面

4. 启动识别
   └─ Web "识别助手" Tab → "启动 JJ 识别"

5. 首次校准（仅一次）
   └─ 点击棋盘四角
   └─ 选择视角（红方/黑方在下）
   └─ 确认校准

6. 实时使用
   └─ 自动识别局面（置信度 > 78%）
   └─ 按 A 键获取 AI 建议
   └─ 按 M 键查看多个候选
   └─ 在 JJ 中手动走棋
```

### 高级功能

- **自动执行**：AI 建议自动点击手机（实验性，有封号风险）
- **无线 ADB**：通过 WiFi 连接手机
- **多皮肤支持**：自定义棋盘皮肤识别
- **置信度调整**：根据识别效果调优

---

## 🔧 配置文件

### 识别配置

位置：`recognition_companion.py`

```python
MIN_CONFIDENCE = 0.78          # 最小置信度（默认 78%）
STABLE_FRAME_TARGET = 2        # 稳定帧数（默认 2 帧）
TEMPLATE_SIZE = 52             # 模板尺寸（默认 52px）
```

### 校准数据

位置：`recognition_profiles/jj_xiangqi_android_default.json`

包含：
- 棋盘四角坐标
- 棋子识别模板
- 视角方向
- 校准时间戳

**重要**：首次校准成功后，备份整个 `recognition_profiles/` 目录。

---

## 📊 识别原理

### 技术栈
- **ADB**：手机屏幕截图
- **OpenCV**：图像处理和模板匹配
- **NumPy**：数组运算
- **Pillow**：图像格式转换

### 识别流程

```
1. ADB 截图
   └─ adb exec-out screencap -p

2. 棋盘定位
   └─ 透视变换到标准尺寸

3. 棋子识别
   └─ 模板匹配（Template Matching）
   └─ 每个交叉点识别棋子类型

4. FEN 生���
   └─ 棋盘状态 → FEN 字符串

5. 同步到 Web
   └─ HTTP POST /api/position_sync

6. AI 分析
   └─ Pikafish 引擎分析局面
```

### 识别准确率

| 条件 | 准确率 |
|------|--------|
| 标准皮肤 + 良好光线 | 95%+ |
| 非标准皮肤 | 70-90% |
| 光线不足/反光 | 50-70% |

---

## 🐛 故障排查

### ADB 相关

| 问题 | 原因 | 解决 |
|------|------|------|
| adb 命令不存在 | 未安装或未配置 PATH | 安装 ADB 并配置环境变量 |
| 无设备连接 | USB 未连接或调试未开启 | 检查连接和手机设置 |
| unauthorized | 未授权 USB 调试 | 手机重新授权 |
| offline | 设备离线 | 重启 ADB 或手机 |

### 识别相关

| 问题 | 原因 | 解决 |
|------|------|------|
| 置信度低 (< 50%) | 校准不准确 | 重新四角校准 |
| 识别不同步 | 阈值太高 | 降低置信度到 70% |
| 棋子识别错误 | 皮肤不支持 | 切换到标准皮肤 |
| 画面卡顿 | USB 连接不稳定 | 更换数据线或使用无线 ADB |

### Web 相关

| 问题 | 原因 | 解决 |
|------|------|------|
| 识别助手不可用 | 依赖未安装 | pip install numpy opencv-python Pillow |
| 无法启动识别 | ADB 未连接 | 检查 adb devices |
| 画面不更新 | 识别未运行 | 点击"启动 JJ 识别" |

---

## 🔐 安全与合规

### ⚠️ 使用限制

**允许的使用场景**：
- ✅ 个人学习研究
- ✅ 复盘分析
- ✅ 人机对战练习
- ✅ 开局定式研究

**禁止的使用场景**：
- ❌ 竞技排位赛
- ❌ 真人对战作弊
- ❌ 锦标赛/比赛
- ❌ 涉及金钱的对局

### 法律声明

1. 本功能仅供学习研究使用
2. 使用自动执行功能可能违反游戏服务条款
3. 可能导致账号被封禁或限制
4. 使用者自行承担一切风险和责任
5. 开发者不对任何损失负责

### 建议

- 仅在人机对战中使用
- 不要使用自动执行功能
- 手动查看 AI 建议并思考
- 将其作为学习工具而非作弊工具

---

## 📚 相关文档

### 快速入门
- [JJ象棋快速开始.md](JJ象棋快速开始.md) - 5分钟快速开始
- [检查ADB环境.bat](检查ADB环境.bat) - 环境检查脚本
- [测试手机识别.py](测试手机识别.py) - 功能测试脚本

### 详细指南
- [JJ象棋使用指南.md](JJ象棋使用指南.md) - 完整使用说明
- [使用指南.md](使用指南.md) - Web 界面操作
- [成功运行指南.md](成功运行指南.md) - 系统配置

### 技术文档
- [recognition_companion.py](recognition_companion.py) - 识别模块源码
- [web_assistant.py](web_assistant.py) - Web 服务源码
- [README.md](README.md) - 项目主文档

---

## 🛠️ 测试与调试

### 环境检查
```bash
# 检查 ADB 环境
检查ADB环境.bat

# 或命令行
adb devices
adb shell "echo 'Test OK'"
```

### 功能测试
```bash
# 测试识别功能
python 测试手机识别.py

# 测试 API
python test_api.py

# 测试引擎
python test_simple.py
```

### 调试模式

编辑 `recognition_companion.py`，启用调试输出：

```python
DEBUG = True  # 打印识别详情
SAVE_DEBUG_IMAGES = True  # 保存调试图像
```

---

## 💡 高级技巧

### 无线 ADB（不用 USB 线）

```bash
# 1. USB 连接，启用无线
adb tcpip 5555

# 2. 拔掉 USB

# 3. 连接 WiFi IP（在手机设置中查看）
adb connect 192.168.1.xxx:5555

# 4. 验证
adb devices
```

### 提高识别速度

```python
# 降低截图分辨率
adb shell wm size 720x1280

# 恢复原始分辨率
adb shell wm size reset
```

### 自定义识别参数

编辑 `recognition_companion.py`:

```python
# 更宽松的识别（更快但可能不准）
MIN_CONFIDENCE = 0.70
STABLE_FRAME_TARGET = 1

# 更严格的识别（更慢但更准）
MIN_CONFIDENCE = 0.85
STABLE_FRAME_TARGET = 3
```

---

## 🎯 性能指标

### 识别性能
- **截图延迟**：50-200ms
- **识别延迟**：100-500ms（取决于设备性能）
- **总响应时间**：< 1 秒
- **准确率**：85-95%（标准皮肤）

### 系统要求
- **CPU**：双核以上
- **内存**：2GB 以上
- **Python**：3.6+
- **USB**：USB 2.0 以上

---

## 📞 获取帮助

### 常见问题
1. 查看 [JJ象棋使用指南.md](JJ象棋使用指南.md) 的故障排查章节
2. 运行 [检查ADB环境.bat](检查ADB环境.bat) 诊断
3. 运行 [测试手机识别.py](测试手机识别.py) 测试

### 技术支持
- **项目主页**：https://github.com/official-pikafish/Pikafish
- **问题反馈**：项目 Issues 页面
- **ADB 文档**：https://developer.android.com/studio/command-line/adb

---

## 📝 更新日志

### v2.2 (当前)
- ✅ JJ 象棋 Android 识别支持
- ✅ 自动四角校准
- ✅ 多皮肤支持
- ✅ 实时局面同步
- ✅ 自动执行着法（实验性）

### 未来计划
- 🔄 iOS 设备支持
- 🔄 其他象棋 App 支持
- 🔄 云识别服务
- 🔄 更多皮肤预设

---

**🎉 开始在 JJ 象棋中使用 AI 助手吧！**

**⚠️ 请遵守游戏规则，合理使用辅助工具！**
