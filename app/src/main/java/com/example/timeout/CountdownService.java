package com.example.timeout;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import android.content.SharedPreferences;

public class CountdownService extends Service {
    private int totalTime;
    private Handler handler;
    private Runnable runnable;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName componentName;

    private static final String CHANNEL_ID = "timeout_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        componentName = new ComponentName(this, AdminReceiver.class);

        createChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("倒计时锁屏中")
                .setContentText("后台运行中，结束将自动锁屏")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(100, notification);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "倒计时后台服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            totalTime = intent.getIntExtra("time", 0);
        }

        if (runnable != null) handler.removeCallbacks(runnable);

        runnable = new Runnable() {
            @Override
            public void run() {
                if (totalTime > 0) {
                    totalTime--;
                    Intent intent = new Intent("COUNT_DOWN_TIME");
                    intent.putExtra("remaining", totalTime);
                    sendBroadcast(intent);
                    handler.postDelayed(this, 1000);
                } else {

                    // ==============================
                    // ✅ 倒计时结束 = 强制重置状态
                    // ==============================
                    SharedPreferences sp = getSharedPreferences("countdown_state", MODE_PRIVATE);
                    sp.edit()
                            .putBoolean("is_running", false)
                            .putInt("last_remaining", 0)
                            .apply();

                    tryLock();
                    stopForeground(true);
                    stopSelf();
                }
            }
        };
        handler.post(runnable);

        return START_STICKY;
    }

    private void tryLock() {
        if (devicePolicyManager != null && devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow();
            Toast.makeText(this, "已锁屏", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (runnable != null) handler.removeCallbacks(runnable);
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}