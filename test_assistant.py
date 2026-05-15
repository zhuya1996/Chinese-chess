#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试助手是否能正常工作"""

import sys
from pathlib import Path

# 添加当前目录到路径
sys.path.insert(0, str(Path(__file__).parent))

from chess_assistant import ChessAssistant

def test_assistant():
    """测试助手基本功能"""
    print("="*60)
    print("测试中国象棋 AI 助手")
    print("="*60 + "\n")
    
    try:
        # 创建助手
        print("1. 创建助手实例...")
        assistant = ChessAssistant()
        print("   ✓ 成功\n")
        
        # 启动引擎
        print("2. 启动引擎...")
        if not assistant.start_engine():
            print("   ✗ 失败")
            return False
        print("   ✓ 成功\n")
        
        # 设置初始局面
        print("3. 设置初始局面...")
        assistant.set_position()
        print("   ✓ 成功\n")
        
        # 快速分析
        print("4. 进行快速分析（深度10）...")
        result = assistant.analyze(depth=10)
        
        if result and result['best_move']:
            print(f"   ✓ 分析成功")
            print(f"   最佳着法: {result['best_move']}")
            print(f"   评估值: {result['score']}")
            print(f"   搜索深度: {result['depth']}")
            print(f"   搜索节点: {result['nodes']:,}\n")
        else:
            print("   ✗ 分析失败\n")
            return False
        
        # 停止引擎
        print("5. 停止引擎...")
        assistant.stop_engine()
        print("   ✓ 成功\n")
        
        print("="*60)
        print("✓ 所有测试通过！助手工作正常。")
        print("="*60)
        return True
        
    except Exception as e:
        print(f"\n✗ 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_assistant()
    sys.exit(0 if success else 1)
