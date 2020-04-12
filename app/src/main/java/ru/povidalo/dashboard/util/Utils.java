package ru.povidalo.dashboard.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorMatrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Looper;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.ColorInt;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.povidalo.dashboard.Dashboard;

public class Utils {
    public static final long ONE_SECOND = 1000;
    public static final long ONE_MINUTE = 60 * ONE_SECOND;
    public static final long ONE_HOUR = 60 * ONE_MINUTE;
    public static final long ONE_DAY = 24 * ONE_HOUR;
    
    public static int DEFAULT_BG_THREAD_PRIORITY = Thread.MIN_PRIORITY+1;
    
    public static final boolean LOGS_ENABLED = true;
    private static final boolean LOG_TO_FILE = true;
    private static boolean disableFileLogging = false;
    private static final String APPLICATION_TAG = "ru.povidalo.dashboard";
    private static long timeStart = System.currentTimeMillis();
    
    private static String DEVICE_ID_PREFERNCE = "DEVICE_ID";
    
    private static final String LOGGIING_FILE = "/" + APPLICATION_TAG + ".log";
    
    public static Typeface akrobatRegular = null, robotoLight = null, robotoRegular = null, robotoBold = null, courierPrimeSansBold = null;
    
    public static final ColorMatrix grayScale = new ColorMatrix(new float[]
            {
                    0.213f, 0.715f, 0.072f, 0, 100f,
                    0.213f, 0.715f, 0.072f, 0, 100f,
                    0.213f, 0.715f, 0.072f, 0, 100f,
                    0, 0, 0, 0.5f, 0
            }); //matrix for #afafaf filter with 50% opacity
    
    static {
        if (akrobatRegular == null) {
            akrobatRegular = Typeface.createFromAsset(Dashboard.getContext().getAssets(), "fonts/akrobat_regular.otf");
        }
        if (robotoLight == null) {
            robotoLight = Typeface.createFromAsset(Dashboard.getContext().getAssets(), "fonts/font_roboto_light.ttf");
        }
        if (robotoRegular == null) {
            robotoRegular = Typeface.createFromAsset(Dashboard.getContext().getAssets(), "fonts/font_roboto_regular.ttf");
        }
        if (robotoBold == null) {
            robotoBold = Typeface.createFromAsset(Dashboard.getContext().getAssets(), "fonts/font_roboto_bold.ttf");
        }
        if(courierPrimeSansBold == null) {
            courierPrimeSansBold = Typeface.createFromAsset(Dashboard.getContext().getAssets(), "fonts/courier_prime_sans_bold.ttf");
        }
    }
    
    // Logging
    public static void log(String message) {
        if (message != null && LOGS_ENABLED) {
            // message += formStackTrace(Thread.currentThread().getStackTrace());
            Log.d(APPLICATION_TAG, message);
            if (LOGS_ENABLED && LOG_TO_FILE) {
                logToFile(message);
            }
        }
    }
    
    public static void logWithPost(String message) {
        if (message != null) {
            log(message);
        }
    }
    
    public static void logTimedStart(String message) {
        if (LOGS_ENABLED) {
            timeStart = System.currentTimeMillis();
            log(message);
        }
    }
    
    public static void logTimed(String message) {
        if (LOGS_ENABLED) {
            log(message + " Time passed: " + (System.currentTimeMillis() - timeStart));
        }
    }
    
    public static void log(Object message) {
        if (message != null && LOGS_ENABLED) {
            log(message.toString());
        }
    }
    
    public static void logWithPost(Object message) {
        if (message != null && LOGS_ENABLED) {
            logWithPost(message.toString());
        }
    }
    
    public static void logError(String message) {
        logError(message, null);
    }
    
    public static void logError(Throwable e) {
        logError("ERROR", e);
    }
    
    public static void logError(String message, Throwable e) {
        if (e != null) {
            //Log.e(APPLICATION_TAG, message, e);
            Log.e(APPLICATION_TAG, message + formStackTrace(e));
            saveToBuffer(message, e);
        } else {
            if (LOGS_ENABLED) {
                message += formStackTrace(Thread.currentThread().getStackTrace());
            }
            Log.e(APPLICATION_TAG, message);
        }
        if (LOGS_ENABLED && LOG_TO_FILE) {
            if (e != null) {
                message += formStackTrace(e);
            }
            logToFile(message);
        }
    }
    
    @SuppressWarnings("unused")
    private static void logToFile(String message) {
        if (LOGS_ENABLED && LOG_TO_FILE && !disableFileLogging) {
            SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            message = dt.format(new Date()) + ": " + message;
            File dir = Dashboard.getContext().getExternalCacheDir();
            String filePath;
            if (dir != null) {
                dir.mkdirs();
                filePath = dir + LOGGIING_FILE;
            } else {
                filePath = "/sdcard/" + LOGGIING_FILE;
            }
            try {
                FileOutputStream fOut = new FileOutputStream(filePath, true);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                myOutWriter.append(message);
                myOutWriter.append("\n");
                myOutWriter.close();
                fOut.close();
            } catch (Exception e) {
                Log.e(APPLICATION_TAG, "Unable to open file for logging", e);
                disableFileLogging = true;
            }
        }
    }
    
