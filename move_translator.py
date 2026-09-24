#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UCI 着法转中文着法
"""

# 中文数字
CHINESE_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九']
ARABIC_NUMS = ['１', '２', '３', '４', '５', '６', '７', '８', '９']

# 棋子名称
PIECE_NAMES = {
    'r': '车', 'n': '马', 'b': '相', 'a': '士', 'k': '帅',
    'c': '炮', 'p': '兵',
    'R': '车', 'N': '马', 'B': '象', 'A': '士', 'K': '将',
    'C': '炮', 'P': '卒'
}


def fen_to_board(fen):
    """将 FEN 转换为棋盘数组"""
    board = [['' for _ in range(9)] for _ in range(10)]
    rows = fen.split(' ')[0].split('/')
    for fen_row, row_data in enumerate(rows):
        col = 0
        for ch in row_data:
            if ch.isdigit():
                col += int(ch)
            else:
                board[fen_row][col] = ch
                col += 1
    return board


def uci_to_chinese(uci_move, fen=None):
    """
    将 UCI 格式着法转换为中文着法

    Args:
        uci_move: UCI 格式着法，如 "h2e2"
        fen: 当前局面的 FEN 字符串（可选，用于确定棋子类型）

    Returns:
        中文着法，如 "炮二平五"
    """
    if not uci_move or len(uci_move) < 4:
        return uci_move

    files = 'abcdefghi'

    # 解析 UCI 格式
    from_file = files.index(uci_move[0]) if uci_move[0] in files else -1
    from_rank = int(uci_move[1]) if uci_move[1].isdigit() else -1
    to_file = files.index(uci_move[2]) if uci_move[2] in files else -1
    to_rank = int(uci_move[3]) if uci_move[3].isdigit() else -1

    if from_file == -1 or to_file == -1 or from_rank == -1 or to_rank == -1:
        return uci_move

    # 判断红黑方（0-4 行是红方）
    is_red = from_rank <= 4

    # 获取棋子类型
    piece = ''
    if fen:
        board = fen_to_board(fen)
        piece_code = board[from_rank][from_file]
        if piece_code:
            piece = PIECE_NAMES.get(piece_code, '')

    # 列号转换（红方从右往左，黑方从左往右）
    from_col = (9 - from_file) if is_red else (from_file + 1)
    to_col = (9 - to_file) if is_red else (to_file + 1)

    # 方向和步数
    direction = ''
    steps = ''

    if from_file == to_file:
        # 直线移动
        rank_diff = abs(to_rank - from_rank)
        if (is_red and to_rank < from_rank) or (not is_red and to_rank > from_rank):
            direction = '进'
        else:
            direction = '退'
        nums = CHINESE_NUMS if is_red else ARABIC_NUMS
        steps = nums[rank_diff - 1] if rank_diff <= 9 else str(rank_diff)
    else:
        # 横向移动
        direction = '平'
        nums = CHINESE_NUMS if is_red else ARABIC_NUMS
        steps = nums[to_col - 1]

    # 组合着法
    nums = CHINESE_NUMS if is_red else ARABIC_NUMS
    from_col_chinese = nums[from_col - 1]

    if piece:
        return f"{piece}{from_col_chinese}{direction}{steps}"
    else:
        # 没有棋子信息时，只显示位置和方向
        return f"{from_col_chinese}{direction}{steps}"


def format_move_with_piece(uci_move, fen):
    """
    完整格式的着法转换（包含棋子名称）

    Args:
        uci_move: UCI 格式着法
        fen: 当前局面 FEN

    Returns:
        完整中文着法，如 "炮二平五"
    """
    return uci_to_chinese(uci_move, fen)


def batch_translate(uci_moves, fen=None):
    """
    批量转换着法

    Args:
        uci_moves: UCI 着法列表
        fen: 当前局面 FEN

    Returns:
        中文着法列表
    """
    return [uci_to_chinese(move, fen) for move in uci_moves]


if __name__ == '__main__':
    # 测试
    test_fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w"

    test_moves = [
        'b2e2',  # 炮二平五
        'h2e2',  # 炮八平五
        'b0c2',  # 马二进三
        'a3a4',  # 兵一进一
    ]

    print("测试着法转换:")
    for move in test_moves:
        chinese = uci_to_chinese(move, test_fen)
        print(f"  {move} → {chinese}")
