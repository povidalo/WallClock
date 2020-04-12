package ru.povidalo.dashboard.watchdog.service;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import ru.povidalo.dashboard.watchdog.R;

public class WatchDogService extends Service {
    private static final String                      WAKE_LOCK_TAG = "DashboardWatchDog:WAKE_LOCK";
    private static final String                      DASHBOARD_NOTIFICATION_CHANNEL = "DashboardWatchDogNotification";
    private static final String                      TAG = "DashboardWatchdog";
    private static final String                      TARGET_PACKAGE = "ru.povidalo.dashboard";
    private              PowerManager.WakeLock       wakeLock      = null;
    private Timer timer = null;

    @Override
    public void onCreate() {
        super.onCreate();

        checkWakeLock();

        setupNotification();

        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAppIsRunning();
            }
        }, 60000L, 2*60000L);
        scheduleReboot(false);
    }

    private void scheduleReboot(boolean soon) {
        Calendar calendar = Calendar.getInstance();
        if (soon) {
            calendar.add(Calendar.MINUTE, 2);
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 5);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        try {
            Log.w(TAG, "Performing su request to resolve rights");
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
            DataInputStream inputStream = new DataInputStream(su.getInputStream());
            outputStream.writeBytes("sleep 1\n");
            outputStream.flush();
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();
            Scanner s = new Scanner(inputStream).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            Log.w(TAG, "SU cmd exited with code "+su.exitValue()+" and sent: "+result);
        } catch (Exception e) {
            Log.e(TAG, "SU cmd error", e);
        }
        Log.w(TAG, "Reboot scheduled at "+calendar.getTime());
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Log.w(TAG, "Performing reboot");
                    Process su = Runtime.getRuntime().exec("su");
                    DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
                    DataInputStream inputStream = new DataInputStream(su.getInputStream());
                    outputStream.writeBytes("sleep 3\n");
                    outputStream.flush();
                    outputStream.writeBytes("reboot now\n");
                    outputStream.flush();
                    outputStream.writeBytes("exit\n");
                    outputStream.flush();
                    su.waitFor();
                    Scanner s = new Scanner(inputStream).useDelimiter("\\A");
                    String result = s.hasNext() ? s.next() : "";
                    Log.w(TAG, "Reboot SU cmd exited with code "+su.exitValue()+" and sent: "+result);
                    scheduleReboot(true);
                } catch (Exception e) {
                    Log.e(TAG, "Reboot error", e);
                    scheduleReboot(true);
                }
            }
        }, calendar.getTime());
    }

    private Intent getLaunchingIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setPackage(TARGET_PACKAGE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        return intent;
    }

    private void setupNotification() {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, getLaunchingIntent(), 0);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            //create channel for Oreo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel nc = new NotificationChannel(DASHBOARD_NOTIFICATION_CHANNEL, DASHBOARD_NOTIFICATION_CHANNEL,
                        NotificationManager.IMPORTANCE_DEFAULT);
                nc.enableVibration(false);
                manager.createNotificationChannel(nc);
            }

            // build notification
            Notification notification = new NotificationCompat.Builder(this, DASHBOARD_NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.eye)  // the status icon
                    .setWhen(System.currentTimeMillis())  // the time stamp
                    .setContentTitle(getText(R.string.app_name))  // the label of the entry
                    .setContentText("")  // the contents of the entry
                    .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                    .setAutoCancel(false)
                    .build();

            startForeground(R.string.app_name, notification);
        }

        checkWakeLock();
    }

    private boolean appFoundOnLastPass = false;
    private void checkAppIsRunning() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (!powerManager.isInteractive()) {
            disableWakeLock();
            checkWakeLock();
        }

        if (needPermissionForBlocking()) {
            Log.w(TAG, "No permission. Open Settings app");
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        String currentApp = "";
        UsageStatsManager usm = (UsageStatsManager)this.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,  time - 1000*1000, time);
        if (appList != null && appList.size() > 0) {
            SortedMap<Long, UsageStats> mySortedMap = new TreeMap<Long, UsageStats>();
            for (UsageStats usageStats : appList) {
                mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!mySortedMap.isEmpty()) {
                currentApp = mySortedMap.get(mySortedMap.lastKey()).getPackageName();
            }
        }

        if (!TARGET_PACKAGE.equals(currentApp)) {
            Log.w(TAG, "Main activity not present! Latest app: "+currentApp);
            if (!appFoundOnLastPass) {
                Log.w(TAG, "Starting main activity!");
                startActivity(getLaunchingIntent());
            }
            appFoundOnLastPass = !appFoundOnLastPass;
        } else {
            Log.w(TAG, "all ok. we are online");
            appFoundOnLastPass = true;
        }
    }

    private boolean needPermissionForBlocking(){
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return  (mode != AppOpsManager.MODE_ALLOWED);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    @Override
    public void onDestroy() {
        disableWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void disableWakeLock() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        }
    }

    private void checkWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, WAKE_LOCK_TAG);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }
}
