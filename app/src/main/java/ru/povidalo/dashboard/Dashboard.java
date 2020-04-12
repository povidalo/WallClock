package ru.povidalo.dashboard;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import ru.povidalo.dashboard.util.ApplicationExceptionHandler;

public class Dashboard extends Application {
    private static ConnectivityManager cm = null;
    private static Context appContext;

    private static boolean heavyInitComplete = false;

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.currentThread().setUncaughtExceptionHandler(
                new ApplicationExceptionHandler(Thread.currentThread()));

        appContext = this;
    }

    public static Context getContext() {
        return appContext;
    }

    public static int getVersionCode() {
        PackageManager pm = appContext.getPackageManager();
        PackageInfo pi;
        int buildCode;
        try {
            pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            buildCode = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            buildCode = 0;
        }
        return buildCode;
    }

    public static void showNotification(int id, String text) {
        NotificationManager mNM = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNM != null) {
            Notification.Builder builder = new Notification.Builder(getContext());
            builder.setContentTitle(getContext().getResources().getString(R.string.app_name));
            builder.setContentText(text);

            builder.setSmallIcon(R.drawable.ico);
            builder.setAutoCancel(false);

            mNM.notify(id, builder.getNotification());
        }
    }

    public static int getBuild() {
        PackageManager pm = getContext().getPackageManager();
        PackageInfo    pi;
        int buildCode = 0;
        try {
            pi = pm.getPackageInfo(getContext().getPackageName(), 0);
            buildCode = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            buildCode = 0;
        }
        return buildCode;
    }

    public static boolean isOnline(boolean withToast) {
        if (!isOnline()) {
            if (withToast) {
                Toast.makeText(getContext(), getContext().getString(R.string.check_internet).replaceAll(" *(<.*>)* *", " "), Toast.LENGTH_SHORT).show();
            }
            return false;
        } else {
            return true;
        }
    }

    public static boolean isOnline() {
        if (cm == null) {
            cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        if (cm == null) {
            return false;
        }

        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();

    }

    public static int screenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }
    
    public static int screenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }
}