    private static String formStackTrace(StackTraceElement[] trace) {
        if (trace == null) {
            return "";
        }
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement el : trace) {
            stackTrace.append("\n");
            stackTrace.append("    at " + el.getClassName() + '.' + el.getMethodName() + '(' + el.getFileName() + ':' + el
                    .getLineNumber() + ')');
        }
        return stackTrace.toString();
    }
    
    public static String formStackTrace(Throwable e) {
        StringBuilder stackTrace = new StringBuilder();
        stackTrace.append("\n" + e.getClass().getName() + ": " + e.getLocalizedMessage());
        stackTrace.append(formStackTrace(e.getStackTrace()));
        Throwable cause = e.getCause();
        while (cause != null) {
            stackTrace.append("\nCaused by: " + e.getClass().getName() + ": " + cause.getLocalizedMessage());
            stackTrace.append(formStackTrace(cause.getStackTrace()));
            cause = cause.getCause();
        }
        
        return stackTrace.toString();
    }
    
    private static void saveToBuffer(String message, Throwable e) {
        StringBuilder messageWithStackTrace = new StringBuilder();
        messageWithStackTrace.append(message);
        messageWithStackTrace.append(formStackTrace(e));
    }
    
    // Other
    
    public static void CopyStream(InputStream is, OutputStream os) {
        final int buffer_size = 1024;
        try {
            byte[] bytes = new byte[buffer_size];
            for (; ; ) {
                int count = is.read(bytes, 0, buffer_size);
                if (count == -1) {
                    break;
                }
                os.write(bytes, 0, count);
            }
        } catch (Exception ex) {
            logError(ex);
        }
    }
    
    public static boolean copyFile(Uri src, File dst, ContentResolver contentResolver)  {
        if (src == null || dst == null) {
            return false;
        }
        if (dst.isFile()) {
            dst.delete();
        }
        
        InputStream in;
        OutputStream out;
        try {
            in = contentResolver.openInputStream(src);
        } catch (FileNotFoundException e){
            Utils.logError(e);
            return false;
        }
        
        try {
            out = new FileOutputStream(dst);
        } catch (FileNotFoundException e){
            try {
                in.close();
            } catch (IOException ioE) {
                Utils.logError(ioE);
            }
            Utils.logError(e);
            return false;
        }
        
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            Utils.logError(e);
            return false;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                Utils.logError(e);
            }
            try {
                out.close();
            } catch (IOException e) {
                Utils.logError(e);
            }
        }
        return true;
    }
    
    public static void deleteDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        
        File[] files = null;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            files = null;
        }
        
        if (files != null) {
            for (File child : files) {
                if (child != null) {
                    if (child.isDirectory()) {
                        deleteDir(child);
                    } else {
                        child.delete();
                    }
                }
            }
        }
        
        dir.delete();
    }
    
    public static boolean addNoMedia(File root) {
        boolean res = true;
        if (root != null && root.isDirectory()) {
            File nomedia = new File(root, ".nomedia");
            if (!nomedia.exists()) {
                OutputStream out = null;
                try {
                    out = new FileOutputStream(nomedia);
                    out.flush();
                }  catch (Exception e) {
                    Utils.logError(e);
                    res = false;
                }
                if (out != null) {
                    try {
                        out.close();
                    }  catch (Exception e) {
                        Utils.logError(e);
                        res = false;
                    }
                }
            }
            if (!nomedia.isFile()) {
                res = false;
            }
        }
        return res;
    }
    
    public static void checkIfLooper() {
        if (LOGS_ENABLED && Looper.myLooper() == Looper.getMainLooper()) {
            logError("In Main Looper");
        }
    }
    
    public static String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();
            
            StringBuilder builder = new StringBuilder();
            for (int b : messageDigest) {
                builder.append(Integer.toHexString((b >> 4) & 0xf));
                builder.append(Integer.toHexString((b >> 0) & 0xf));
            }
            return builder.toString();
            
        } catch (NoSuchAlgorithmException e) {
            logError(e);
        }
        return "";
    }
    
    public static boolean createDirs(File dir) {
        if (dir != null) {
            if (dir.isDirectory()) {
                return true;
            } else {
                if (createDirs(dir.getParentFile())) {
                    dir.mkdir();
                    if (dir.isDirectory()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public static long getFileSize(final File file) {
        if (file == null || !file.exists())
            return 0;
        if (!file.isDirectory())
            return file.length();
        final List<File> dirs = new LinkedList<>();
        dirs.add(file);
        long result = 0;
        while (!dirs.isEmpty()) {
            final File dir = dirs.remove(0);
            if (!dir.exists())
                continue;
            final File[] listFiles = dir.listFiles();
            if (listFiles == null || listFiles.length == 0)
                continue;
            for (final File child : listFiles) {
                result += child.length();
                if (child.isDirectory())
                    dirs.add(child);
            }
        }
        return result;
    }
    
    public static String retrieveDeviceId(Context context) {
        String deviceId = null;
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        deviceId = sharedPref.getString(DEVICE_ID_PREFERNCE, null);
        if (deviceId != null && deviceId.length() > 0) {
            return deviceId;
        }
        
        if (deviceId == null) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && androidId.length() > 0) {
                deviceId = md5(androidId);
            }
        }
        
        if (deviceId == null) {
            String serialNumber = getSerialNumber();
            if (serialNumber != null && serialNumber.length() > 0) {
                deviceId = md5(serialNumber);
            }
        }
        
        if (deviceId == null || deviceId.length() <= 0) {
            deviceId = md5(String.valueOf(System.currentTimeMillis()));
        }
        
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(DEVICE_ID_PREFERNCE, deviceId);
        editor.apply();
        
        return deviceId;
    }
    
    private static String getSerialNumber() {
        String number = null;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            try {
                number = android.os.Build.SERIAL;
            } catch (Throwable e) {
                number = null;
            }
        }
        return number;
    }
    
    public static <T extends Object> String join(String separator, Collection<T> list) {
        if(list == null) return "";
        
        StringBuilder stringBuilder = new StringBuilder();
        for (T s : list) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(separator);
            }
            String value = String.valueOf(s);
            if(!TextUtils.isEmpty(value)) {
                stringBuilder.append(value);
            }
        }
        return stringBuilder.toString();
    }
    
    public static String join(String separator, Collection<String> list, boolean capital) {
        if(list == null) return "";
        
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : list) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(separator);
            }
            if(!TextUtils.isEmpty(s)) {
                stringBuilder.append(capitalize(s));
            }
        }
        return stringBuilder.toString();
    }
    
    public static String join(String separator, String[] array) {
        if(array == null) return "";
        
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : array) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(separator);
            }
            if(!TextUtils.isEmpty(s)) {
                stringBuilder.append(capitalize(s));
            }
        }
        return stringBuilder.toString();
    }
    
    public static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < count; i++)
            builder.append(c);
        return builder.toString();
    }
    
    public static String capitalize(String string) {
        if(TextUtils.isEmpty(string))
            return "";
        return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
    }
    
    public static Spanned highlightHtmlText(CharSequence text, @ColorInt int color) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        final Pattern pattern = Pattern.compile("<[^>]*>");
        final Matcher matcher = pattern.matcher(text);
        
        int lastEnd = 0;
        boolean match = false;
        while (matcher.find()) {
            
            if(match && lastEnd > 0 && matcher.start() > lastEnd) {
                String tagText = TextUtils.substring(text, lastEnd, matcher.start());
                if(tagText.length() > 0) {
                    result.append(tagText);
                    int length = result.length();
                    result.setSpan(new ForegroundColorSpan(color), length - tagText.length(), length, 0);
                }
                lastEnd = matcher.end();
                match = false;
            } else {
                result.append(text, lastEnd, matcher.start());
                lastEnd = matcher.end();
                match = true;
            }
        }
        
        return result.append(text, lastEnd, text.length());
    }
    
    public static <T> boolean contains(final Collection<T> array, final T v) {
        if(array == null)
            return false;
        
        for (final T e : array)
            if (e.equals(v))
                return true;
        
        return false;
    }
    
    public static boolean contains(final int[] array, final int v) {
        if(array == null)
            return false;
        
        for (final int e : array)
            if (e == v)
                return true;
        
        return false;
    }
    
    public static boolean contains(final long[] array, final long v) {
        if(array == null)
            return false;
        
        for (final long e : array)
            if (e == v)
                return true;
        
        return false;
    }
    
    public enum DownloaderType {
        SUN("SunImagesFromSDO", 0.3);
        
        private final String dir;
        private final double quota;
        
        DownloaderType(String dir, double quota) {
            this.dir = dir;
            this.quota = quota;
        }
    
        public long getQuota(long bytesOccupied) {
            File dir = getDir();
            StatFs stat = new StatFs(dir.getAbsolutePath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            for (DownloaderType t : DownloaderType.values()) {
                if (t != this) {
                    bytesOccupied += getFileSize(t.getDir());
                }
            }
        
            return (long) ((bytesAvailable + bytesOccupied) * quota);
        }
        
        public long getQuota() {
            return getQuota(getFileSize(getDir()));
        }
        
        public File getDir() {
            File dir = new File(Dashboard.getContext().getExternalCacheDir(), this.dir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }
    }
}
