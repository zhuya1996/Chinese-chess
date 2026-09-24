# ⚡ 快速修复指南

## 🚨 启动报错？按这个顺序操作

### 1️⃣ 第一步：运行诊断（30秒）

**双击运行**：`诊断环境.bat`

这会告诉你具体缺少什么。

---

### 2️⃣ 第二步：根据诊断结果修复

#### 情况 A：显示 "✗ Anaconda Python 不存在"

**解决方案**：使用系统 Python

**双击运行**：`启动服务_简单版.bat`

如果还是不行，手动运行：
```bash
python web_assistant.py
```

#### 情况 B：显示 "ModuleNotFoundError: No module named 'flask'"

**解决方案**：安装 Flask

**双击运行**：`安装依赖.bat`

或手动运行：
```bash
pip install Flask Werkzeug numpy opencv-python Pillow psutil
```

#### 情况 C：显示 "✗ src\pikafish.exe 不存在"

**解决方案**：引擎文件丢失

参考：[成功运行指南.md](成功运行指南.md) 第一部分下载引擎

---

### 3️⃣ 第三步：测试服务

#### 方法一：浏览器测试

打开浏览器访问：
```
http://localhost:5000
```

**成功标志**：看到象棋棋盘界面

#### 方法二：测试移动端页面

访问：
```
http://localhost:5000/mobile
```

**成功标志**：看到紫色渐变的 AI 助手卡片

---

## 🛠️ 可用的启动脚本

| 脚本 | 用途 | 推荐度 |
|------|------|--------|
| `启动服务_简单版.bat` | 最简单，快速启动 | ⭐⭐⭐⭐⭐ |
| `启动服务_自动检测.bat` | 自动检测环境 | ⭐⭐⭐⭐ |
| `启动服务_修复版.bat` | 完整检查 | ⭐⭐⭐ |
| `诊断环境.bat` | 诊断问题 | ⭐⭐⭐⭐⭐ |

**推荐顺序**：
1. 先运行 `诊断环境.bat` 看问题
2. 再运行 `启动服务_简单版.bat` 启动

---

## 📱 手机访问设置

### 第一步：获取电脑 IP

**方法一：运行诊断脚本**
`诊断环境.bat` 会显示你的 IP

**方法二：手动查询**
```bash
ipconfig | findstr "IPv4"
```

**记下类似这样的地址**：`192.168.1.100`

### 第二步：手机浏览器访问

```
http://192.168.1.100:5000/mobile
（替换为你的 IP）
```

### 第三步：添加到主屏幕

浏览器菜单 → 添加到主屏幕 → 完成！

---

## 💻 命令行启动（最可靠）

如果所有脚本都失败，直接用命令行：

### Windows 命令提示符

```bash
# 1. 打开命令提示符
Win + R → 输入 cmd → 确定

# 2. 切换到项目目录
cd /d E:\XQ\Chinese-chess

# 3. 启动服务
python web_assistant.py

# 如果 python 不行，试试完整路径
C:\ProgramData\Anaconda3\python.exe web_assistant.py
```

### PowerShell

```powershell
# 1. 打开 PowerShell
Win + X → Windows PowerShell

# 2. 切换目录
cd E:\XQ\Chinese-chess

# 3. 启动
python web_assistant.py
```

---

## 🆘 仍然不行？

### 提供这些信息给我

1. **诊断结果**
   ```bash
   诊断环境.bat > 诊断结果.txt
   ```
   把 `诊断结果.txt` 的内容发给我

2. **错误截图**
   - 启动脚本的错误截图
   - 完整的错误信息

3. **系统信息**
   ```bash
   # 查看系统版本
   Win + R → 输入 winver → 确定
   
   # 查看 Python 版本
   python --version
   ```

---

## ✅ 成功后的下一步

### Web 界面使用

1. 浏览器打开 `http://localhost:5000`
2. 点击"启动引擎"
3. 开始走棋！

### 移动端使用

1. 手机浏览器打开 `http://你的IP:5000/mobile`
2. 添加到主屏幕
3. 打开 JJ 象棋
4. 点击主屏幕上的快捷方式
5. 查看 AI 建议！

---

## 📚 详细文档

如需更多信息，查看：

- [启动问题解决方案.md](启动问题解决方案.md) - 详细故���排除
- [快速开始_Android悬浮窗.md](快速开始_Android悬浮窗.md) - 移动端使用
- [成功运行指南.md](成功运行指南.md) - 完整使用指南

---

**现在就试试吧！** 🚀

1. 双击 `诊断环境.bat`
2. 双击 `启动服务_简单版.bat`
3. 浏览器打开 `http://localhost:5000`

有问题告诉我具体的错误信息！
