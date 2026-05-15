#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
象棋引擎参数优化器
自动优化Pikafish引擎参数以提升对弈能力
"""

import subprocess
import time
from pathlib import Path

class EngineOptimizer:
    """引擎参数优化器"""

    def __init__(self, engine_path=None):
        self.engine_path = Path(engine_path) if engine_path else Path(__file__).parent / "src" / "pikafish.exe"
        self.recommended_settings = {}

    def get_optimal_settings(self, hardware_level="medium"):
        """根据硬件水平获取最优参数设置

        Args:
            hardware_level: 硬件水平 "low", "medium", "high", "ultra"

        Returns:
            dict: 最优参数设置
        """

        settings = {
            "low": {      # 4GB内存, 2核CPU
                "Threads": 2,
                "Hash": 256,        # MB
                "MultiPV": 1,
                "Slow Mover": 80,
                "Contempt": 20,
                "Skill Level": 10
            },
            "medium": {   # 8GB内存, 4核CPU
                "Threads": 4,
                "Hash": 1024,       # MB
                "MultiPV": 2,
                "Slow Mover": 84,
                "Contempt": 24,
                "Skill Level": 15
            },
            "high": {     # 16GB内存, 8核CPU
                "Threads": 8,
                "Hash": 2048,       # MB
                "MultiPV": 3,
                "Slow Mover": 89,
                "Contempt": 28,
                "Skill Level": 18
            },
            "ultra": {    # 32GB+内存, 16核+CPU
                "Threads": 16,
                "Hash": 4096,       # MB
                "MultiPV": 5,
                "Slow Mover": 93,
                "Contempt": 32,
                "Skill Level": 20
            }
        }

        return settings.get(hardware_level, settings["medium"])

    def test_settings(self, settings, test_positions):
        """测试参数设置在特定局面上的表现"""
        # 这里可以实现自动测试逻辑
        pass

    def apply_settings(self, settings):
        """生成UCI命令来应用设置"""
        commands = []
        for option, value in settings.items():
            commands.append(f"setoption name {option} value {value}")
        return commands

    def create_optimized_config(self, hardware_level="medium"):
        """创建优化的配置文件"""
        settings = self.get_optimal_settings(hardware_level)

        config_content = f"""# 象棋引擎优化配置 - {hardware_level}硬件水平
# 生成时间: {time.strftime('%Y-%m-%d %H:%M:%S')}

[Engine]
Threads = {settings['Threads']}
Hash = {settings['Hash']}
MultiPV = {settings['MultiPV']}
Slow Mover = {settings['Slow Mover']}
Contempt = {settings['Contempt']}
Skill Level = {settings['Skill Level']}

# 说明:
# Threads: 使用的CPU线程数
# Hash: 哈希表大小(MB)，影响重复局面识别
# MultiPV: 多变化分析数量
# Slow Mover: 时间控制系数(100=标准)
# Contempt: 藐视值，影响策略选择
# Skill Level: 技能等级(0-20)
"""

        config_path = Path(__file__).parent / f"engine_config_{hardware_level}.txt"
        with open(config_path, 'w', encoding='utf-8') as f:
            f.write(config_content)

        return config_path

class AdvancedChessAssistant:
    """增强版象棋助手"""

    def __init__(self):
        from chess_assistant import ChessAssistant
        self.base_assistant = ChessAssistant()
        self.optimizer = EngineOptimizer()
        self.current_settings = {}

    def start_optimized_engine(self, hardware_level="medium"):
        """启动优化后的引擎"""
        # 获取最优设置
        optimal_settings = self.optimizer.get_optimal_settings(hardware_level)
        self.current_settings = optimal_settings

        # 启动基础引擎
        if not self.base_assistant.start_engine():
            return False

        # 应用优化设置
        commands = self.optimizer.apply_settings(optimal_settings)
        for cmd in commands:
            self.base_assistant._send_command(cmd)

        print(f"✅ 已应用优化设置 ({hardware_level}硬件水平):")
        for key, value in optimal_settings.items():
            print(f"   {key}: {value}")

        return True

    def deep_analyze(self, fen=None, depth=20, time_limit=30):
        """深度分析功能"""
        if fen:
            self.base_assistant.set_fen(fen)

        print(f"\n🔍 开始深度分析 (深度{depth}, 限时{time_limit}秒)...")
        start_time = time.time()

        # 使用更高级的分析参数
        result = self.base_assistant.analyze(depth=depth)

        elapsed = time.time() - start_time

        # 增强结果展示
        print(f"\n📊 分析结果 (用时 {elapsed:.1f}秒):")
        print(f"   最佳着法: {result['best_move']}")
        print(f"   评估值: {result['score']}")
        print(f"   搜索深度: {result['depth']}")
        print(f"   预期变化: {' → '.join(result['pv'][:5])}")

        return result

    def get_opening_suggestion(self, moves):
        """获取开局建议"""
        opening_moves = {
            # 常见开局应对
            "炮二平五": ["马八进七", "马二进三", "车一平二"],
            "马二进三": ["炮二平五", "相三进五", "马八进七"],
            "兵三进一": ["炮二平五", "马二进三", "相七进五"],
        }

        # 这里可以集成更复杂的开局库
        return opening_moves

def detect_hardware():
    """检测硬件水平"""
    import psutil

    cpu_count = psutil.cpu_count(logical=False)  # 物理核心数
    memory_gb = psutil.virtual_memory().total / (1024**3)  # GB

    if cpu_count <= 2 or memory_gb <= 4:
        return "low"
    elif cpu_count <= 4 or memory_gb <= 8:
        return "medium"
    elif cpu_count <= 8 or memory_gb <= 16:
        return "high"
    else:
        return "ultra"

def main():
    """演示优化功能"""
    print("🚀 象棋引擎参数优化器")
    print("=" * 50)

    # 检测硬件
    hardware_level = detect_hardware()
    print(f"📱 检测到硬件水平: {hardware_level}")

    # 创建优化器
    optimizer = EngineOptimizer()

    # 生成配置文件
    config_path = optimizer.create_optimized_config(hardware_level)
    print(f"✅ 已生成优化配置: {config_path}")

    # 显示推荐设置
    settings = optimizer.get_optimal_settings(hardware_level)
    print(f"\n🎯 推荐参数设置:")
    for key, value in settings.items():
        print(f"   {key}: {value}")

    print(f"\n💡 使用建议:")
    print(f"   • 搜索深度: 快速分析15-18, 深度分析20-25")
    print(f"   • 开局阶段: 重点关注出子速度和阵型")
    print(f"   • 中局阶段: 利用MultiPV分析多个变化")
    print(f"   • 残局阶段: 适当增加搜索时间")

if __name__ == "__main__":
    main()