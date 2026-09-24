#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试 API 调用"""

import requests
import json
import time

BASE_URL = "http://localhost:5000"

def test_api():
    print("=" * 60)
    print("测试 API")
    print("=" * 60)

    # 1. 检查状态
    print("\n[1/5] 检查服务状态...")
    resp = requests.get(f"{BASE_URL}/api/status")
    print(f"响应: {resp.json()}")

    # 2. 启动引擎
    print("\n[2/5] 启动引擎...")
    resp = requests.post(f"{BASE_URL}/api/start", json={})
    result = resp.json()
    print(f"响应: {result}")

    if not result.get('success'):
        print(f"\n错误: 引擎启动失败")
        print(f"详细: {result}")
        return False

    # 等待引擎就绪
    print("\n[3/5] 等待引擎就绪...")
    time.sleep(2)

    resp = requests.get(f"{BASE_URL}/api/status")
    status = resp.json()
    print(f"状态: {status}")

    if not status.get('is_ready'):
        print("\n警告: 引擎未就绪")
        return False

    # 3. 获取建议
    print("\n[4/5] 获取着法建议...")
    resp = requests.post(f"{BASE_URL}/api/suggest", json={"depth": 10})
    result = resp.json()
    print(f"响应: {result}")

    if result.get('success') and result.get('best_move'):
        print(f"最佳着法: {result['best_move']}")
        print(f"评估: {result['score']}")
        print(f"主要变化: {result.get('pv', [])}")
    else:
        print("获取建议失败")
        return False

    # 4. 走棋
    print("\n[5/5] 执行着法...")
    move = result['best_move']
    resp = requests.post(f"{BASE_URL}/api/move", json={"move": move})
    result = resp.json()
    print(f"响应: {result}")

    print("\n" + "=" * 60)
    print("API 测试成功！")
    print("=" * 60)
    return True

if __name__ == "__main__":
    try:
        test_api()
    except Exception as e:
        print(f"\n错误: {e}")
        import traceback
        traceback.print_exc()
