#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
中国象棋开局库
提供常见开局变化和应对策略
"""

class OpeningBook:
    """象棋开局库"""

    def __init__(self):
        self.openings = {
            # 红方开局
            "炮二平五": {
                "name": "中炮开局",
                "responses": {
                    # 黑方应对
                    "马8进7": {"name": "屏风马", "next_moves": ["马二进三", "车一平二"]},
                    "炮8平5": {"name": "顺炮", "next_moves": ["马二进三", "马八进七"]},
                    "象3进5": {"name": "飞象", "next_moves": ["马二进三", "兵七进一"]},
                    "马2进3": {"name": "跳马", "next_moves": ["兵七进一", "马八进七"]},
                }
            },
            "马二进三": {
                "name": "起马开局",
                "responses": {
                    "炮8平5": {"name": "中炮应对", "next_moves": ["相三进五", "马八进七"]},
                    "炮2平5": {"name": "中炮应对", "next_moves": ["相七进五", "车九平八"]},
                    "卒7进1": {"name": "进卒", "next_moves": ["兵七进一", "马八进七"]},
                }
            },
            "兵三进一": {
                "name": "仙人指路",
                "responses": {
                    "炮8平7": {"name": "卒底炮", "next_moves": ["炮二平五", "马二进三"]},
                    "卒3进1": {"name": "对兵局", "next_moves": ["兵七进一", "相七进五"]},
                    "炮2平7": {"name": "金钩炮", "next_moves": ["相三进五", "马八进七"]},
                }
            },
            "相三进五": {
                "name": "飞相开局",
                "responses": {
                    "炮8平5": {"name": "中炮", "next_moves": ["马二进三", "马八进七"]},
                    "炮2平4": {"name": "过宫炮", "next_moves": ["马二进三", "车九平八"]},
                    "马2进3": {"name": "跳马", "next_moves": ["兵七进一", "马八进七"]},
                }
            },
            "马八进七": {
                "name": "起马开局",
                "responses": {
                    "炮2平5": {"name": "中炮", "next_moves": ["车九平八", "马二进三"]},
                    "卒3进1": {"name": "进卒", "next_moves": ["兵三进一", "相三进五"]},
                    "象7进5": {"name": "飞象", "next_moves": ["相三进五", "兵三进一"]},
                }
            }
        }

        # 常见开局原则
        self.principles = {
            "开局原则": [
                "快速出子，尤其是大子(车、马、炮)",
                "抢占中路，控制要点",
                "保持阵型协调，避免孤军深入",
                "注意子力联系，形成攻防体系"
            ],
            "中炮要点": [
                "配合中兵进攻，形成中路攻势",
                "注意保护中路，防止被反击",
                "配合双车形成攻势"
            ],
            "屏风马要点": [
                "稳固防守，反击有力",
                "配合炮、象形成防守体系",
                "寻找机会反击中路或侧翼"
            ],
            "仙人指路要点": [
                "灵活多变，根据对手应对调整策略",
                "注意控制要道，限制对方发展",
                "为中局战斗做准备"
            ]
        }

    def get_opening_name(self, move):
        """根据开局着法获取开局名称"""
        return self.openings.get(move, {}).get("name", "未知开局")

    def get_response_suggestion(self, opening_move, response_move):
        """获取应对建议"""
        opening = self.openings.get(opening_move)
        if not opening:
            return None

        response = opening["responses"].get(response_move)
        if not response:
            return None

        return response

    def get_next_moves(self, opening_move, response_move):
        """获取后续建议着法"""
        response = self.get_response_suggestion(opening_move, response_move)
        if response:
            return response.get("next_moves", [])
        return []

    def analyze_opening_phase(self, moves):
        """分析开局阶段"""
        if len(moves) < 2:
            return {"phase": "开局准备", "suggestions": ["选择适合的开局着法"]}

        if len(moves) <= 6:
            opening_move = moves[0] if moves else None
            response_move = moves[1] if len(moves) > 1 else None

            if opening_move and response_move:
                opening_name = self.get_opening_name(opening_move)
                response = self.get_response_suggestion(opening_move, response_move)

                return {
                    "phase": "开局阶段",
                    "opening": opening_name,
                    "response": response.get("name") if response else "未知应对",
                    "next_moves": self.get_next_moves(opening_move, response_move),
                    "principles": self.principles.get(opening_name, self.principles["开局原则"])
                }

        return {"phase": "过渡到中局", "suggestions": ["注意阵型协调", "准备中局战斗"]}

class OpeningTrainer:
    """开局训练器"""

    def __init__(self):
        self.book = OpeningBook()
        self.current_opening = None
        self.training_mode = False

    def start_training(self, opening_type=None):
        """开始开局训练"""
        self.training_mode = True
        if opening_type:
            self.current_opening = opening_type
        return True

    def check_move(self, move):
        """检查着法是否正确"""
        if not self.training_mode:
            return {"correct": True, "suggestion": "请先开始训练模式"}

        # 这里可以实现具体的训练逻辑
        return {"correct": True, "suggestion": "着法正确"}

    def get_opening_hint(self):
        """获取开局提示"""
        return "按照开局原则，快速出子，控制要点"

def main():
    """演示开局库功能"""
    book = OpeningBook()

    print("📚 中国象棋开局库")
    print("=" * 50)

    # 示例：分析开局
    moves = ["炮二平五", "马8进7", "马二进三"]
    analysis = book.analyze_opening_phase(moves)

    print(f"🎯 开局分析:")
    print(f"   阶段: {analysis['phase']}")
    if 'opening' in analysis:
        print(f"   开局: {analysis['opening']}")
        print(f"   应对: {analysis['response']}")
        print(f"   后续着法: {', '.join(analysis['next_moves'])}")
        print(f"   要点: {', '.join(analysis['principles'][:2])}")

    # 示例：获取建议
    suggestion = book.get_response_suggestion("炮二平五", "马8进7")
    if suggestion:
        print(f"\n💡 应对建议:")
        print(f"   策略: {suggestion['name']}")
        print(f"   后续: {', '.join(suggestion['next_moves'])}")

if __name__ == "__main__":
    main()