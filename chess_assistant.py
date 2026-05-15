#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
中国象棋 AI 助手
提供简单的交互界面，帮助你分析局面和获取最佳着法建议
"""

import subprocess
import sys
import os
import re
from pathlib import Path

class ChessAssistant:
    def __init__(self, engine_path=None):
        """初始化象棋助手"""
        if engine_path is None:
            # 默认引擎路径
            engine_path = Path(__file__).parent / "src" / "pikafish.exe"
        
        self.engine_path = Path(engine_path)
        if not self.engine_path.exists():
            raise FileNotFoundError(f"引擎文件不存在: {self.engine_path}")
        
        self.engine = None
        self.is_ready = False
        
    def start_engine(self):
        """启动引擎"""
        try:
            self.engine = subprocess.Popen(
                [str(self.engine_path)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                cwd=str(self.engine_path.parent)
            )
            
            # 发送 UCI 命令
            self._send_command("uci")
            
            # 等待 uciok
            while True:
                line = self.engine.stdout.readline().strip()
                if line == "uciok":
                    break
            
            # 检查引擎是否就绪
            self._send_command("isready")
            while True:
                line = self.engine.stdout.readline().strip()
                if line == "readyok":
                    self.is_ready = True
                    break
            
            print("✓ 引擎已启动并就绪")
            return True
            
        except Exception as e:
            print(f"✗ 启动引擎失败: {e}")
            return False
    
    def _send_command(self, command):
        """发送命令到引擎"""
        if self.engine:
            self.engine.stdin.write(command + "\n")
            self.engine.stdin.flush()
    
    def set_position(self, fen=None, moves=None):
        """设置棋盘局面
        
        Args:
            fen: FEN格式的局面字符串，None表示初始局面
            moves: 着法列表，例如 ['e2e4', 'e7e5']
        """
        if fen is None:
            cmd = "position startpos"
        else:
            cmd = f"position fen {fen}"
        
        if moves:
            cmd += " moves " + " ".join(moves)
        
        self._send_command(cmd)
        print(f"✓ 已设置局面")
    
    def analyze(self, depth=15, time_ms=None):
        """分析当前局面
        
        Args:
            depth: 搜索深度（默认15）
            time_ms: 思考时间（毫秒），如果设置则忽略depth
            
        Returns:
            dict: 包含最佳着法、评估值等信息
        """
        if not self.is_ready:
            print("✗ 引擎未就绪")
            return None
        
        # 发送搜索命令
        if time_ms:
            self._send_command(f"go movetime {time_ms}")
        else:
            self._send_command(f"go depth {depth}")
        
        best_move = None
        ponder = None
        score = None
        pv = []
        nodes = 0
        nps = 0
        depth_reached = 0
        
        print(f"\n正在分析（深度 {depth}）...")
        print("-" * 60)
        
        # 读取引擎输出
        while True:
            line = self.engine.stdout.readline().strip()
            
            if line.startswith("info"):
                # 解析信息行
                if "depth" in line:
                    match = re.search(r'depth (\d+)', line)
                    if match:
                        depth_reached = int(match.group(1))
                
                if "score cp" in line:
                    match = re.search(r'score cp (-?\d+)', line)
                    if match:
                        score = int(match.group(1))
                
                if "score mate" in line:
                    match = re.search(r'score mate (-?\d+)', line)
                    if match:
                        mate_in = int(match.group(1))
                        score = f"杀棋 {mate_in}"
                
                if "nodes" in line:
                    match = re.search(r'nodes (\d+)', line)
                    if match:
                        nodes = int(match.group(1))
                
                if "nps" in line:
                    match = re.search(r'nps (\d+)', line)
                    if match:
                        nps = int(match.group(1))
                
                if "pv" in line:
                    match = re.search(r'pv (.+)$', line)
                    if match:
                        pv = match.group(1).split()
                        # 显示进度
                        if depth_reached > 0:
                            score_str = f"{score:+5d}" if isinstance(score, int) else str(score)
                            print(f"深度 {depth_reached:2d} | 评分: {score_str:>10s} | 主变例: {' '.join(pv[:5])}")
            
            elif line.startswith("bestmove"):
                # 解析最佳着法
                parts = line.split()
                best_move = parts[1] if len(parts) > 1 else None
                ponder = parts[3] if len(parts) > 3 and parts[2] == "ponder" else None
                break
        
        print("-" * 60)
        
        result = {
            'best_move': best_move,
            'ponder': ponder,
            'score': score,
            'depth': depth_reached,
            'nodes': nodes,
            'nps': nps,
            'pv': pv
        }
        
        return result
    
    def get_suggestion(self, depth=15):
        """获取着法建议（简化版）
        
        Returns:
            str: 最佳着法的描述
        """
        result = self.analyze(depth=depth)
        
        if result and result['best_move']:
            move = result['best_move']
            score = result['score']
            
            # 转换着法为中文描述
            from_sq = move[:2]
            to_sq = move[2:4]
            
            score_desc = ""
            if isinstance(score, int):
                if score > 0:
                    score_desc = f"（优势 +{score/100:.2f}）"
                elif score < 0:
                    score_desc = f"（劣势 {score/100:.2f}）"
                else:
                    score_desc = "（均势）"
            else:
                score_desc = f"（{score}）"
            
            print(f"\n💡 建议着法: {move} {score_desc}")
            print(f"   从 {from_sq} 到 {to_sq}")
            
            if result['pv'] and len(result['pv']) > 1:
                print(f"   预期变化: {' '.join(result['pv'][:5])}")
            
            print(f"   搜索节点: {result['nodes']:,}")
            print(f"   搜索速度: {result['nps']:,} 节点/秒")
            
            return move
        
        return None
    
    def stop_engine(self):
        """停止引擎"""
        if self.engine:
            self._send_command("quit")
            self.engine.wait(timeout=5)
            self.engine = None
            print("\n✓ 引擎已停止")
    
    def interactive_mode(self):
        """交互模式"""
        print("\n" + "="*60)
        print("     中国象棋 AI 助手 - 交互模式")
        print("="*60)
        print("\n可用命令:")
        print("  start          - 设置初始局面")
        print("  fen <FEN>      - 设置指定FEN局面")
        print("  move <着法>    - 走一步棋（如 e2e4）")
        print("  analyze [深度] - 分析当前局面（默认深度15）")
        print("  suggest        - 获取着法建议")
        print("  help           - 显示帮助")
        print("  quit           - 退出")
        print("\n" + "="*60 + "\n")
        
        moves = []
        current_fen = None
        
        while True:
            try:
                cmd = input("\n👉 请输入命令: ").strip().lower()
                
                if not cmd:
                    continue
                
                parts = cmd.split(maxsplit=1)
                command = parts[0]
                args = parts[1] if len(parts) > 1 else ""
                
                if command == "quit" or command == "exit":
                    break
                
                elif command == "start":
                    moves = []
                    current_fen = None
                    self.set_position()
                    print("✓ 已设置为初始局面")
                
                elif command == "fen":
                    if args:
                        current_fen = args
                        moves = []
                        self.set_position(fen=current_fen)
                    else:
                        print("✗ 请提供FEN字符串")
                
                elif command == "move":
                    if args:
                        moves.append(args)
                        self.set_position(fen=current_fen, moves=moves)
                        print(f"✓ 已走棋: {args}")
                    else:
                        print("✗ 请提供着法（如 e2e4）")
                
                elif command == "analyze":
                    depth = int(args) if args.isdigit() else 15
                    self.analyze(depth=depth)
                
                elif command == "suggest":
                    self.get_suggestion(depth=15)
                
                elif command == "help":
                    print("\n可用命令:")
                    print("  start          - 设置初始局面")
                    print("  fen <FEN>      - 设置指定FEN局面")
                    print("  move <着法>    - 走一步棋（如 e2e4）")
                    print("  analyze [深度] - 分析当前局面（默认深度15）")
                    print("  suggest        - 获取着法建议")
                    print("  help           - 显示帮助")
                    print("  quit           - 退出")
                
                else:
                    print(f"✗ 未知命令: {command}，输入 'help' 查看帮助")
            
            except KeyboardInterrupt:
                print("\n\n正在退出...")
                break
            except Exception as e:
                print(f"✗ 错误: {e}")


def main():
    """主函数"""
    print("\n" + "="*60)
    print("     中国象棋 AI 助手")
    print("     基于 Pikafish 引擎")
    print("="*60)
    
    # 创建助手实例
    assistant = ChessAssistant()
    
    # 启动引擎
    if not assistant.start_engine():
        print("\n无法启动引擎，请检查 pikafish.exe 是否存在")
        return
    
    try:
        # 进入交互模式
        assistant.interactive_mode()
    finally:
        # 停止引擎
        assistant.stop_engine()


if __name__ == "__main__":
    main()
