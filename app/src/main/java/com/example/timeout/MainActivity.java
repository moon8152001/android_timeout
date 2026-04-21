package com.example.timeout;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.Uri;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView tvTime;
    private Button btnStart;
    private ComponentName componentName;
    private DevicePolicyManager devicePolicyManager;

    private SharedPreferences sp;
    private boolean isCountdownRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTime = findViewById(R.id.tv_time);
        btnStart = findViewById(R.id.btn_start);
        componentName = new ComponentName(this, AdminReceiver.class);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        sp = getSharedPreferences("countdown_state", MODE_PRIVATE);

        // 按钮点击事件恢复正常
        btnStart.setOnClickListener(v -> showInputDialog());

        // 关于
        TextView tvAbout = findViewById(R.id.tv_about);
        tvAbout.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        });

        // 首次提示
        SharedPreferences spHint = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (!spHint.getBoolean("has_shown_hint", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("重要提示")
                    .setMessage("为保证锁屏功能正常生效，请在设置中将本APP设为【手动管理】，并开启：\n\n自启动、关联启动、后台活动")
                    .setPositiveButton("我知道了", (d, w) ->
                            spHint.edit().putBoolean("has_shown_hint", true).apply())
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatusAndUpdateUI();

        try {
            registerReceiver(receiver, new IntentFilter("COUNT_DOWN_TIME"));
        } catch (Exception e) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(receiver);
        } catch (Exception e) {}
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (CountdownService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void checkServiceStatusAndUpdateUI() {
        boolean serviceRunning = isServiceRunning();
        int lastRemain = sp.getInt("last_remaining", 0);

        if (serviceRunning) {
            isCountdownRunning = true;
            btnStart.setText("倒计时进行中");
            btnStart.setEnabled(false);
            tvTime.setText(String.format("%02d:%02d", lastRemain / 60, lastRemain % 60));
        } else {
            isCountdownRunning = false;
            sp.edit()
                    .putBoolean("is_running", false)
                    .putInt("last_remaining", 0)
                    .apply();

            btnStart.setText("开始倒计时");
            btnStart.setEnabled(true);
            tvTime.setText("00:00");
        }
    }

    private void showInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("例如：1 表示 1 分钟");
        input.setPadding(40, 30, 40, 30);
        input.setTextSize(18);
        input.setText("30");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
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

                        isCountdownRunning = true;
                        sp.edit()
                                .putBoolean("is_running", true)
                                .putInt("last_remaining", min * 60)
                                .apply();

                        btnStart.setText("倒计时进行中");
                        btnStart.setEnabled(false);

                        stopService(new Intent(this, CountdownService.class));
                        Intent service = new Intent(this, CountdownService.class);
                        service.putExtra("time", min * 60);
                        startService(service);

                        Toast.makeText(this, min + " 分钟倒计时开始", Toast.LENGTH_SHORT).show();

                    } catch (Exception e) {
                        Toast.makeText(this, "输入有效数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int remain = intent.getIntExtra("remaining", 0);
            sp.edit().putInt("last_remaining", remain).apply();
            tvTime.setText(String.format("%02d:%02d", remain / 60, remain % 60));

            if (remain <= 0) {
                checkServiceStatusAndUpdateUI();

                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow();
                }
                Toast.makeText(MainActivity.this, "时间到，已锁屏", Toast.LENGTH_SHORT).show();
            }
        }
    };

    public void goToSetting(View view) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "请前往：设置 → 应用 → 应用启动管理", Toast.LENGTH_LONG).show();
            }
        }
    }
}