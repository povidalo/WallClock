package ru.povidalo.dashboard.watchdog.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;

public class WatchDogServiceConnection implements ServiceConnection {
    private Messenger mService = null;
    private boolean mBound = false;

    public WatchDogServiceConnection() {

    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = new Messenger(service);
        mBound = true;
    }

    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mBound = false;
    }

    public boolean isBound() {
        return mBound;
    }

    public Messenger getService() {
        return mService;
    }
}