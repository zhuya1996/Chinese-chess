package com.xiangqi.overlay;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Pikafish 引擎进程封装。
 * <p>
 * 引擎随 APK 打包在 {@code jniLibs/arm64-v8a/libpikafish.so}，安装后位于
 * {@code nativeLibraryDir}，这是 Android 10+ 唯一允许执行原生二进制的位置
 * （从应用数据目录解压再执行会被 W^X 策略拦截）。
 * <p>
 * 与引擎的交互完全遵循 UCI 文本协议：stdin 写命令、stdout 读结果。
 */
public final class PikafishEngine {

    /** 一条候选着法（MultiPV 的一行结果）。 */
    public static final class Candidate {
        public int depth;
        public int rank = 1;          // multipv 序号，从 1 开始
        public int scoreCp;           // 厘兵，正值 = 行棋方占优
        public boolean mate;
        public int mateIn;            // mateIn > 0 表示行棋方 N 步获胜，< 0 表示 N 步被将死
        public List<String> pv = new ArrayList<>();
        /** 主着法的中文写法，如「炮二平五」。 */
        public String moveText = "";
        /** 主着法的 UCI，如 h2e2。 */
        public String uci = "";
    }

    public interface Listener {
        /** 每收到一条带 pv 的 info 就回调一次（同一步棋会回调多次，深度递增）。 */
        void onCandidates(List<Candidate> candidates, int depth);

        void onError(String message);
    }

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;
    private Thread searchThread;
    private volatile boolean running;
    private volatile boolean searching;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile int threadCount = 2;

    public synchronized boolean isRunning() {
        return running && process != null;
    }

    public boolean isSearching() {
        return searching;
    }

    // ------------------------------------------------------------ 启动 / 关闭

