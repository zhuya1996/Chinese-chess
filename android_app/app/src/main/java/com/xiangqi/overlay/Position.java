package com.xiangqi.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * 象棋局面的最小表示：10 行 x 9 列 + 行棋方。
 * <p>
 * 坐标约定（与 FEN 一致）：
 *  - row 0 在屏幕最上方（黑方底线），row 9 在最下方（红方底线）
 *  - col 0 为 'a' 列，col 8 为 'i' 列
 *  - 大写 = 红方（K 帅 A 仕 B 相 N 马 R 车 C 炮 P 兵）
 *  - 小写 = 黑方（k 将 a 士 b 象 n 马 r 车 c 炮 p 卒）
 * <p>
 * 注意：本项目不需要走法合法性判断（那是引擎的事），
 * 所以这里只有局面表示与 FEN 读写，刻意保持精简。
 */
public final class Position {

    public static final int ROWS = 10;
    public static final int COLS = 9;
    public static final char EMPTY = '.';

    public static final String START_FEN =
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    public final char[][] cells = new char[ROWS][COLS];
    public boolean redToMove = true;

    /**
     * 关键：Java 的 char 数组默认值是 '\0'，不是 '.'。
     * 不在这里统一填充 EMPTY 的话，fromFen 只给有棋子的格子赋值，
     * 空位就全是 '\0' —— 而 '\0' != '.'，于是：
     *   ① validateInitialLayout 认为 90 个点全是「有棋子」→ 空位模板列表为空，
     *      标定必然报「未能生成空位模板」（真机 v1.4 实测命中）；
     *   ② diffMove 找不到「起点变空」→ 永远返回 null → 棋步识别失效；
     *   ③ toFen 生成不出空位数字 → 喂给引擎的 FEN 是乱码。
     */
    public Position() {
        for (int r = 0; r < ROWS; r++) {
            java.util.Arrays.fill(cells[r], EMPTY);
        }
    }

    public static Position fromFen(String fen) {
        Position p = new Position();
        if (fen == null) {
            return p;
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length == 0) {
            return p;
        }
        String[] rows = parts[0].split("/");
        for (int r = 0; r < ROWS && r < rows.length; r++) {
            int col = 0;
            for (int i = 0; i < rows[r].length() && col < COLS; i++) {
                char c = rows[r].charAt(i);
                if (Character.isDigit(c)) {
                    col += c - '0';
                } else {
                    p.cells[r][col] = c;
                    col++;
                }
            }
        }
        if (parts.length > 1 && "b".equalsIgnoreCase(parts[1])) {
            p.redToMove = false;
        }
        return p;
    }

    /** 生成引擎可用的 FEN（棋盘 + 行棋方）。 */
    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            int empty = 0;
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < COLS; c++) {
                char p = cells[r][c];
                // 兜底：万一有路径漏填空位（'\0'），也按空位处理，避免 FEN 出乱码
                if (p == EMPTY || p == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        row.append(empty);
                        empty = 0;
                    }
                    row.append(p);
                }
            }
            if (empty > 0) {
                row.append(empty);
            }
            if (row.length() == 0) {
                row.append('9');
            }
            sb.append(row);
            if (r < ROWS - 1) {
                sb.append('/');
            }
        }
        sb.append(' ').append(redToMove ? 'w' : 'b');
        return sb.toString();
    }

    public Position copy() {
        Position p = new Position();
        p.redToMove = redToMove;
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(cells[r], 0, p.cells[r], 0, COLS);
        }
        return p;
    }

    /** 局面指纹，用于判断「这帧和上一帧是不是同一个局面」。 */
    public String key() {
        StringBuilder sb = new StringBuilder(ROWS * COLS + ROWS);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(cells[r][c]);
            }
            sb.append('|');
        }
        return sb.toString();
    }

    public static boolean isRed(char piece) {
        return piece != EMPTY && Character.isUpperCase(piece);
    }

    /** 找某个颜色的帅/将位置，返回 {row, col}，找不到返回 null。 */
    public int[] findKing(boolean red) {
        char target = red ? 'K' : 'k';
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c] == target) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /**
     * 找出从 prev 到 cur 的那步棋。
     * 正常情况恰好有 2 个格子不同：起点变空、终点变成该棋子。
     *
     * @return {fromRow, fromCol, toRow, toCol}；无法唯一确定返回 null
     */
    public static int[] diffMove(Position prev, Position cur) {
        List<int[]> changed = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (prev.cells[r][c] != cur.cells[r][c]) {
                    changed.add(new int[]{r, c});
                }
            }
        }
        if (changed.size() != 2) {
            return null;
        }
        int[] a = changed.get(0);
        int[] b = changed.get(1);
        // 终点是现在有棋子的那个格子
        if (cur.cells[a[0]][a[1]] != EMPTY && cur.cells[b[0]][b[1]] == EMPTY) {
            return new int[]{b[0], b[1], a[0], a[1]};
        }
        if (cur.cells[b[0]][b[1]] != EMPTY && cur.cells[a[0]][a[1]] == EMPTY) {
            return new int[]{a[0], a[1], b[0], b[1]};
        }
        return null;
    }
}
