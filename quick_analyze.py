#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速分析工具 - 用于实时对弈辅助
在对弈过程中快速获取AI建议
"""

import sys
from chess_assistant import ChessAssistant

def quick_analyze(fen=None, depth=12):
    """快速分析并给出建议
    
    Args:
        fen: 当前局面的FEN字符串（可选）
        depth: 搜索深度（默认12，速度较快）
    """
    print("\n" + "="*60)
    print("     快速分析工具")
    print("="*60 + "\n")
    
    assistant = ChessAssistant()
    
    if not assistant.start_engine():
        print("无法启动引擎")
        return
    
    try:
        # 设置局面
        if fen:
            assistant.set_position(fen=fen)
            print(f"✓ 已设置局面: {fen[:50]}...")
        else:
            assistant.set_position()
            print("✓ 使用初始局面")
        
        # 快速分析
        print(f"\n正在快速分析（深度 {depth}）...\n")
        assistant.get_suggestion(depth=depth)
        
    finally:
        assistant.stop_engine()


if __name__ == "__main__":
    if len(sys.argv) > 1:
        # 从命令行参数获取FEN
        fen = sys.argv[1]
        depth = int(sys.argv[2]) if len(sys.argv) > 2 else 12
        quick_analyze(fen, depth)
    else:
        # 交互式输入
        print("\n快速分析工具")
        print("="*60)
        print("\n请输入当前局面的FEN字符串（直接回车使用初始局面）:")
        fen = input("FEN: ").strip()
        
        print("\n请输入搜索深度（直接回车使用默认值12）:")
        depth_input = input("深度: ").strip()
        depth = int(depth_input) if depth_input.isdigit() else 12
        
        quick_analyze(fen if fen else None, depth)
