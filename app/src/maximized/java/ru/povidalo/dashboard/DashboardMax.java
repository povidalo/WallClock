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
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import ru.povidalo.dashboard.DB.DatabaseHelper;
import ru.povidalo.dashboard.command.ICommand;

public class DashboardMax extends Dashboard {
    private static boolean heavyInitComplete = false;

    @Override
    public void onCreate() {
        super.onCreate();
        DatabaseHelper.getInstance();
    }

    public static boolean initHeavySingletons(final ICommand listener) {
        if (!heavyInitComplete) {
            AsyncTask task = new AsyncTask() {
                @Override
                protected Object doInBackground(Object[] params) {
                    DatabaseHelper.loadAllData();
                    return null;
                }

                @Override
                protected void onPostExecute(Object o) {
                    super.onPostExecute(o);
                    heavyInitComplete = true;
                    if (listener != null) {
                        listener.onCommandState(null, ICommand.CommandState.FINISHED);
                    }
                }
            };
            return false;
        } else {
            return true;
        }
    }
    
    public static long readCPUMaxFreq() {
        ProcessBuilder cmd;
        String result="";
        long MHz = -1;
        
        try{
            String[] args = {"/system/bin/cat", "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"};
            cmd = new ProcessBuilder(args);
            
            Process     process = cmd.start();
            InputStream in      = process.getInputStream();
            byte[]      re      = new byte[1024];
            while (true) {
                int bytes = in.read(re);
                if (bytes < 0) {
                    break;
                }
                result = result + new String(Arrays.copyOf(re, bytes));
            }
            in.close();
        } catch(IOException ex){
            ex.printStackTrace();
        }
        
        result = result.replaceAll("\n", "");
        
        try {
            MHz = Long.valueOf(result);
        } catch (Exception e) {
        
        }
        
        return MHz;
    }
}
