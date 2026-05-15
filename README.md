# 中国象棋 AI 助手

基于 [Pikafish](https://github.com/official-pikafish/Pikafish) 引擎（Stockfish 的象棋分支）构建的交互式中国象棋 AI 分析平台。提供 Flask Web 图形界面、命令行工具，以及可选的 Android 手机屏幕识别功能。

## 功能特性

- **交互式 Web 棋盘** — 点击选子、走棋，支持朱砂红/墨玉棋子、四种棋盘主题
- **AI 引擎分析** — 集成 Pikafish UCI 引擎，支持可配置搜索深度（固定 / 区间模式）
- **一键最佳着法建议** — 分析当前局面并显示最佳走法，支持一键落子
- **多候选着法** — MultiPV 分析，展示 1-5 个候选着法及其评估值
- **形势评估条** — 全局彩色评估条，直观显示红黑双方优劣
- **着法记录** — 完整走棋历史，可在 Tab 面板中查看
- **开局库参考** — 内置常见开局（中炮、屏风马、仙人指路等）
- **硬件自适应** — 自动检测 CPU 核心数和内存，优化引擎参数
- **自动提示通知** — 可选的自动提示模式，走棋后自动给出建议
- **Android 屏幕识别** — 通过 ADB 连接手机，识别 JJ 象棋 App 画面并同步局面（可选）
- **热部署模式** — 开发时自动重载模板，方便调试
- **多种棋盘主题** — 古典木纹、米黄雅韵、乌木深沉、碧玉青翠

## 快速开始

### 1. 环境要求

- Python 3.9+
- 编译好的 `pikafish.exe` 和 `pikafish.nnue`（放在 `src/` 目录下）
- Windows 用户可直接使用项目提供的 `.bat` 脚本

### 2. 安装 Python 依赖

```bash
# Windows（双击）
安装依赖.bat

# 或手动安装
pip install -r requirements.txt
```

### 3. 获取引擎文件

**方式一：自行编译（需要 MSYS2 + MinGW64）**

```powershell
# 先安装 MSYS2（如未安装）
.\setup_msys2.ps1

# 编译 Pikafish 引擎
.\build_pikafish.ps1

# 下载神经网络模型
bash scripts/net.sh
```

**方式二：下载预编译版本**

从 [Pikafish Releases](https://github.com/official-pikafish/Pikafish/releases) 下载对应平台的 `pikafish.exe`，并下载 `pikafish.nnue` 网络文件，将两者放入 `src/` 目录。

### 4. 启动 Web 服务

```bash
# Windows（双击）
启动Web界面.bat

# 开发模式（模板改动后自动重启）
启动Web界面_热部署.bat

# 或手动启动
python web_assistant.py
# 指定端口
python web_assistant.py --port 8080
# 热部署模式
python web_assistant.py --hot-reload
```

启动后在浏览器打开 **http://localhost:5000**。

## 使用说明

### Web 界面操作

1. **启动引擎** — 点击"启动引擎"按钮，等待状态显示"引擎就绪"
2. **走棋** — 点击棋子选中，再点击目标位置（或可走位置的高亮圆点）
3. **获取建议** — 点击"获取建议"按钮或按 `A` 键
4. **应用建议** — 点击"建议并落子"按钮，AI 的最佳着法会自动执行
5. **多个候选** — 点击"多个候选"按钮或按 `M` 键查看多个候选着法
6. **撤销** — 点击"撤销"按钮或按 `Z` 键
7. **翻转棋盘** — 点击"翻转"按钮切换视角
8. **重置** — 点击"重置"按钮回到初始局面

右侧面板包含四个 Tab：

| Tab | 内容 |
|-----|------|
| 着法记录 | 完整走棋历史 |
| 候选&评估 | 分析结果、评估值、PV 主线、形势评估条、棋谱讲解 |
| 识别助手 | Android 手机屏幕识别（需要 ADB） |
| 工具设置 | 搜索深度、AI 辅助模式、添加着法、FEN 工具、主题切换 |

### 命令行工具

```bash
# 快速单步分析（可选参数：FEN 和深度）
python quick_analyze.py "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w" 20

# 交互式 CLI 模式
python chess_assistant.py
# 进入后可用命令：start / move e2e4 / suggest / analyze 20 / fen <FEN> / quit
```

### Android 屏幕识别（可选）

如需使用手机屏幕识别功能：

1. 安装 [ADB](https://developer.android.com/studio/command-line/adb) 并确保 `adb devices` 能识别手机
2. 手机打开 JJ 象棋 App，进入标准棋盘界面
3. 在 Web 界面的"识别助手"Tab 中启动识别
4. 按提示进行四角校准
5. 识别成功后，系统会自动同步局面并给出 AI 建议

## 项目结构

```
├── web_assistant.py          # 主 Web 应用（Flask + API 路由）
├── chess_assistant.py        # 命令行交互式助手 / 引擎接口
├── engine_optimizer.py       # 硬件检测 + 引擎参数优化
├── opening_book.py           # 中国象棋开局知识库
├── recognition_companion.py  # Android 手机屏幕识别模块
├── quick_analyze.py          # CLI 快速单次分析工具
├── test_assistant.py         # 基础功能测试
├── requirements.txt          # Python 依赖
├── 启动Web界面.bat            # Windows 启动脚本
├── 启动Web界面_热部署.bat     # Windows 热部署启动脚本
├── 安装依赖.bat              # Windows 依赖安装脚本
├── 快速分析.bat              # Windows 快速分析启动脚本
├── build_pikafish.ps1        # Pikafish 引擎编译脚本
├── setup_msys2.ps1           # MSYS2 工具链安装脚本
├── templates/
│   └── chess.html            # Web 界面（单页应用）
├── src/                      # Pikafish C++ 引擎源代码
│   ├── Makefile
│   └── *.cpp / *.h
├── recognition_profiles/     # 识别校准数据（运行时生成）
├── scripts/                  # 构建辅助脚本
└── tests/                    # 引擎测试脚本
```

## 依赖项

### Python 包

| 包 | 用途 |
|---|------|
| Flask | Web 框架 |
| Werkzeug | WSGI 工具库 |
| numpy | 数组运算（识别模块） |
| opencv-python | 图像处理（识别模块） |
| Pillow | 图像处理（识别模块） |
| psutil | 硬件信息检测（优化模块） |

### 系统工具（可选）

| 工具 | 用途 |
|------|------|
| ADB | Android 手机屏幕识别 |
| MSYS2 + MinGW64 | Windows 下编译 Pikafish 引擎 |

## 许可证

本项目基于 GPL v3 许可证开源，详见 [Copying.txt](Copying.txt)。

Pikafish 引擎源代码位于 `src/` 目录，基于 Stockfish 的象棋适配版本，同样遵循 GPL v3 许可证。

## 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) — 强大的中国象棋 UCI 引擎
- [Stockfish](https://stockfishchess.org/) — 世界顶级国际象棋引擎，Pikafish 的基石
- Pika Xiangqi Zero 项目 — 提供 NNUE 训练数据
