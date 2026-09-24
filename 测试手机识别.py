#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试手机屏幕截图和识别功能"""

import subprocess
import sys
from pathlib import Path

def check_adb():
    """检查 ADB 是否可用"""
    try:
        result = subprocess.run(
            ['adb', 'version'],
            capture_output=True,
            text=True,
            timeout=5
        )
        if result.returncode == 0:
            print("✓ ADB 已安装")
            return True
        else:
            print("✗ ADB 未安装或不可用")
            return False
    except FileNotFoundError:
        print("✗ ADB 未找到，请先安装 ADB 工具")
        return False
    except Exception as e:
        print(f"✗ ADB 检查失败: {e}")
        return False

def check_devices():
    """检查连接的设备"""
    try:
        result = subprocess.run(
            ['adb', 'devices'],
            capture_output=True,
            text=True,
            timeout=5
        )
        lines = result.stdout.strip().split('\n')
        devices = [line for line in lines[1:] if '\tdevice' in line]

        if devices:
            print(f"✓ 检测到 {len(devices)} 个设备")
            for device in devices:
                print(f"  - {device.split()[0]}")
            return True
        else:
            print("✗ 未检测到设备")
            print("\n请确保:")
            print("  1. 手机已通过 USB 连接")
            print("  2. 手机已开启 USB 调试")
            print("  3. 手机已授权 USB 调试")
            return False
    except Exception as e:
        print(f"✗ 设备检查失败: {e}")
        return False

def test_screenshot():
    """测试截图功能"""
    print("\n测试截图功能...")
    try:
        # 执行截图命令
        result = subprocess.run(
            ['adb', 'exec-out', 'screencap', '-p'],
            capture_output=True,
            timeout=10
        )

        if result.returncode == 0 and len(result.stdout) > 1000:
            # 保存截图
            screenshot_path = Path('test_screenshot.png')
            screenshot_path.write_bytes(result.stdout)
            print(f"✓ 截图成功，已保存到: {screenshot_path}")
            print(f"  文件大小: {len(result.stdout) / 1024:.1f} KB")

            # 检查是否安装了图像库
            try:
                from PIL import Image
                img = Image.open(screenshot_path)
                print(f"  分辨率: {img.width}x{img.height}")
                return True
            except ImportError:
                print("  (无法显示图像信息，PIL 未安装)")
                return True
        else:
            print("✗ 截图失败")
            return False

    except subprocess.TimeoutExpired:
        print("✗ 截图超时")
        return False
    except Exception as e:
        print(f"✗ 截图失败: {e}")
        return False

def test_input():
    """测试输入功能（点击屏幕中心）"""
    print("\n测试输入功能...")
    response = input("是否测试点击功能？这会在手机屏幕中心点击一次 (y/n): ")
    if response.lower() != 'y':
        print("跳过输入测试")
        return True

    try:
        # 获取屏幕尺寸
        result = subprocess.run(
            ['adb', 'shell', 'wm', 'size'],
            capture_output=True,
            text=True,
            timeout=5
        )

        if result.returncode == 0:
            # 解析尺寸
            size_line = result.stdout.strip().split('\n')[-1]
            if 'Physical size:' in size_line:
                size = size_line.split(': ')[1]
                width, height = map(int, size.split('x'))
                center_x, center_y = width // 2, height // 2

                print(f"  屏幕尺寸: {width}x{height}")
                print(f"  点击位置: ({center_x}, {center_y})")

                # 执行点击
                result = subprocess.run(
                    ['adb', 'shell', 'input', 'tap', str(center_x), str(center_y)],
                    capture_output=True,
                    timeout=5
                )

                if result.returncode == 0:
                    print("✓ 点击成功")
                    return True
                else:
                    print("✗ 点击失败")
                    return False

        print("✗ 无法获取屏幕尺寸")
        return False

    except Exception as e:
        print(f"✗ 输入测试失败: {e}")
        return False

def test_recognition_deps():
    """检查识别功能依赖"""
    print("\n检查识别依赖库...")

    deps = {
        'numpy': 'NumPy',
        'cv2': 'OpenCV (opencv-python)',
        'PIL': 'Pillow'
    }

    all_ok = True
    for module, name in deps.items():
        try:
            __import__(module)
            print(f"✓ {name} 已安装")
        except ImportError:
            print(f"✗ {name} 未安装")
            all_ok = False

    if not all_ok:
        print("\n安装缺失的依赖:")
        print("  pip install numpy opencv-python Pillow")

    return all_ok

def main():
    print("=" * 60)
    print("JJ 象棋识别功能测试")
    print("=" * 60)
    print()

    # 1. 检查 ADB
    print("[1/5] 检查 ADB 工具...")
    if not check_adb():
        print("\n❌ ADB 未安装，无法继续")
        sys.exit(1)

    # 2. 检查设备
    print("\n[2/5] 检查连接的设备...")
    if not check_devices():
        print("\n❌ 未检测到设备，请连接手机并开启 USB 调试")
        sys.exit(1)

    # 3. 测试截图
    print("\n[3/5] 测试截图功能...")
    if not test_screenshot():
        print("\n❌ 截图功能异常")
        sys.exit(1)

    # 4. 测试输入
    print("\n[4/5] 测试输入功能...")
    test_input()

    # 5. 检查依赖
    print("\n[5/5] 检查识别依赖...")
    deps_ok = test_recognition_deps()

    print("\n" + "=" * 60)
    if deps_ok:
        print("✅ 所有测试通过！")
        print("=" * 60)
        print("\n下一步:")
        print("  1. 在手机上打开 JJ 象棋 App")
        print("  2. 进入对局界面")
        print("  3. 在 Web 界面切换到 '识别助手' Tab")
        print("  4. 点击 '启动 JJ 识别' 按钮")
        print("  5. 按提示进行四角校准")
    else:
        print("⚠️ 测试部分通过，但缺少识别依赖")
        print("=" * 60)
        print("\n请先安装依赖:")
        print("  pip install numpy opencv-python Pillow")
    print()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n测试已取消")
        sys.exit(0)
    except Exception as e:
        print(f"\n\n错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
