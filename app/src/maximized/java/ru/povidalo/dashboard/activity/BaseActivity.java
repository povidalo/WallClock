package ru.povidalo.dashboard.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;

import ru.povidalo.dashboard.service.CrawlerService;
import ru.povidalo.dashboard.service.CrawlerServiceConnection;


public abstract class BaseActivity extends AppCompatActivity {
    private static final String       WAKE_LOCK_TAG = "Dashboard_BaseActivity:WAKE_LOCK";
    final   Messenger                 mMessenger    = new Messenger(new IncomingHandler());
    private CrawlerServiceConnection  mBgConnection = new CrawlerServiceConnection(mMessenger);
    private PowerManager.WakeLock     wakeLock      = null;
    
    private void doBindServices() {
        bindService(new Intent(BaseActivity.this, CrawlerService.class), mBgConnection, Context.BIND_AUTO_CREATE);

        Intent watchdogIntent = new Intent();
        watchdogIntent.setComponent(new ComponentName("ru.povidalo.dashboard.watchdog",
                "ru.povidalo.dashboard.watchdog.service.WatchDogService"));
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(watchdogIntent);
        } else {
            startService(watchdogIntent);
        }
    }
    
    private void doUnbindServices() {
        if (mBgConnection.isBound()) {
            mBgConnection.unRegisterClient(mMessenger);
            unbindService(mBgConnection);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        doBindServices();
        checkWakeLock();
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
    
    @Override
    protected void onDestroy() {
        doUnbindServices();
        disableWakeLock();
        super.onDestroy();
    }
    
    protected abstract void onMessage(int msgCode, int value, String data);
    
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            onMessage(msg.what, msg.arg1, msg.getData() != null ? msg.getData().getString("data") : null);
        }
    }
}
