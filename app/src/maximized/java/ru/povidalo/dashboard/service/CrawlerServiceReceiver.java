package ru.povidalo.dashboard.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CrawlerServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            Intent seviceIntent = new Intent(context, CrawlerService.class);
            seviceIntent.setData(intent.getData());
            seviceIntent.setAction(intent.getAction());
            seviceIntent.putExtras(intent);
            context.startService(seviceIntent);
        }
    }
}