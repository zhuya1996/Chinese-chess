#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""简单测试引擎是否可用"""

import subprocess
import sys
from pathlib import Path

def test_engine():
    engine_path = Path("src/pikafish.exe")
    nnue_path = Path("src/pikafish.nnue")

    print("=" * 60)
    print("Pikafish 引擎测试")
    print("=" * 60)
    print()

    # 检查文件存在
    print("[1/4] 检查引擎文件...")
    if not engine_path.exists():
        print(f"错误: 找不到 {engine_path}")
        return False
    print(f"OK - {engine_path} 存在 ({engine_path.stat().st_size / 1024 / 1024:.1f} MB)")

    print("\n[2/4] 检查神经网络模型...")
    if not nnue_path.exists():
        print(f"警告: 找不到 {nnue_path}")
    else:
        print(f"OK - {nnue_path} 存在 ({nnue_path.stat().st_size / 1024 / 1024:.1f} MB)")

    # 测试引擎启动
    print("\n[3/4] 测试引擎启动...")
    try:
        proc = subprocess.Popen(
            [str(engine_path.absolute())],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=str(engine_path.parent.absolute()),
            shell=False
        )

        # 发送 uci 命令
        proc.stdin.write("uci\n")
        proc.stdin.flush()

        # 读取响应
        output_lines = []
        while True:
            line = proc.stdout.readline().strip()
            output_lines.append(line)
            if line == "uciok":
                break
            if len(output_lines) > 100:  # 防止无限循环
                break

        if "uciok" in output_lines:
            print("OK - 引擎成功响应 UCI 协议")
        else:
            print("错误: 引擎未正确响应")
            return False

        # 测试简单分析
        print("\n[4/4] 测试分析功能...")
        proc.stdin.write("isready\n")
        proc.stdin.flush()

        while True:
            line = proc.stdout.readline().strip()
            if line == "readyok":
                break

        proc.stdin.write("position startpos\n")
        proc.stdin.flush()
        proc.stdin.write("go depth 5\n")
        proc.stdin.flush()

        best_move = None
        while True:
            line = proc.stdout.readline().strip()
            if line.startswith("bestmove"):
                best_move = line.split()[1] if len(line.split()) > 1 else None
                break

        if best_move:
            print(f"OK - 引擎分析成功，建议着法: {best_move}")
        else:
            print("错误: 引擎未返回着法")
            return False

        # 退出引擎
        proc.stdin.write("quit\n")
        proc.stdin.flush()
        proc.wait(timeout=5)

        print("\n" + "=" * 60)
        print("测试成功！引擎工作正常")
        print("=" * 60)
        print("\n现在可以运行: python web_assistant.py")
        return True

    except Exception as e:
        print(f"\n错误: {e}")
        return False

if __name__ == "__main__":
    success = test_engine()
    sys.exit(0 if success else 1)
