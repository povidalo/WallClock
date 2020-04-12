package ru.povidalo.dashboard.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class PreferencesController {
    public static boolean hasField(Context context, String name)
    {
        if (context == null)
            return false;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.contains(name);
    }

    public static void putLong(Context context, String name, long value)
    {
        if (context == null)
            return;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor ed = pref.edit();
        ed.putLong(name, value);
        ed.commit();
    }
    public static long getLong(Context context, String name, long defValue)
    {
        if (context == null)
            return defValue;

        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getLong(name, defValue);
    }
    public static void putBoolean(Context context, String name, boolean value)
    {
        if (context == null)
            return;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor ed = pref.edit();
        ed.putBoolean(name, value);
        ed.commit();
    }

    public static boolean getBoolean(Context context, String name, boolean defValue)
    {
        if (context == null)
            return defValue;

        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getBoolean(name, defValue);
    }

    public static int getInt(Context context, String name, int defValue)
    {
        if (context == null)
            return defValue;

        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getInt(name, defValue);
    }

    public static void putInt(Context context, String name, int value)
    {
        if (context == null)
            return;

        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor ed = pref.edit();
        ed.putInt(name, value);
        ed.commit();
    }
    public static String getString(Context context, String name, String defValue)
    {
        if (context == null)
            return defValue;

        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(name, defValue);
    }

    public static void putString(Context context, String name, String value)
    {
        if (context == null)
            return;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor ed = pref.edit();
        ed.putString(name, value);
        ed.commit();
    }

    public static void removeData(Context context, String name)
    {
        if (context == null)
            return;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor ed = pref.edit();
        ed.remove(name);
        ed.commit();
    }

}
