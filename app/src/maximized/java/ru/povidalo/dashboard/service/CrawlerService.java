package ru.povidalo.dashboard.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import ru.povidalo.dashboard.command.Command;
import ru.povidalo.dashboard.command.CommandController;
import ru.povidalo.dashboard.command.ICommand;
import ru.povidalo.dashboard.command.ProtocolError;
import ru.povidalo.dashboard.command.SunLoaderCommand;
import ru.povidalo.dashboard.util.Utils;

public class CrawlerService extends Service {
    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_DOWNLOADER_FINISHED = 3;
    
    private volatile boolean         screenOn                                 = false;
    private BroadcastReceiver        screenStateListener                      = null;
    private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
    private double[] timeToDownloadSun = {2.5};
    
    
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    @Override
    public void onCreate() {
        super.onCreate();

        screenStateListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    screenOn = false;
                } else {
                    screenOn = true;
                }
                screenStateChanged();
            }
        };

        IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateListener, screenIntentFilter);

        CommandController.cancelAllDelayedCommandsOfType(SunLoaderCommand.class);
        new SunLoaderCommand(dowloaderListener).execute();
    }
    
    private void startSunLoader() {
        List<Long> delay = new ArrayList<>();
        for (int i = 0; i < timeToDownloadSun.length; i++) {
            delay.add(getDelayTillNextHour(timeToDownloadSun[i]));
        }
        Collections.sort(delay);
        Utils.log("Next Sun update scheduled in "+(delay.isEmpty() ? 10 : ((double) delay.get(0))/Utils.ONE_HOUR)+" hours");
        CommandController.cancelAllDelayedCommandsOfType(SunLoaderCommand.class);
        new SunLoaderCommand(dowloaderListener).executeDelayed(delay.isEmpty() ? 10*Utils.ONE_HOUR : delay.get(0));
    }

    private long getDelayTillNextHour(double time) {
        Calendar calendar = Calendar.getInstance();
        double timeNow = calendar.get(Calendar.HOUR_OF_DAY)+calendar.get(Calendar.MINUTE)/60.0;

        return (long) ((time - timeNow + ((timeNow < time)?0:24))*Utils.ONE_HOUR);
    }

    @Override
    public void onDestroy() {
        screenOn = false;

        if (screenStateListener != null) {
            try {
                unregisterReceiver(screenStateListener);
            } catch (Exception e) {

            }
        }
        screenStateListener = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            screenOn = false;
            screenStateChanged();
        } else if (intent != null && Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            screenOn = true;
            screenStateChanged();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        screenOn = true;
        return mMessenger.getBinder();
    }

    private void screenStateChanged() {

    }
    
    public class MovieUpdatedEvent {
        private final Utils.DownloaderType type;
        
        public MovieUpdatedEvent(Utils.DownloaderType type) {
            this.type = type;
        }
    
        public Utils.DownloaderType getType() {
            return type;
        }
    }
    
    private ICommand dowloaderListener = new ICommand() {
        @Override
        public boolean onCommandProgress(Command command, int progress) {
            return false;
        }
    
        @Override
        public boolean onCommandState(Command command, CommandState state) {
            if (state == CommandState.SUCCESS) {
                if (command instanceof SunLoaderCommand) {
                    sendMessageToUI(MSG_DOWNLOADER_FINISHED, Utils.DownloaderType.SUN.ordinal(),
                            ((SunLoaderCommand) command).getFileName());
                }
            }
            if (state == CommandState.FINISHED) {
                if (command instanceof SunLoaderCommand) {
                    startSunLoader();
                }
            }
            return false;
        }
    
        @Override
        public boolean onCommandError(Command command, ProtocolError error) {
            return false;
        }
    };
    
    private void sendMessageToUI(int messageCode, int value, String data) {
        for (int i = mClients.size()-1; i >= 0; i--) {
            try {
                Message m = Message.obtain(null, messageCode, value, 0);
                Bundle extras = new Bundle();
                extras.putString("data", data);
                m.setData(extras);
                mClients.get(i).send(m);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }
}
