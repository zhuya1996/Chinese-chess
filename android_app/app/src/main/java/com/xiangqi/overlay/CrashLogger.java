package com.xiangqi.overlay;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 全局崩溃日志。
 * <p>
 * 为什么需要它：真机上的闪退用户只能描述「一点就退」，没有堆栈就无从定位。
 * 这里把未捕获异常的完整堆栈写入 filesDir/crash.log，主界面「诊断信息」
 * 会把它带出来，一次反馈就能拿到确切原因。
 * <p>
 * 记录完仍然调用系统默认处理器，崩溃行为保持 Android 原生（照常闪退），
 * 不做「吞掉异常假装没崩」的事 —— 那会让 App 处于未定义状态。
 */
public final class CrashLogger {

    private static File file;

    private CrashLogger() {
    }

    public static void install(Context context) {
        file = new File(context.getFilesDir(), "crash.log");
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                StringBuilder sb = new StringBuilder();
                sb.append("时间：").append(new java.util.Date().toString()).append('\n');
                sb.append("线程：").append(thread.getName()).append('\n');
                sb.append(sw.toString());
                try (FileWriter w = new FileWriter(file, false)) {
                    w.write(sb.toString());
                }
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    /** 最近一次崩溃的堆栈；从未崩溃过返回 null。 */
    public static String lastCrash() {
        if (file == null || !file.exists() || file.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            int lines = 0;
            while ((line = r.readLine()) != null && lines < 60) {
                sb.append(line).append('\n');
                lines++;
            }
        } catch (Throwable t) {
            return null;
        }
        String text = sb.toString();
        return text.length() == 0 ? null : text;
    }

    public static void clear() {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
