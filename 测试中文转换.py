#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试中文着法转换"""

from move_translator import uci_to_chinese

# 测试标准开局 FEN
test_fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w"

test_moves = [
    ('h9g7', '黑方马8进7'),
    ('b2e2', '红方炮二平五'),
    ('h2e2', '红方炮八平五'),
    ('b0c2', '红方马二进三'),
    ('a3a4', '红方兵一进一'),
]

print("测试中文着法转换：\n")
for uci, expected in test_moves:
    result = uci_to_chinese(uci, test_fen)
    print(f"{uci:8s} → {result:12s} (期望: {expected})")

print("\n\n测试简化转换（无 FEN）：\n")
for uci, expected in test_moves:
    result = uci_to_chinese(uci)
    print(f"{uci:8s} → {result}")
