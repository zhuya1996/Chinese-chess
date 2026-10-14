package com.xiangqi.overlay;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * 厂商与系统版本差异的集中处理。
 * <p>
 * 设计原则（很重要）：
 * 「权限检测」在国产 ROM 上是<b>不可靠</b>的，同一个权限用不同 API 查可能得到不同答案。
 * 所以这里的检测结果<b>只用来给用户提示</b>，绝不用来阻断流程 ——
 * 真正的判据是实际操作（addView）能不能成功。历史上就因为把检测当成了硬门槛，
 * 导致用户明明授权了按钮却点不动。
 */
public final class DeviceCompat {

    private DeviceCompat() {
    }

    /** 悬浮窗窗口类型：API 26 起必须用 TYPE_APPLICATION_OVERLAY。 */
    public static int overlayWindowType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ---------------------------------------------------------------- 悬浮窗权限

    /**
     * 是否已授予悬浮窗权限。
     * <p>
     * 同时用两套机制判断，任一认为已授权即返回 true：
     * <ol>
     *   <li>{@link Settings#canDrawOverlays} —— 官方 API，但在部分 ROM 上会误报 false</li>
     *   <li>AppOpsManager 的 system_alert_window 操作 —— 更接近系统真实状态</li>
     * </ol>
     */
    public static boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) {
            return true; // API 21/22 装上即有，无需运行时申请
        }
        try {
            if (Settings.canDrawOverlays(ctx)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return appOpsAllows(ctx);
    }

    /** 用 AppOpsManager 复核一次，绕开 Settings.canDrawOverlays 的误报。 */
    private static boolean appOpsAllows(Context ctx) {
        int mode = appOpsMode(ctx);
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** 返回 AppOps 中 system_alert_window 的原始模式值，-1 表示查询失败。 */
    private static int appOpsMode(Context ctx) {
        try {
            AppOpsManager ops =
                    (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return -1;
            }
            String op = AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
            int uid = Process.myUid();
            String pkg = ctx.getPackageName();
            if (Build.VERSION.SDK_INT >= 29) {
                return ops.unsafeCheckOpNoThrow(op, uid, pkg);
            }
            return ops.checkOpNoThrow(op, uid, pkg);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 打开悬浮窗权限设置页。
     * <p>
     * 小米 / 华为等 ROM 上 {@code ACTION_MANAGE_OVERLAY_PERMISSION} 有可能被重定向或
     * 根本不存在，所以逐级降级：专用页 → 应用详情页 → 全局设置页。
     *
     * @return 是否成功打开
     */
    public static boolean openOverlaySettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= 23) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
            if (tryStart(ctx, i)) {
                return true;
            }
            // 部分 ROM 不接受带 package 的形式，退回不带参数的
            if (tryStart(ctx, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
                return true;
            }
        }
        return openAppDetails(ctx);
    }

    /** 打开本应用的系统详情页。 */
    public static boolean openAppDetails(Context ctx) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.getPackageName()));
        if (tryStart(ctx, i)) {
            return true;
        }
        return tryStart(ctx, new Intent(Settings.ACTION_SETTINGS));
    }

    /**
     * 打开 MIUI / HyperOS 的应用权限编辑页 —— 这里是"后台弹出界面"等
     * 小米私有权限的所在地。非小米机型返回 false。
     */
    public static boolean openMiuiPermissionEditor(Context ctx) {
        String[][] targets = {
                {"com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"},
                {"com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"},
        };
        for (String[] t : targets) {
            try {
                Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
                i.setComponent(new ComponentName(t[0], t[1]));
                i.putExtra("extra_pkgname", ctx.getPackageName());
                if (tryStart(ctx, i)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean tryStart(Context ctx, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 架构与系统版本

    public static boolean hasArm64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    public static boolean is32BitOnly() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) {
                return false;
            }
        }
        return true;
    }

    public static String abiList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Build.SUPPORTED_ABIS[i]);
        }
        return sb.toString();
    }

    /**
     * 预编译引擎二进制声明的最低系统版本（解析 ELF 的 .note.android.ident 得到）。
     * 注意这是<b>引擎文件</b>的限制，不是 App 代码的限制。
     */
    public static final int ENGINE_MIN_API = 29;

    public static boolean engineSupported() {
        return Build.VERSION.SDK_INT >= ENGINE_MIN_API;
    }

    public static String androidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static boolean ignoringBatteryOptimizations(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 申请电池优化白名单；部分 ROM 会拦截，失败时退回应用详情页。 */
    public static void requestIgnoreBatteryOptimizations(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.getPackageName()));
            ctx.startActivity(i);
            return;
        } catch (Throwable ignored) {
        }
        openAppDetails(ctx);
    }

    public static String manufacturer() {
        String m = Build.MANUFACTURER;
        return m == null ? "" : m.toLowerCase();
    }

    public static boolean isXiaomi() {
        String m = manufacturer();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    // ---------------------------------------------------------------- 诊断信息

    /**
     * 生成一份完整的诊断文本。
     * <p>
     * 用户遇到问题时可以把这段内容截图反馈，从而精确定位到底是哪一环没通过，
     * 而不是只能笼统地说「权限都给了还是不行」。
     */
    public static String diagnostics(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("== 设备 ==\n");
        sb.append("App 版本：").append(appVersion(ctx)).append('\n');
        sb.append("厂商/型号：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("系统：").append(androidVersion()).append('\n');
        sb.append("CPU 架构：").append(abiList()).append('\n');
        sb.append("引擎可运行：").append(engineSupported() ? "是" : "否（需 Android 10+）").append('\n');

        sb.append("\n== 权限（原始检测值）==\n");
        boolean settingsSays = false;
        try {
            settingsSays = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
        } catch (Throwable ignored) {
        }
        sb.append("canDrawOverlays()：").append(settingsSays).append('\n');
        sb.append("AppOps system_alert_window：")
                .append(appOpsModeName(appOpsMode(ctx))).append('\n');
        sb.append("→ 综合判定：")
                .append(canDrawOverlays(ctx) ? "已授权" : "未授权（若你已手动开启，忽略此项，以实际能否显示为准）")
                .append('\n');
        sb.append("通知权限：").append(hasNotificationPermission(ctx) ? "有" : "无").append('\n');
        sb.append("电池优化白名单：")
                .append(ignoringBatteryOptimizations(ctx) ? "已加入" : "未加入").append('\n');

        sb.append("\n== 运行状态 ==\n");
        OverlayService s = OverlayService.get();
        if (s == null) {
            sb.append("后台服务：未创建\n");
        } else {
            sb.append("后台服务：已创建\n");
            sb.append("截屏运行中：").append(s.isCaptureRunning() ? "是" : "否").append('\n');
            sb.append("识别已启动：").append(s.isActive() ? "是" : "否").append('\n');
            sb.append("已标定：").append(s.isCalibrated() ? "是" : "否").append('\n');
            sb.append("引擎状态：").append(s.isEngineReady() ? "就绪" : "未启动").append('\n');
            sb.append("悬浮窗：").append(s.isOverlayShown() ? "已显示" : "未显示").append('\n');
            String last = s.getLastError();
            if (last != null && last.length() > 0) {
                sb.append("最近一次错误：").append(last).append('\n');
            }
        }
        return sb.toString();
    }

    /** 读取本应用的版本号，用于区分用户手上测的是哪一版。 */
    private static String appVersion(Context ctx) {
        try {
            android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            return "未知";
        }
    }

    private static String appOpsModeName(int mode) {
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
                return "ALLOWED（0，已授权）";
            case AppOpsManager.MODE_IGNORED:
                return "IGNORED（1，被拒绝）";
            case AppOpsManager.MODE_ERRORED:
                return "ERRORED（2）";
            case AppOpsManager.MODE_DEFAULT:
                return "DEFAULT（3，未显式设置）";
            default:
                return "查询失败（-1）";
        }
    }

    public static boolean hasNotificationPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        try {
            return ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 厂商保活说明

    public static String keepAliveHint() {
        String m = manufacturer();
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return "小米/红米：设置 → 应用设置 → 应用管理 → 象棋助手 → 省电策略 → 选「无限制」；"
                    + "再在最近任务里下拉本应用卡片并点锁形图标，防止被清理。";
        }
        if (m.contains("huawei") || m.contains("honor")) {
            return "华为/荣耀：设置 → 应用 → 应用启动管理 → 象棋助手 → 关闭「自动管理」，"
                    + "把「允许自启动 / 允许关联启动 / 允许后台活动」三项全部打开。";
        }
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            return "OPPO/一加/realme：设置 → 电池 → 更多设置 → 象棋助手 → 允许后台运行；"
                    + "并在最近任务里下拉锁定本应用。";
        }
        if (m.contains("vivo") || m.contains("iqoo")) {
            return "vivo/iQOO：设置 → 电池 → 后台耗电管理 → 象棋助手 → 允许后台高耗电；"
                    + "并在最近任务里锁定本应用。";
        }
        if (m.contains("samsung")) {
            return "三星：设置 → 电池 → 后台使用限制 → 把「象棋助手」从睡眠应用中移出。";
        }
        return "请在系统设置中把本应用加入电池优化白名单（或允许后台运行），"
                + "否则切到象棋 App 后识别可能被系统中断。";
    }

    /** 悬浮窗权限在设置里的位置说明。 */
    public static String overlayHint() {
        String m = manufacturer();
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return "小米/红米：除了「显示在其他应用上层」，还要开「后台弹出界面」，"
                    + "否则可能出现「已授权但仍不显示悬浮窗」。";
        }
        if (m.contains("huawei") || m.contains("honor")) {
            return "华为/荣耀：设置 → 应用 → 应用管理 → 象棋助手 → 权限 → 悬浮窗。";
        }
        return "在系统设置中授予本应用「显示在其他应用上层」权限。";
    }
}
