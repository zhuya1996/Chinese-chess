package com.xiangqi.overlay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * 厂商与系统版本差异的集中处理。
 * 目标是「所有安卓手机都能用」：能自动适配的自动适配，
 * 必须用户手动开的，给出对应的跳转路径和文字说明。
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

    public static boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(ctx);
        }
        return true; // API 21/22 装上即有，无需运行时申请
    }

    public static Intent overlaySettingsIntent(Context ctx) {
        if (Build.VERSION.SDK_INT >= 23) {
            return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
        }
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.getPackageName()));
    }

    public static Intent appDetailsIntent(Context ctx) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.getPackageName()));
    }

    /** 这台手机是否带 arm64（当前唯一打包了引擎的架构）。 */
    public static boolean hasArm64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    /** 这台手机是否只支持 32 位（此时引擎跑不起来，需要明确提示而不是崩溃）。 */
    public static boolean is32BitOnly() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 预编译引擎（Pikafish-Android-arm64-universal）二进制声明的最低系统版本。
     * 通过解析 ELF 的 .note.android.ident 段得到：API 29 = Android 10。
     * <p>
     * 注意这是<b>引擎文件</b>的限制，不是 App 代码的限制 ——
     * 本 App 按 minSdk 21 编写，将来用 NDK 以更低 API 重编引擎即可支持 Android 5~9。
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
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    public static String manufacturer() {
        String m = Build.MANUFACTURER;
        return m == null ? "" : m.toLowerCase();
    }

    /** 各厂商的后台保活设置说明，按品牌给出最短路径。 */
    public static String keepAliveHint() {
        String m = manufacturer();
        if (m.contains("xiaomi") || m.contains("redmi")) {
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

    /** 各厂商悬浮窗权限在设置里的位置说明。 */
    public static String overlayHint() {
        String m = manufacturer();
        if (m.contains("xiaomi") || m.contains("redmi")) {
            return "小米/红米：除「显示在其他应用上层」外，还需开启「后台弹出界面」，"
                    + "否则悬浮窗可能不显示。";
        }
        if (m.contains("huawei") || m.contains("honor")) {
            return "华为/荣耀：设置 → 应用 → 应用管理 → 象棋助手 → 权限 → 悬浮窗。";
        }
        return "在系统设置中授予本应用「显示在其他应用上层」权限。";
    }
}
