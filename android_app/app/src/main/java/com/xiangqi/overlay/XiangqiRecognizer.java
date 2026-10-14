package com.xiangqi.overlay;

import com.xiangqi.overlay.img.GrayImage;
import com.xiangqi.overlay.img.ImageOps;
import com.xiangqi.overlay.img.Perspective;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 棋局识别核心 —— 从 {@code recognition_companion.py} 移植到 Android。
 * <p>
 * 流程（与 Python 版一一对应，并做了三处改进）：
 * <pre>
 *   截屏帧 ──透视矫正──> 480x540 标准棋盘
 *            │
 *            ├─ 逐点裁 52x52 小图 → 灰度 → 高斯模糊
 *            ├─ 与「空位模板」「棋子模板」做归一化互相关，取最高分
 *            └─ 得到 90 个点的棋子分布 + 整体置信度
 *                  │
 *                  ├─ 置信度 < 0.78            → 拒绝
 *                  ├─ 棋子数量不合法           → 拒绝
 *                  ├─ 与上一局面相差不是一步    → 拒绝（动画/漏识别）
 *                  └─ 连续 2 帧一致            → 接受
 * </pre>
 * 三处改进：
 * <ol>
 *   <li><b>自动判断我方颜色</b>：读九宫里的 帅/将 在屏幕上还是下，不再依赖用户手选</li>
 *   <li><b>落子归属判定轮次</b>：看刚走的那步是哪方走的，比原版靠界面指示更可靠</li>
 *   <li><b>空位模板自动去重</b>：相似度 > 0.95 的空位只留一张，速度提升一倍以上</li>
 * </ol>
 */
public final class XiangqiRecognizer {

    public static final int CELL = 60;
    public static final int BOARD_W = CELL * 8;   // 480
    public static final int BOARD_H = CELL * 9;   // 540
    public static final int TEMPLATE = 52;
    public static final double MIN_CONFIDENCE = 0.78;
    public static final int STABLE_FRAME_TARGET = 2;

    private static final double UI_TURN_MIN_DELTA = 0.045;
    private static final int EDGE_THRESHOLD = 60;
    private static final double EMPTY_DEDUPE_THRESHOLD = 0.95;

    private BoardProfile profile;
    private final Position startReference = Position.fromFen(Position.START_FEN);
    private final String startKey = startReference.key();

    private String lastBoardKey;
    private Position lastPosition;
    private String pendingBoardKey;
    private int pendingBoardCount;
    private String lastSideToMove = "w";
    /** 标准棋盘坐标系 -> 屏幕坐标系 的映射，用于把建议着法画成屏幕上的箭头。 */
    private double[] canonicalToScreen;

    public volatile String lastRejectReason = "等待画面";
    public volatile int stableFrameCount;

    public void setProfile(BoardProfile p) {
        this.profile = p;
        reset();
    }

    public void reset() {
        lastBoardKey = null;
        lastPosition = null;
        pendingBoardKey = null;
        pendingBoardCount = 0;
        lastSideToMove = "w";
        lastRejectReason = "等待画面";
        stableFrameCount = 0;
    }

    // ---------------------------------------------------------------- 数据

