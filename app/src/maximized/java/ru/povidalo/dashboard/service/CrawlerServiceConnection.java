package ru.povidalo.dashboard.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import ru.povidalo.dashboard.util.Utils;

public class CrawlerServiceConnection implements ServiceConnection {
    private Messenger mService = null;
    private boolean mBound = false;
    private final Messenger client;
    
    public CrawlerServiceConnection(Messenger client) {
        this.client = client;
    }
    
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = new Messenger(service);
        mBound = true;
        registerClient(client);
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
    
    public void registerClient(Messenger client) {
        if (isBound()) {
            try {
                Message msg = Message.obtain(null, CrawlerService.MSG_REGISTER_CLIENT);
                msg.replyTo = client;
                mService.send(msg);
            }
            catch (RemoteException e) {
                Utils.logError(e);
            }
        }
    }
    
    public void unRegisterClient(Messenger client) {
        if (isBound()) {
            try {
                Message msg = Message.obtain(null, CrawlerService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = client;
                mService.send(msg);
            }
            catch (RemoteException e) {
                Utils.logError(e);
            }
        }
    }
}

