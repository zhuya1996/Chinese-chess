package com.xiangqi.overlay;

import android.app.Application;

/** 应用入口：安装全局崩溃日志，其余初始化都在各自组件里。 */
public final class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