    /**
     * 从 Bitmap 构造一帧（标定界面用，标定是一次性操作，不在意这点开销）。
     */
    public static Frame fromBitmap(android.graphics.Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] argb = new int[w * h];
        bmp.getPixels(argb, 0, w, 0, 0, w, h);
        GrayImage gray = new GrayImage(w, h);
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            gray.data[i] = (int) (0.299 * ((c >> 16) & 0xFF)
                    + 0.587 * ((c >> 8) & 0xFF)
                    + 0.114 * (c & 0xFF));
        }
        return new Frame(gray, argb, w, h, w);
    }

    /** 一帧画面：灰度图（识别用）+ ARGB 像素（判断行棋方用）。 */
    public static final class Frame {
        public final GrayImage gray;
        public final int[] argb;
        public final int width;
        public final int height;
        /** ARGB 数组的行距（单位：int 个数），截屏时行尾可能有 padding。 */
        public final int rowStride;

        public Frame(GrayImage gray, int[] argb, int width, int height, int rowStride) {
            this.gray = gray;
            this.argb = argb;
            this.width = width;
            this.height = height;
            this.rowStride = rowStride;
        }
    }

    /** 识别结果。 */
    public static final class Result {
        public boolean accepted;
        public String rejectReason = "";
        public Position position;
        public String fen = "";
        public String boardKey = "";
        public double confidence;
        public String sideToMove = "w";
        public String mySide = "red";
        public String turnSource = "";
        public int stableCount;
        /** 对手刚走的那步 {fromRow, fromCol, toRow, toCol}。 */
        public int[] lastMove;
        public String lastMoveText = "";
    }

    // ---------------------------------------------------------------- 主流程

    public Result recognize(Frame frame) {
        Result r = new Result();
        if (profile == null || !profile.isCalibrated()) {
            r.rejectReason = "尚未完成棋盘标定";
            lastRejectReason = r.rejectReason;
            return r;
        }
        if (frame == null || frame.width < 100 || frame.height < 100) {
            r.rejectReason = "画面无效";
            lastRejectReason = r.rejectReason;
            return r;
        }

        double[] srcPts = profile.absoluteCorners(frame.width, frame.height);
        GrayImage canonical = warpToCanonical(frame.gray, srcPts);
        if (canonical == null) {
            r.rejectReason = "棋盘四角标定无效，请重新标定";
            lastRejectReason = r.rejectReason;
            return r;
        }

        char[][] raw = new char[Position.ROWS][Position.COLS];
        double confidence = classifyBoard(canonical, raw);
        r.confidence = confidence;

        // 自动判断屏幕下方是哪种颜色（即通常我所在的一侧）
        String detected = detectBottomSide(raw);
        if (detected != null) {
            profile.bottomSide = detected;
        }
        r.mySide = profile.bottomSide;

        // 屏幕下方是黑方时，把棋盘旋转 180 度对齐到「红在下」的 FEN 约定
        char[][] board = "black".equals(profile.bottomSide) ? rotate180(raw) : raw;

        Position candidate = toPosition(board);
        String boardKey = keyOf(board);
        r.boardKey = boardKey;
        r.position = candidate;
        r.fen = candidate.toFen();

        if (confidence < MIN_CONFIDENCE) {
            reject(r, String.format("识别置信度偏低（%.0f%%），请确认棋盘完整、无遮挡、无弹窗", confidence * 100));
            return r;
        }
        if (!isBoardShapeValid(board)) {
            reject(r, "棋子数量不合法，可能认错了棋子，请在标定时使用标准开局画面");
            return r;
        }

        // 稳定帧过滤：动画、拖动过程中不采纳
        if (boardKey.equals(pendingBoardKey)) {
            pendingBoardCount++;
        } else {
            pendingBoardKey = boardKey;
            pendingBoardCount = 1;
        }
        r.stableCount = pendingBoardCount;
        stableFrameCount = pendingBoardCount;
        if (pendingBoardCount < STABLE_FRAME_TARGET) {
            reject(r, "等待画面稳定 " + pendingBoardCount + "/" + STABLE_FRAME_TARGET);
            return r;
        }

        if (boardKey.equals(lastBoardKey)) {
            reject(r, "局面未变化，等待下一手");
            return r;
        }

        // 单步推进校验（防止漏识别或抓到动画中间帧）
        Position reference = lastPosition != null ? lastPosition : null;
        int[] move;
        if (reference == null) {
            move = null;   // 第一帧：无参照，直接接受
        } else {
            move = Position.diffMove(reference, candidate);
            if (move == null) {
                reject(r, "检测到跨步跳变，可能漏识别或抓到动画帧，请稍等");
                return r;
            }
        }
        r.lastMove = move;

        // 判断轮到谁走
        String sideToMove = resolveSideToMove(frame, candidate, move);
        r.sideToMove = sideToMove;
        candidate.redToMove = "w".equals(sideToMove);
        r.fen = candidate.toFen();

        // 对手刚走的着法（用走子前的局面来翻译，才能知道是哪个子）
        if (move != null && reference != null) {
            r.lastMoveText = MoveTranslator.toChinese(reference, toUci(move));
        }

        lastBoardKey = boardKey;
        lastPosition = candidate.copy();
        lastSideToMove = sideToMove;
        r.accepted = true;
        r.turnSource = lastTurnSource;
        lastRejectReason = "";
        return r;
    }

    private String lastTurnSource = "";

    private void reject(Result r, String reason) {
        r.rejectReason = reason;
        lastRejectReason = reason;
    }

    // ---------------------------------------------------------------- 透视

    private GrayImage warpToCanonical(GrayImage frameGray, double[] srcPts) {
        double[] dst = {0, 0, BOARD_W, 0, BOARD_W, BOARD_H, 0, BOARD_H};
        // 求 dst -> src 的映射，再逐像素采样回源图
        double[] h = Perspective.findHomography(dst, srcPts);
        if (h == null) {
            canonicalToScreen = null;
            return null;
        }
        canonicalToScreen = h;
        return Perspective.warp(frameGray, h, BOARD_W, BOARD_H);
    }

    /**
     * 把棋盘上的一个交叉点换算成屏幕坐标，用于在悬浮窗里画出落点箭头。
     *
     * @return {x, y}（相对截屏帧的像素坐标）；无法计算返回 null
     */
    public float[] screenPointOf(int row, int col) {
        double[] h = canonicalToScreen;
        if (h == null || profile == null) {
            return null;
        }
        double cx;
        double cy;
        if ("black".equals(profile.bottomSide)) {
            // 识别时把棋盘旋转过，画箭头要转回屏幕方向
            cx = (Position.COLS - 1 - col) * CELL;
            cy = (Position.ROWS - 1 - row) * CELL;
        } else {
            cx = col * CELL;
            cy = row * CELL;
        }
        double denom = h[6] * cx + h[7] * cy + h[8];
        if (Math.abs(denom) < 1e-12) {
            return null;
        }
        double x = (h[0] * cx + h[1] * cy + h[2]) / denom;
        double y = (h[3] * cx + h[4] * cy + h[5]) / denom;
        return new float[]{(float) x, (float) y};
    }

    // ---------------------------------------------------------------- 分类

    private GrayImage preparePatch(GrayImage canonical, int col, int row) {
        GrayImage raw = ImageOps.cropPatch(canonical, col * CELL, row * CELL, TEMPLATE);
        return ImageOps.gaussianBlur3(raw);
    }

    /** 返回整体置信度，同时把识别出的棋盘写进 out。 */
    private double classifyBoard(GrayImage canonical, char[][] out) {
        double total = 0;
        int n = 0;
        List<GrayImage> empties = profile.emptyTemplates;

        for (int row = 0; row < Position.ROWS; row++) {
            for (int col = 0; col < Position.COLS; col++) {
                GrayImage patch = preparePatch(canonical, col, row);
                char bestPiece = Position.EMPTY;
                double best = -1, second = -1;

                for (int i = 0; i < empties.size(); i++) {
                    double s = ImageOps.ncc(patch, empties.get(i));
                    if (s > best) {
                        second = best;
                        best = s;
                        bestPiece = Position.EMPTY;
                    } else if (s > second) {
                        second = s;
                    }
                }
                for (Map.Entry<String, List<GrayImage>> e : profile.pieceTemplates.entrySet()) {
                    char pc = e.getKey().isEmpty() ? Position.EMPTY : e.getKey().charAt(0);
                    List<GrayImage> list = e.getValue();
                    for (int i = 0; i < list.size(); i++) {
                        double s = ImageOps.ncc(patch, list.get(i));
                        if (s > best) {
                            second = best;
                            best = s;
                            bestPiece = pc;
                        } else if (s > second) {
                            second = s;
                        }
                    }
                }

                out[row][col] = bestPiece;
                double gap = Math.max(0, best - Math.max(second, 0));
                total += Math.min(1.0, best * 0.8 + gap * 0.2);
                n++;
            }
        }
        return n > 0 ? total / n : 0;
    }

    // ------------------------------------------------------------ 标定相关

    /**
     * 校验当前画面是否为标准初始局面，通过后自动生成模板。
     *
     * @return null 表示成功，否则返回失败原因
     */
    public String calibrate(BoardProfile target, Frame frame, float[] cornersRel, String bottomSideHint) {
        if (frame == null || frame.width < 100) {
            return "没有可用的截图";
        }
        this.profile = target;

        // v1.6：手动角点自动吸附 —— 用户只需大致框住棋盘，
        // 这里在粗略四角附近重新搜索网格并精确对齐；找不到就沿用原角点。
        float[] use = cornersRel;
        try {
            float[] snapped = BoardAutoDetect.refineNear(frame.gray, cornersRel);
            if (snapped != null) {
                use = snapped;
            }
        } catch (Throwable ignored) {
            // 吸附失败不影响原流程
        }

        for (int i = 0; i < 8; i++) {
            target.cornersRel[i] = use[i];
        }

        double[] srcPts = target.absoluteCorners(frame.width, frame.height);
        GrayImage canonical = warpToCanonical(frame.gray, srcPts);
        if (canonical == null) {
            return "四角坐标无效，请重新拖动";
        }

        // 与 Python 版一致：先检查像不像初始局面
        String err = validateInitialLayout(canonical);
        if (err != null) {
            return err;
        }

        target.pieceTemplates.clear();
        target.emptyTemplates.clear();

        List<GrayImage> emptyPool = new ArrayList<>();
        for (int row = 0; row < Position.ROWS; row++) {
            for (int col = 0; col < Position.COLS; col++) {
                char piece = startReference.cells[row][col];
                GrayImage patch = preparePatch(canonical, col, row);
                if (piece == Position.EMPTY) {
                    emptyPool.add(patch);
                } else {
                    String key = String.valueOf(piece);
                    List<GrayImage> list = target.pieceTemplates.get(key);
                    if (list == null) {
                        list = new ArrayList<>();
                        target.pieceTemplates.put(key, list);
                    }
                    list.add(patch);
                }
            }
        }
        target.emptyTemplates.addAll(dedupe(emptyPool));

        // 行棋方指示区（棋盘上方与下方各一条）
        int[] top = estimateTurnRegion(use, frame.width, frame.height, true);
        int[] bottom = estimateTurnRegion(use, frame.width, frame.height, false);
        toRelative(top, frame.width, frame.height, target.turnTop);
        toRelative(bottom, frame.width, frame.height, target.turnBottom);

        // 用真实识别结果自动判断屏幕下方是红还是黑（比让用户手选更可靠）
        char[][] raw = new char[Position.ROWS][Position.COLS];
        classifyBoard(canonical, raw);
        String detected = detectBottomSide(raw);
        target.bottomSide = detected != null ? detected
                : (bottomSideHint == null ? "red" : bottomSideHint);

        target.calibratedAt = System.currentTimeMillis();
        reset();
        return null;
    }

    /**
     * 检查画面是否是标准初始局面（32 个点有棋子、58 个点为空）。
     * 判定方式与 Python 版一致：与「平均空位模板」的差异 + 边缘密度 + 中心纹理。
     */
    private String validateInitialLayout(GrayImage canonical) {
        List<int[]> occupied = new ArrayList<>();
        List<int[]> empty = new ArrayList<>();
        for (int row = 0; row < Position.ROWS; row++) {
            for (int col = 0; col < Position.COLS; col++) {
                if (startReference.cells[row][col] != Position.EMPTY) {
                    occupied.add(new int[]{col, row});
                } else {
                    empty.add(new int[]{col, row});
                }
            }
        }
        if (empty.isEmpty()) {
            return "未能生成空位模板";
        }

        // 平均空位模板
        GrayImage ref = new GrayImage(TEMPLATE, TEMPLATE);
        for (int[] p : empty) {
            GrayImage g = preparePatch(canonical, p[0], p[1]);
            for (int i = 0; i < ref.data.length; i++) {
                ref.data[i] += g.data[i];
            }
        }
        for (int i = 0; i < ref.data.length; i++) {
            ref.data[i] /= empty.size();
        }

        double occMedian = median(occupancyScores(canonical, occupied, ref));
        double empMedian = median(occupancyScores(canonical, empty, ref));
        if (occMedian - empMedian < 0.06) {
            return "当前画面不像标准开局局面，请回到开局（双方棋子都在原始位置）再标定";
        }
        double threshold = (occMedian + empMedian) / 2.0;

        // 逐点校验改成「先统计再报错」：失败时能说出具体错了几个点，
        // 便于远程定位是角点偏了还是画面里有遮挡（v1.5）
        int occBad = 0;
        int occWorst = -1;
        for (int[] p : occupied) {
            if (occupancyScore(canonical, p[0], p[1], ref) <= threshold) {
                occBad++;
                if (occWorst < 0) {
                    occWorst = p[0] + 1;   // 记下第一个异常点的列号（1-9）
                }
            }
        }
        int empBad = 0;
        int empWorst = -1;
        for (int[] p : empty) {
            if (occupancyScore(canonical, p[0], p[1], ref) >= threshold) {
                empBad++;
                if (empWorst < 0) {
                    empWorst = p[0] + 1;
                }
            }
        }
        if (occBad + empBad > 0) {
            return String.format("四角可能有偏差：%d 个该有棋子的点、%d 个空点判定异常"
                            + "（首个异常点在第 %s 列）。请双指放大微调角点后重试",
                    occBad, empBad, occWorst > 0 ? String.valueOf(occWorst) : String.valueOf(empWorst));
        }
        return null;
    }

    private double[] occupancyScores(GrayImage canonical, List<int[]> pts, GrayImage emptyRef) {
        double[] out = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[i] = occupancyScore(canonical, pts.get(i)[0], pts.get(i)[1], emptyRef);
        }
        return out;
    }

    private double occupancyScore(GrayImage canonical, int col, int row, GrayImage emptyRef) {
        GrayImage patch = preparePatch(canonical, col, row);
        double diff = ImageOps.meanAbsDiff(patch, emptyRef);
        double edge = ImageOps.edgeRatio(patch, EDGE_THRESHOLD);
        double centerVar = ImageOps.centerStdDev(patch);
        return diff * 0.6 + edge * 0.25 + centerVar * 0.15;
    }

    private static double median(double[] v) {
        if (v.length == 0) {
            return 0;
        }
        double[] c = v.clone();
        java.util.Arrays.sort(c);
        int mid = c.length / 2;
        return c.length % 2 == 1 ? c[mid] : (c[mid - 1] + c[mid]) / 2.0;
    }

    /** 相似度高的空位模板只留一张，加快识别速度。 */
    private static List<GrayImage> dedupe(List<GrayImage> pool) {
        List<GrayImage> kept = new ArrayList<>();
        for (GrayImage g : pool) {
            boolean similar = false;
            for (GrayImage k : kept) {
                if (ImageOps.ncc(g, k) > EMPTY_DEDUPE_THRESHOLD) {
                    similar = true;
                    break;
                }
            }
            if (!similar) {
                kept.add(g);
            }
        }
        return kept;
    }

    // ------------------------------------------------------------ 轮次判断

    /**
     * 判断现在轮到谁走。
     * 优先级：落子归属 > 界面指示 > 默认红先。
     */
    private String resolveSideToMove(Frame frame, Position candidate, int[] move) {
        if (move != null) {
            char moved = candidate.cells[move[2]][move[3]];
            if (moved != Position.EMPTY) {
                lastTurnSource = "move_attribution";
                return Position.isRed(moved) ? "b" : "w";
            }
        }
        String ui = detectSideToMoveFromUi(frame);
        if (ui != null) {
            lastTurnSource = "ui_indicator";
            return ui;
        }
        lastTurnSource = "default_red_first";
        return "w";
    }

    /**
     * 从界面亮度/颜色活跃度猜「轮到谁走」。
     * 棋盘上方与下方各取一条区域，哪边更「活跃」（高亮、有彩色元素）就说明那方正在走子。
     */
    private String detectSideToMoveFromUi(Frame frame) {
        if (profile.turnTop[2] <= profile.turnTop[0] || profile.turnBottom[2] <= profile.turnBottom[0]) {
            return null;
        }
        int[] top = clampRect(profile.absoluteRect(profile.turnTop, frame.width, frame.height),
                frame.width, frame.height);
        int[] bottom = clampRect(profile.absoluteRect(profile.turnBottom, frame.width, frame.height),
                frame.width, frame.height);
        if (top == null || bottom == null) {
            return null;
        }
        double topScore = activityScore(frame, top);
        double bottomScore = activityScore(frame, bottom);
        double delta = bottomScore - topScore;
        if (Math.abs(delta) < UI_TURN_MIN_DELTA) {
            return null;
        }
        String bottomSide = profile.bottomSide;
        String activeSide = delta > 0 ? bottomSide
                : ("red".equals(bottomSide) ? "black" : "red");
        return "red".equals(activeSide) ? "w" : "b";
    }

    private double activityScore(Frame frame, int[] rect) {
        if (frame.argb == null) {
            return 0;
        }
        ImageOps.ColorStats st = ImageOps.colorStats(frame.argb, 0, frame.rowStride,
                rect[0], rect[1], rect[2], rect[3]);
        double edge = 0;
        GrayImage region = frame.gray.crop(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1]);
        if (region.size() > 0) {
            edge = ImageOps.edgeRatio(region, EDGE_THRESHOLD);
        }
        return st.colorfulRatio * 0.4 + edge * 0.25 + st.brightRatio * 0.2
                + st.saturation * 0.1 + st.value * 0.05;
    }

    private static int[] clampRect(int[] r, int w, int h) {
        int left = Math.max(0, Math.min(w - 1, r[0]));
        int top = Math.max(0, Math.min(h - 1, r[1]));
        int right = Math.max(left + 1, Math.min(w, r[2]));
        int bottom = Math.max(top + 1, Math.min(h, r[3]));
        if (right - left < 8 || bottom - top < 8) {
            return null;
        }
        return new int[]{left, top, right, bottom};
    }

    // ------------------------------------------------------------ 工具

    /**
     * 通过九宫里的 帅/将 判断屏幕下方是哪种颜色。
     * 红帅只能在下方 3x3 九宫（row 7-9, col 3-5），黑将只能在上方九宫。
     */
    private static String detectBottomSide(char[][] board) {
        boolean redBottom = false;
        for (int r = 7; r <= 9; r++) {
            for (int c = 3; c <= 5; c++) {
                if (board[r][c] == 'K') {
                    redBottom = true;
                }
            }
        }
        if (redBottom) {
            return "red";
        }
        for (int r = 7; r <= 9; r++) {
            for (int c = 3; c <= 5; c++) {
                if (board[r][c] == 'k') {
                    return "black";
                }
            }
        }
        return null;
    }

    private static char[][] rotate180(char[][] in) {
        char[][] out = new char[Position.ROWS][Position.COLS];
        for (int r = 0; r < Position.ROWS; r++) {
            for (int c = 0; c < Position.COLS; c++) {
                out[r][c] = in[Position.ROWS - 1 - r][Position.COLS - 1 - c];
            }
        }
        return out;
    }

    private static Position toPosition(char[][] board) {
        Position p = new Position();
        for (int r = 0; r < Position.ROWS; r++) {
            System.arraycopy(board[r], 0, p.cells[r], 0, Position.COLS);
        }
        return p;
    }

    private static String keyOf(char[][] board) {
        StringBuilder sb = new StringBuilder(Position.ROWS * (Position.COLS + 1));
        for (int r = 0; r < Position.ROWS; r++) {
            for (int c = 0; c < Position.COLS; c++) {
                sb.append(board[r][c]);
            }
            sb.append('|');
        }
        return sb.toString();
    }

    /** 内部坐标 → UCI。 */
    private static String toUci(int[] move) {
        return String.valueOf((char) ('a' + move[1]))
                + (9 - move[0])
                + (char) ('a' + move[3])
                + (9 - move[2]);
    }

    private static boolean isBoardShapeValid(char[][] board) {
        int[] count = new int[128];
        for (int r = 0; r < Position.ROWS; r++) {
            for (int c = 0; c < Position.COLS; c++) {
                char p = board[r][c];
                if (p != Position.EMPTY && p < 128) {
                    count[p]++;
                }
            }
        }
        if (count['K'] != 1 || count['k'] != 1) {
            return false;
        }
        return count['A'] <= 2 && count['a'] <= 2
                && count['B'] <= 2 && count['b'] <= 2
                && count['N'] <= 2 && count['n'] <= 2
                && count['R'] <= 2 && count['r'] <= 2
                && count['C'] <= 2 && count['c'] <= 2
                && count['P'] <= 5 && count['p'] <= 5;
    }

    /** 估计「轮到谁走」的指示区（棋盘上/下方各一条）。 */
    private static int[] estimateTurnRegion(float[] cornersRel, int frameW, int frameH, boolean top) {
        float[] abs = new float[8];
        for (int i = 0; i < 4; i++) {
            abs[2 * i] = cornersRel[2 * i] * frameW;
            abs[2 * i + 1] = cornersRel[2 * i + 1] * frameH;
        }
        float left = Math.min(Math.min(abs[0], abs[2]), Math.min(abs[4], abs[6]));
        float right = Math.max(Math.max(abs[0], abs[2]), Math.max(abs[4], abs[6]));
        float topY = Math.min(Math.min(abs[1], abs[3]), Math.min(abs[5], abs[7]));
        float bottomY = Math.max(Math.max(abs[1], abs[3]), Math.max(abs[5], abs[7]));
        float boardW = right - left;
        float boardH = bottomY - topY;

        int regionW = (int) Math.min(frameW - 20, boardW * 1.25);
        int regionH = (int) Math.max(48, Math.min(frameH * 0.12, boardH * 0.18));
        int centerX = (int) ((left + right) / 2);
        int halfW = regionW / 2;

        int centerY = top
                ? (int) Math.max(regionH / 2 + 6, topY - boardH * 0.22)
                : (int) Math.min(frameH - regionH / 2 - 6, bottomY + boardH * 0.22);

        int l = Math.max(0, centerX - halfW);
        int t = Math.max(0, centerY - regionH / 2);
        int rr = Math.min(frameW, centerX + halfW);
        int b = Math.min(frameH, centerY + regionH / 2);
        return new int[]{l, t, rr, b};
    }

    private static void toRelative(int[] rect, int frameW, int frameH, float[] out) {
        out[0] = rect[0] / (float) frameW;
        out[1] = rect[1] / (float) frameH;
        out[2] = rect[2] / (float) frameW;
        out[3] = rect[3] / (float) frameH;
    }
}
