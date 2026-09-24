#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试引擎路径"""

import subprocess
import sys
from pathlib import Path

# 模拟 web_assistant.py 中的代码
engine_path = Path(__file__).parent / "src" / "pikafish.exe"

print(f"当前工作目录: {Path.cwd()}")
print(f"脚本目录: {Path(__file__).parent}")
print(f"引擎路径: {engine_path}")
print(f"引擎绝对路径: {engine_path.absolute()}")
print(f"引擎存在: {engine_path.exists()}")
print()

if not engine_path.exists():
    print("错误: 引擎文件不存在")
    sys.exit(1)

print("测试方式 1: 相对路径")
try:
    proc = subprocess.Popen(
        [str(engine_path)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(engine_path.parent)
    )
    proc.stdin.write("quit\n")
    proc.stdin.flush()
    proc.wait(timeout=2)
    print("✓ 相对路径成功")
except Exception as e:
    print(f"✗ 相对路径失败: {e}")

print("\n测试方式 2: 绝对路径")
try:
    proc = subprocess.Popen(
        [str(engine_path.absolute())],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(engine_path.parent.absolute())
    )
    proc.stdin.write("quit\n")
    proc.stdin.flush()
    proc.wait(timeout=2)
    print("✓ 绝对路径成功")
except Exception as e:
    print(f"✗ 绝对路径失败: {e}")

print("\n测试方式 3: 完整命令")
try:
    full_path = str(engine_path.absolute()).replace("/", "\\")
    proc = subprocess.Popen(
        [full_path],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(engine_path.parent.absolute())
    )
    proc.stdin.write("quit\n")
    proc.stdin.flush()
    proc.wait(timeout=2)
    print("✓ Windows 路径成功")
except Exception as e:
    print(f"✗ Windows 路径失败: {e}")