    /**
     * 启动引擎并完成 UCI 握手。首次调用会把 50MB 神经网络从 assets 解压到私有目录。
     *
     * @return null 表示成功，否则返回失败原因
     */
    public synchronized String start(Context context) {
        if (running) {
            return null;
        }
        String enginePath = enginePath(context);
        if (enginePath == null) {
            return "找不到引擎文件（libpikafish.so），这台手机的 CPU 架构可能不受支持";
        }
        String nnuePath = ensureNnue(context);
        if (nnuePath == null) {
            return "神经网络文件解压失败，请检查存储空间";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(enginePath);
            pb.redirectErrorStream(true);
            pb.directory(context.getFilesDir());
            process = pb.start();
        } catch (IOException e) {
            process = null;
            return "无法启动引擎：" + e.getMessage();
        }

        running = true;
        lines.clear();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    lines.offer(line);
                }
            } catch (IOException ignored) {
                // 进程被关闭属正常情况
            }
        }, "pikafish-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        String err = handshake(nnuePath);
        if (err != null) {
            shutdown();
            return err;
        }
        return null;
    }

    public synchronized void shutdown() {
        running = false;
        searching = false;
        try {
            if (writer != null) {
                writer.write("quit\n");
                writer.flush();
            }
        } catch (IOException ignored) {
        }
        if (process != null) {
            try {
                process.waitFor(800, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            process.destroy();
            process = null;
        }
        writer = null;
        reader = null;
        lines.clear();
    }

    private String handshake(String nnuePath) {
        send("uci");
        if (!waitFor("uciok", 10_000)) {
            return "引擎没有响应（uciok 超时）";
        }
        int cores = Runtime.getRuntime().availableProcessors();
        threadCount = Math.max(1, Math.min(cores - 1, 6));
        send("setoption name EvalFile value " + nnuePath);
        send("setoption name Threads value " + threadCount);
        send("setoption name Hash value 128");
        send("isready");
        if (!waitFor("readyok", 15_000)) {
            return "引擎没有就绪（readyok 超时）";
        }
        return null;
    }

    // ------------------------------------------------------------ 分析

    /**
     * 分析一个局面。在独立线程执行，不阻塞调用方。
     *
     * @param depth   搜索深度，建议 12~20
     * @param multiPv 候选着法数量，1~5
     */
    public synchronized void analyze(final String fen, final int depth, final int multiPv,
                                     final Listener listener) {
        if (!running) {
            if (listener != null) {
                listener.onError("引擎未启动");
            }
            return;
        }
        stopSearch();

        searchThread = new Thread(() -> runSearch(fen, depth, multiPv, listener), "pikafish-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /** 让当前搜索立刻结束（引擎仍保持运行）。 */
    public synchronized void stopSearch() {
        if (!searching) {
            return;
        }
        searching = false;
        send("stop");
        Thread t = searchThread;
        if (t != null) {
            try {
                t.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        drainLines();
    }

    private void runSearch(String fen, int depth, int multiPv, Listener listener) {
        searching = true;
        drainLines();
        try {
            send("setoption name MultiPV value " + Math.max(1, Math.min(8, multiPv)));
            send("position fen " + fen);
            send("go depth " + depth);

            List<Candidate> latest = new ArrayList<>();
            int bestDepth = 0;

            while (running && searching) {
                String line = lines.poll(200, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                if (line.startsWith("bestmove")) {
                    searching = false;
                    if (listener != null && !latest.isEmpty()) {
                        listener.onCandidates(latest, bestDepth);
                    }
                    break;
                }
                if (!line.startsWith("info") || !line.contains(" pv ")) {
                    continue;
                }
                Candidate c = parseInfo(line);
                if (c == null || c.pv.isEmpty()) {
                    continue;
                }
                if (c.depth < bestDepth) {
                    continue;   // 丢弃旧深度的回包
                }
                bestDepth = c.depth;
                upsert(latest, c);
                if (listener != null) {
                    listener.onCandidates(latest, bestDepth);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            searching = false;
        }
    }

    private static void upsert(List<Candidate> list, Candidate c) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).rank == c.rank) {
                list.set(i, c);
                return;
            }
        }
        list.add(c);
        list.sort((a, b) -> Integer.compare(a.rank, b.rank));
    }

    private static Candidate parseInfo(String line) {
        String[] tok = line.split("\\s+");
        Candidate c = new Candidate();
        for (int i = 0; i < tok.length; i++) {
            switch (tok[i]) {
                case "depth":
                    if (i + 1 < tok.length) {
                        c.depth = parseInt(tok[i + 1], 0);
                    }
                    break;
                case "multipv":
                    if (i + 1 < tok.length) {
                        c.rank = Math.max(1, parseInt(tok[i + 1], 1));
                    }
                    break;
                case "score":
                    if (i + 2 < tok.length) {
                        if ("cp".equals(tok[i + 1])) {
                            c.scoreCp = parseInt(tok[i + 2], 0);
                        } else if ("mate".equals(tok[i + 1])) {
                            c.mate = true;
                            c.mateIn = parseInt(tok[i + 2], 0);
                        }
                    }
                    break;
                case "pv":
                    for (int k = i + 1; k < tok.length; k++) {
                        if (tok[k].length() >= 4) {
                            c.pv.add(tok[k]);
                        }
                    }
                    if (!c.pv.isEmpty()) {
                        c.uci = c.pv.get(0);
                    }
                    return c;
                default:
                    break;
            }
        }
        return c;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ------------------------------------------------------------ 底层

    private void send(String cmd) {
        try {
            writer.write(cmd);
            writer.write("\n");
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    private boolean waitFor(String prefix, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = lines.poll();
            if (line != null) {
                if (line.startsWith(prefix)) {
                    return true;
                }
                continue;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void drainLines() {
        lines.clear();
    }

    private static String enginePath(Context context) {
        try {
            File f = new File(context.getApplicationInfo().nativeLibraryDir, "libpikafish.so");
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 从 assets 解压神经网络到私有目录，已存在且大小正常则跳过。 */
    private static String ensureNnue(Context context) {
        File out = new File(context.getFilesDir(), "pikafish.nnue");
        if (out.exists() && out.length() > 40_000_000L) {
            return out.getAbsolutePath();
        }
        InputStream in = null;
        OutputStream os = null;
        try {
            in = context.getAssets().open("pikafish.nnue");
            os = new FileOutputStream(out);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
            return out.getAbsolutePath();
        } catch (IOException e) {
            out.delete();
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
