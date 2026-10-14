package com.xiangqi.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UCI 着法 → 中文着法（炮二平五）。
 * <p>
 * 注意：原项目 move_translator.py 存在方向与棋子识别错误（马会"平"、兵会"退"），
 * 本实现按标准象棋记谱法重写，规则如下：
 *
 *  1. 红方列号从右往左数一~九（i 列 = 一，a 列 = 九）；黑方从左往右数（a = １，i = ９）
 *  2. 同列移动：向对方方向为「进」，反之「退」
 *  3. 跨列移动：一律「平」，数字为目标列号
 *  4. 车/炮/兵/将 的进退数字 = 移动格数；马/相/仕 的进退数字 = 目标列号
 *  5. 同列有己方同种棋子时，用 前/中/后 代替列号，如「前炮平五」
 */
public final class MoveTranslator {

    private static final String[] RED_NUMS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] BLACK_NUMS = {"１", "２", "３", "４", "５", "６", "７", "８", "９"};

    private MoveTranslator() {
    }

    public static String pieceName(char piece) {
        switch (piece) {
            case 'K': return "帅";
            case 'A': return "仕";
            case 'B': return "相";
            case 'N': return "马";
            case 'R': return "车";
            case 'C': return "炮";
            case 'P': return "兵";
            case 'k': return "将";
            case 'a': return "士";
            case 'b': return "象";
            case 'n': return "马";
            case 'r': return "车";
            case 'c': return "炮";
            case 'p': return "卒";
            default: return "";
        }
    }

    /** 把 UCI 着法转成中文。board 用于确定棋子种类与前后消歧。 */
    public static String toChinese(Position board, String uci) {
        if (uci == null || uci.length() < 4) {
            return uci == null ? "" : uci;
        }
        int fromCol = uci.charAt(0) - 'a';
        int uciFromRank = uci.charAt(1) - '0';
        int toCol = uci.charAt(2) - 'a';
        int uciToRank = uci.charAt(3) - '0';

        // UCI 的 rank 0 是红方底线，对应内部 row 9
        int fromRow = 9 - uciFromRank;
        int toRow = 9 - uciToRank;

        if (fromCol < 0 || fromCol > 8 || toCol < 0 || toCol > 8
                || fromRow < 0 || fromRow > 9 || toRow < 0 || toRow > 9) {
            return uci;
        }

        char piece = board.cells[fromRow][fromCol];
        if (piece == Position.EMPTY) {
            return uci;
        }

        boolean red = Character.isUpperCase(piece);
        String[] nums = red ? RED_NUMS : BLACK_NUMS;
        String name = pieceName(piece);
        char type = Character.toLowerCase(piece);

        // 方向与数字
        String direction;
        String number;
        if (fromCol == toCol) {
            boolean forward = red ? (toRow < fromRow) : (toRow > fromRow);
            direction = forward ? "进" : "退";
            if (type == 'n' || type == 'b' || type == 'a') {
                // 马/相/仕 用目标列号
                number = nums[fileNumber(red, toCol) - 1];
            } else {
                int steps = Math.abs(toRow - fromRow);
                number = steps >= 1 && steps <= 9 ? nums[steps - 1] : String.valueOf(steps);
            }
        } else {
            direction = "平";
            number = nums[fileNumber(red, toCol) - 1];
        }

        // 同列同种棋子消歧
        String prefix = disambiguationPrefix(board, piece, fromRow, fromCol);
        if (prefix != null) {
            return prefix + name + direction + number;
        }
        return name + nums[fileNumber(red, fromCol) - 1] + direction + number;
    }

    private static int fileNumber(boolean red, int col) {
        return red ? (9 - col) : (col + 1);
    }

    /**
     * 若同一列上还有己方同种棋子，返回 前/中/后；否则返回 null。
     */
    private static String disambiguationPrefix(Position board, char piece, int row, int col) {
        char type = Character.toLowerCase(piece);
        if (type == 'k') {
            return null; // 帅/将只有一个
        }
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r < Position.ROWS; r++) {
            if (board.cells[r][col] == piece) {
                rows.add(r);
            }
        }
        if (rows.size() < 2) {
            return null;
        }
        boolean red = Character.isUpperCase(piece);
        // 红方：行号越小越靠前；黑方：行号越大越靠前
        if (red) {
            Collections.sort(rows);
        } else {
            Collections.sort(rows, Collections.reverseOrder());
        }
        int idx = rows.indexOf(row);
        if (idx < 0) {
            return null;
        }
        if (rows.size() == 2) {
            return idx == 0 ? "前" : "后";
        }
        if (rows.size() == 3) {
            return idx == 0 ? "前" : (idx == 1 ? "中" : "后");
        }
        return null; // 超过 3 个的情况极罕见，退回普通记法
    }
}
