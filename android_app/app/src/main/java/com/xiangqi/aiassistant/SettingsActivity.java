package com.xiangqi.aiassistant;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 设置界面 - 未来功能预留
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 临时提示
        Toast.makeText(this, "设置功能即将推出", Toast.LENGTH_SHORT).show();

        // 返回主界面
        finish();
    }
}
