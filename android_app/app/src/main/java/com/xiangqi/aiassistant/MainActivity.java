package com.xiangqi.aiassistant;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主界面 - 配置服务器和启动悬浮窗
 */
public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final String PREFS_NAME = "XiangqiAI";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER = "http://192.168.1.100:5000";

    private EditText serverUrlInput;
    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private TextView statusText;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        loadSettings();
        updateStatus();
    }

    private void initViews() {
        serverUrlInput = findViewById(R.id.serverUrlInput);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);

        startButton.setOnClickListener(v -> startFloatingWindow());
        stopButton.setOnClickListener(v -> stopFloatingWindow());
        settingsButton.setOnClickListener(v -> openSettings());
    }

    private void loadSettings() {
        String savedUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER);
        serverUrlInput.setText(savedUrl);
    }

    private void saveSettings() {
        String url = serverUrlInput.getText().toString().trim();
        if (!url.isEmpty()) {
            prefs.edit().putString(KEY_SERVER_URL, url).apply();
        }
    }

    private void startFloatingWindow() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 请求权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
                return;
            }
        }

        // 保存设置
        saveSettings();

        // 启动悬浮窗服务
        String serverUrl = serverUrlInput.getText().toString().trim();
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        serviceIntent.putExtra("server_url", serverUrl);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void stopFloatingWindow() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "悬浮窗已停止", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void updateStatus() {
        boolean isRunning = FloatingWindowService.isRunning();
        if (isRunning) {
            statusText.setText("状态: 运行中 🟢");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            statusText.setText("状态: 已停止 ⚪");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // 权限已授予，重新启动
                    startFloatingWindow();
                } else {
                    Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
