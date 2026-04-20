package com.example.timeout;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends Activity {
    private TextView tvTime;
    private Button btnStart;
    private ComponentName componentName;
    private DevicePolicyManager devicePolicyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTime = findViewById(R.id.tv_time);
        btnStart = findViewById(R.id.btn_start);
        componentName = new ComponentName(this, AdminReceiver.class);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        tvTime.setText("00:00");
        btnStart.setOnClickListener(v -> showInputDialog());
    }

    private void showInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("例如：1 表示 1 分钟");
        input.setPadding(40, 30, 40, 30);
        input.setTextSize(18);
        // 默认30分钟
        input.setText("30");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置倒计时")
                .setMessage("输入分钟数")
                .setView(input)
                .setPositiveButton("开始", (d, w) -> {
                    try {
                        int min = Integer.parseInt(input.getText().toString().trim());
                        if (min <= 0) min = 1;

                        if (!devicePolicyManager.isAdminActive(componentName)) {
                            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "必须开启锁屏权限");
                            startActivity(intent);
                            return;
                        }

                        btnStart.setText("倒计时进行中");
                        btnStart.setEnabled(false);

                        Intent service = new Intent(this, CountdownService.class);
                        service.putExtra("time", min * 60);
                        startService(service);

                        Toast.makeText(this, min + " 分钟倒计时开始", Toast.LENGTH_SHORT).show();

                    } catch (Exception e) {
                        Toast.makeText(this, "输入有效数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
        dialog.show();
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int remain = intent.getIntExtra("remaining", 0);
            tvTime.setText(String.format("%02d:%02d", remain / 60, remain % 60));

            if (remain <= 0) {
                btnStart.setText("开始倒计时");
                btnStart.setEnabled(true);

                // 只保留：倒计时结束 → 自动锁屏
                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow();
                }
                Toast.makeText(MainActivity.this, "时间到，已锁屏", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter("COUNT_DOWN_TIME"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
}