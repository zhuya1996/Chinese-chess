# 🤖 GitHub 自动构建 APK 设置指南

## 一次设置，永久使用！用户无需 Android 开发环境

---

## 📋 目录

1. [前置准备](#前置准备)
2. [GitHub 仓库设置](#github-仓库设置)
3. [首次发布](#首次发布)
4. [验证构建](#验证构建)
5. [分享给用户](#分享给用户)
6. [后续更新](#后续更新)
7. [故障排查](#故障排查)

---

## 🎯 前置准备

### 需要准备的账号和工具

1. **GitHub 账号**（免费）
   - 访问 https://github.com 注册
   - 验证邮箱

2. **Git 工具**（如果没有）
   ```bash
   # 下载安装
   https://git-scm.com/download/win
   
   # 验证安装
   git --version
   ```

3. **配置 Git 身份**
   ```bash
   git config --global user.name "你的名字"
   git config --global user.email "你的邮箱"
   ```

---

## 🏗️ GitHub 仓库设置

### 第一步：创建 GitHub 仓库

1. **访问 GitHub**
   - 登录 https://github.com
   - 点击右上角 `+` → `New repository`

2. **填写仓库信息**
   ```
   Repository name: Chinese-chess
   Description: 中国象棋 AI 助手（带悬浮窗）
   Public: ✅ 勾选（免费构建）
   Add a README: ❌ 不勾选（我们已有文件）
   ```

3. **创建仓库**
   - 点击 `Create repository`
   - 记下仓库地址（如 `https://github.com/你的用户名/Chinese-chess.git`）

### 第二步：连接本地项目

```bash
# 1. 进入项目目录
cd E:\app\Chinese-chess

# 2. 初始化 Git（如果未初始化）
git init

# 3. 添加远程仓库
git remote add origin https://github.com/你的用户名/Chinese-chess.git

# 4. 验证连接
git remote -v
```

### 第三步：推送代码

```bash
# 1. 添加所有文件
git add .

# 2. 提交
git commit -m "初始提交：中国象棋 AI 助手"

# 3. 推送到 GitHub
git push -u origin master
```

如果推送失败（需要身份验证）：

**方法一：使用 Personal Access Token（推荐）**

1. GitHub 头像 → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token → 勾选 `repo` → Generate token
3. 复制生成的 token（只显示一次！）
4. 再次推送时：
   ```
   Username: 你的用户名
   Password: 粘贴刚才的 token
   ```

**方法二：使用 SSH**

```bash
# 1. 生成 SSH 密钥
ssh-keygen -t ed25519 -C "你的邮箱"

# 2. 复制公钥
cat ~/.ssh/id_ed25519.pub

# 3. 添加到 GitHub
# Settings → SSH and GPG keys → New SSH key

# 4. 更改远程地址
git remote set-url origin git@github.com:你的用户名/Chinese-chess.git
```

---

## 🚀 首次发布

### 方法一：使用脚本（推荐）

```bash
# 双击运行
发布到GitHub并自动构建APK.bat

# 按提示操作：
# 1. 输入提交信息（可留空）
# 2. 输入版本号（如 v1.0.0）
# 3. 等待完成
```

### 方法二：手动命令

```bash
# 1. 确保代码已推送
git push origin master

# 2. 创建版本标签
git tag -a v1.0.0 -m "首次发布"

# 3. 推送标签（触发构建）
git push origin v1.0.0
```

**重要**：推送标签后，GitHub Actions 会自动开始构建！

---

## ✅ 验证构建

### 第一步：查看 Actions

1. **访问 Actions 页面**
   ```
   https://github.com/你的用户名/Chinese-chess/actions
   ```

2. **查看构建状态**
   - 🟡 黄色圆圈 = 正在构建
   - ✅ 绿色勾 = 构建成功
   - ❌ 红色叉 = 构建失败

3. **点击进入详情**
   - 可查看每个步骤的日志
   - 构建时间约 5-10 分钟

### 第二步：检查 Releases

1. **访问 Releases 页面**
   ```
   https://github.com/你的用户名/Chinese-chess/releases
   ```

2. **确认文件**
   - `app-debug.apk` （~10MB）
   - `app-release-unsigned.apk` （~5MB）
   - 版本说明（自动生成）

3. **测试下载**
   - 点击 APK 文件下载
   - 检查文件大小是否正常

---

## 📤 分享给用户

### 创建下载链接

**固定版本链接**：
```
https://github.com/你的用户名/Chinese-chess/releases/tag/v1.0.0
```

**最新版本链接**（推荐）：
```
https://github.com/你的用户名/Chinese-chess/releases/latest
```

**直接下载链接**：
```
https://github.com/你的用户名/Chinese-chess/releases/download/v1.0.0/app-debug.apk
```

### 生成二维码

1. 访问 https://cli.im/url （或其他二维码生成网站）
2. 输入下载链接
3. 生成二维码图片
4. 用户扫码直接下载

### 宣传文案示例

```
📱 中国象棋 AI 助手 - 悬浮窗版

🚀 特点：
✅ 无需 USB 连接
✅ 实时 AI 建议
✅ 悬浮窗显示
✅ 自由拖动

📥 下载：
https://github.com/你的用户名/Chinese-chess/releases/latest

💡 要求：
- Android 7.0+
- 手机和电脑同一 WiFi
- 允许悬浮窗权限
```

---

## 🔄 后续更新

### 发布新版本

```bash
# 1. 修改代码后提交
git add .
git commit -m "修复Bug / 添加功能"
git push origin master

# 2. 创建新标签
git tag -a v1.0.1 -m "修复悬浮窗显示Bug"
git push origin v1.0.1

# 3. 等待自动构建
# 新版本会出现在 Releases 页面
```

### 版本号规范

- `v1.0.0` - 主版本.次版本.修订号
- `v1.0.1` - Bug 修复
- `v1.1.0` - 新功能
- `v2.0.0` - 重大更新

### 自动构建触发条件

GitHub Actions 会在以下情况自动构建：

1. ✅ 推送 `v*` 标签（如 `v1.0.0`）→ 发布到 Releases
2. ✅ 推送到 `master` 分支 → 只构建，不发布
3. ✅ 创建 Pull Request → 构建测试
4. ✅ 手动触发（Actions 页面）→ 按需构建

---

## 🐛 故障排查

### 问题 1：Actions 页面没有构建

**可能原因**：
- Actions 功能未启用
- 工作流文件位置错误

**解决方法**：
```bash
# 1. 检查文件是否存在
ls .github/workflows/build-android.yml

# 2. 确保文件已推送
git log --oneline | head -5

# 3. 启用 Actions
# 仓库 Settings → Actions → General → Allow all actions
```

### 问题 2：构建失败（红色 ❌）

**查看错误日志**：
1. 点击失败的构建
2. 展开红色步骤
3. 查看错误信息

**常见错误及解决**：

#### 错误：`gradlew: Permission denied`
```yaml
# 已在配置中修复：
- name: Grant execute permission for gradlew
  run: chmod +x android_app/gradlew
```

#### 错误：`Gradle sync failed`
**原因**：Gradle 配置错误
**解决**：检查 `android_app/build.gradle` 和 `app/build.gradle`

#### 错误：`SDK location not found`
**原因**：缺少 Android SDK（GitHub Actions 会自动处理）
**解决**：检查 workflow 文件中的 `setup-java` 步骤

#### 错误：`Task 'assembleDebug' not found`
**原因**：项目结构问题
**解决**：
```bash
cd android_app
./gradlew tasks --all  # 本地检查可用任务
```

### 问题 3：APK 未出现在 Releases

**检查清单**：
- ✅ 是否推送了 `v*` 标签？
  ```bash
  git tag -l
  git push origin v1.0.0
  ```
- ✅ Actions 是否成功（绿色 ✅）？
- ✅ 是否有 `GITHUB_TOKEN` 权限？
  - Settings → Actions → General → Workflow permissions
  - 选择 `Read and write permissions`

### 问题 4：国内下载太慢

**解决方案**：

1. **使用镜像加速**
   ```
   原链接：
   https://github.com/.../app-debug.apk
   
   镜像链接：
   https://ghproxy.com/https://github.com/.../app-debug.apk
   ```

2. **使用 Gitee 同步**
   - 在 Gitee 导入 GitHub 仓库
   - 开启 Gitee Pages
   - 手动上传 APK 到 Gitee Releases

3. **使用国内云存储**
   - 下载 APK 后上传到：
     - 阿里云 OSS
     - 腾讯云 COS
     - 百度网盘

### 问题 5：每次构建都很慢

**优化方法**：

1. **启用 Gradle 缓存**（已配置）
   ```yaml
   - uses: actions/cache@v3
     with:
       path: |
         ~/.gradle/caches
         ~/.gradle/wrapper
   ```

2. **减少构建频率**
   - 只在标签推送时构建
   - 合并多个提交后再发布

3. **使用自托管 Runner**（高级）
   - 在自己的服务器运行构建
   - 适合频繁发布的场景

---

## 📊 构建统计

### 典型构建时间
- **首次构建**：8-12 分钟（下载依赖）
- **后续构建**：5-8 分钟（有缓存）
- **增量构建**：3-5 分钟（代码变动少）

### 资源消耗
- **存储空间**：每个 APK 约 10MB
- **GitHub Actions 配额**：
  - 公开仓库：无限制
  - 私有仓库：2000 分钟/月（免费账户）

---

## 🎓 进阶技巧

### 1. 添加构建徽章

在 `README.md` 中添加：

```markdown
![Build Status](https://github.com/你的用户名/Chinese-chess/actions/workflows/build-android.yml/badge.svg)
```

效果：![Build Status](https://img.shields.io/badge/build-passing-brightgreen)

### 2. 自动更新版本号

创建脚本自动递增版本号：

```bash
# get_next_version.sh
#!/bin/bash
LATEST_TAG=$(git describe --tags --abbrev=0)
NEXT_VERSION=$(echo $LATEST_TAG | awk -F. '{$NF = $NF + 1;} 1' | sed 's/ /./g')
echo $NEXT_VERSION
```

### 3. 多渠道打包

修改 `app/build.gradle`：

```gradle
android {
    flavorDimensions "version"
    productFlavors {
        free {
            dimension "version"
            applicationIdSuffix ".free"
        }
        pro {
            dimension "version"
            applicationIdSuffix ".pro"
        }
    }
}
```

### 4. 签名 APK（发布版）

1. **生成密钥库**：
   ```bash
   keytool -genkey -v -keystore my-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias my-key-alias
   ```

2. **添加到 GitHub Secrets**：
   - Settings → Secrets → New repository secret
   - `KEYSTORE_FILE`: Base64 编码的密钥库
   - `KEYSTORE_PASSWORD`: 密钥库密码
   - `KEY_ALIAS`: 密钥别名
   - `KEY_PASSWORD`: 密钥密码

3. **修改 workflow**：
   ```yaml
   - name: Sign APK
     run: |
       echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > my-release-key.jks
       jarsigner -verbose -sigalg SHA256withRSA \
         -digestalg SHA-256 -keystore my-release-key.jks \
         -storepass "${{ secrets.KEYSTORE_PASSWORD }}" \
         app-release-unsigned.apk my-key-alias
   ```

---

## ✅ 完成检查清单

设置完成后，确认以下项目：

- [ ] GitHub 仓库已创建
- [ ] 代码已推送到 master 分支
- [ ] `.github/workflows/build-android.yml` 文件已上传
- [ ] 首个标签已推送（如 `v1.0.0`）
- [ ] Actions 构建成功（绿色 ✅）
- [ ] Releases 页面有 APK 文件
- [ ] APK 可以正常下载
- [ ] 在手机上测试安装成功
- [ ] 功能测试通过

---

## 🎉 总结

### 设置流程
```
创建 GitHub 仓库
    ↓
推送项目代码
    ↓
推送版本标签（v1.0.0）
    ↓
GitHub Actions 自动构建
    ↓
APK 发布到 Releases
    ↓
用户直接下载使用
```

### 优势
- ✅ 用户无需 Android Studio
- ✅ 用户无需 Gradle
- ✅ 用户无需 JDK
- ✅ 只需浏览器下载 APK
- ✅ 自动化构建，节省时间
- ✅ 版本管理清晰

### 时间投入
- **初次设置**：20-30 分钟
- **后续发布**：2-3 分钟（打标签）
- **用户下载**：1-2 分钟

---

## 📞 获取帮助

如果遇到问题：

1. **查看日志**：Actions → 点击失败的构建 → 查看详细日志
2. **搜索错误**：复制错误信息到 Google/百度
3. **提问**：在项目 Issues 提问
4. **社区**：访问 GitHub Community 寻求帮助

---

**设置一次，受益永久！用户会感谢你的！** 🎊
