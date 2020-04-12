package org.apache.cordova.splashscreen;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

class ResourceLoader extends AsyncTask {
    private final static long MIN_UPDATE_CHECK_INTERVAL = 0;//1 * 3600000L; //1 hour
    private final static long TIMEOUT = 10;
    private final static int THREADS_COUNT = 10;
    private final static String LOG_TAG = "ResourceLoader";
    private final static boolean LOGS_ENABLED = false;
    
    private final SplashScreen splash;
    private final Context      context;
    private final String       updateURL, updateSubFolder;
    private boolean cacheIsValid = false;
    private boolean alreadyStarting = false;
    private final ThreadPoolExecutor      executor;
    private final BlockingQueue<Runnable> executorQueue;
    private boolean onlyPerformUpdatesCheck = false;
    private boolean updatesRequired = false;
    private CallbackContext callbackContext = null;
    private double progress = 0;
    
    private final static int localDescPercent = 2;
    private final static int remoteDescPercent = 4; //x2
    private final static int updateFilesPercent = 70;
    private final static int moveFilesPercent = 5;
    private final static int copyFilesPercent = 15;
    
    public ResourceLoader(Context context, SplashScreen splash, String updateURL, String updateSubFolder) {
        this.context = context;
        this.splash = splash;
        this.updateURL = updateURL;
        this.updateSubFolder = updateSubFolder;
        executorQueue = new LinkedBlockingQueue<Runnable>();
        executor = new ThreadPoolExecutor(THREADS_COUNT, THREADS_COUNT, 0, TimeUnit.MILLISECONDS, executorQueue);
    }
    
    @Override
    protected Object doInBackground(Object[] params) {
        progress = 0;
        updateProgress();
        if (updateURL != null) {
            try {
                boolean copyResources = false;
                FileDescriptor localDesc = null, remoteDesc = null;
                
                //get descriptor from updates dir and from assets
                //compare those descs and check if the one from updates is newer than from assets
                //if so return desc from updates
                //else completely remove updates dir and return desc from assets
                localDesc = getLocalFileDescriptor();
                progress = localDescPercent;
                updateProgress();
                
                //if we have desc from updates - we are good to use update dir on start
                if (localDesc != null && localDesc.type == FileDescriptor.Type.UPDATES) {
                    cacheIsValid = true;
                }
                
                //check if it is time to update (or we are still on assets)
                if (needUpdate(localDesc)) {
                    LogDebug("Updating");
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                            .cache(null)
                            .build();
                    
                    //retrieve desc from server
                    remoteDesc = getRemoteFileDescriptor(client);
                    progress += remoteDescPercent;
                    updateProgress();
    
                    //if successful and server desc is newer than ours current
                    if (remoteDesc != null && remoteDesc.isCompatible(localDesc)) {
                        if (onlyPerformUpdatesCheck) {
                            updatesRequired = true;
                        } else {
                            //download changed files from server desc to separate folder
                            if (updateFiles(client, localDesc, remoteDesc)) {
                                //try retrieve the server desc again to double check that
                                //nothing changed on server while we where downloading files
                                FileDescriptor recheckRemoteDesc = getRemoteFileDescriptor(client);
                                progress += remoteDescPercent;
                                updateProgress();
                                if (recheckRemoteDesc != null && recheckRemoteDesc.equals(remoteDesc)) {
                                    cacheIsValid = false;
                                    //we are now moving downloaded files to updates dir
                                    //updates dir is not reliable from this point
                                    if (moveDownloadedFiles(localDesc, remoteDesc)) {
                                        //now copy ALL assets to updates folder if necesary
                                        if (copyResources(localDesc, remoteDesc)) {
                                            //at last save new descriptor
                                            if (remoteDesc.save(getSubUpdatesDir())) {
                                                cacheIsValid = true;
                                                //we are updated
                                            } else {
                                                LogError("Saving new descriptor failed");
                                            }
                                        } else {
                                            LogError("Remaining resources copy failed");
                                        }
                                    } else {
                                        LogError("Moving downloaded files failed");
                                    }
                                } else {
                                    copyResources = true;
                                    LogError("Remote descriptor recheck failed");
                                }
                            } else {
                                copyResources = true;
                                LogError("Update failed");
                            }
                        }
                    } else if (!onlyPerformUpdatesCheck) {
                        if (remoteDesc == null) {
                            LogError("Bad remote descriptor");
                        } else if (remoteDesc.baseSetup != localDesc.baseSetup) {
                            LogError("Remote descriptor incompatible (baseSetup "+remoteDesc.baseSetup+" remote vs "+localDesc.baseSetup+" local)");
                        } else {
                            LogError("Remote descriptor incompatible (version "+remoteDesc.version+" remote < "+localDesc.version+" local)");
                        }
                        copyResources = true;
                    }
                } else {
                    LogDebug("No update required");
                }
    
                if (!onlyPerformUpdatesCheck) {
                    launchAppNow();
                    if (copyResources && localDesc != null && (localDesc.type == FileDescriptor.Type.ASSETS || remoteDesc != null)) {
                        if (copyResources(localDesc, null)) {
                            //at last save descriptor
                            if (localDesc.save(getSubUpdatesDir())) {
                                cacheIsValid = true;
                                //we are on updates
                            } else {
                                LogError("Saving local descriptor failed");
                            }
                        } else {
                            LogError("Initial resources copy failed");
                        }
                    }
                }
            } catch (Exception e) {
                LogError(formStackTrace(e));
                executorQueue.clear();
            }
        } else {
            LogError("updateURL null");
        }
    
        if (!onlyPerformUpdatesCheck) {
            if (!cacheIsValid) {
                removeRecursively(getUpdatesDir());
            }
            removeRecursively(getDownloadsDir());
        }
        
        return null;
    }
    
    private void updateProgress() {
        if (alreadyStarting) {
            return;
        }
        long progress = Math.round(this.progress);
        if (progress < 0) {
            progress = 0;
        }
        if (progress > 100) {
            progress = 100;
        }
        splash.setProgress((int) (progress*0.8));
    }
    
    public void justCheckUpdates(CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        this.onlyPerformUpdatesCheck = true;
    }
    
    private File getDownloadsDir() {
        return new File(context.getCacheDir(), "downloads");
    }
    
    private File getUpdatesDir() {
        File updatesDir = new File(context.getCacheDir(), "updated");
        if (!updatesDir.exists()) {
            updatesDir.mkdirs();
        }
        return updatesDir;
    }
    
    private File getSubUpdatesDir() {
        File updatesDir = new File(getUpdatesDir(), updateSubFolder);
        if (!updatesDir.exists()) {
            updatesDir.mkdirs();
        }
        return updatesDir;
    }
    
    private boolean updateFiles(OkHttpClient client, FileDescriptor localDesc, FileDescriptor remoteDesc) throws ExecutionException, InterruptedException {
        Map<Future, ExecutorRunnable> updaters = new HashMap<Future, ExecutorRunnable>();
        
        File destination = getDownloadsDir();
        removeRecursively(destination);
        destination.mkdirs();
    
        File downloadsDir = getDownloadsDir();
        double totalSize = 0;
        for (String file : remoteDesc.files.keySet()) {
            if (!localDesc.files.containsKey(file) || !remoteDesc.files.get(file).equals(localDesc.files.get(file))) {
                Long size = remoteDesc.sizes.get(file);
                if (size != null && size > 0) {
                    totalSize += size;
                }
            }
        }
        double percentPerByte = 0;
        if (totalSize > 0) {
            percentPerByte = ((double) updateFilesPercent) / totalSize;
        }
        for (String file : remoteDesc.files.keySet()) {
            if (!localDesc.files.containsKey(file) || !remoteDesc.files.get(file).equals(localDesc.files.get(file))) {
                Downloader dwn = new Downloader(client, this, file, remoteDesc.files.get(file), percentPerByte, updateURL, downloadsDir);
                updaters.put(executor.submit(dwn), dwn);
            }
        }
    
        return waitForExecutorSuccess(updaters);
    }
    
    private boolean copyResources(FileDescriptor localDesc, FileDescriptor remoteDesc) throws ExecutionException, InterruptedException, IOException {
        if (localDesc.type == FileDescriptor.Type.UPDATES) {
            return true;
        } else {
            Map<Future, ExecutorRunnable> updaters = new HashMap<Future, ExecutorRunnable>();
            
            AssetManager am = context.getAssets();
    
            Collection<String> resources = buildResourcesList(am, null);
    
            resources.remove("www/"+updateSubFolder+"/"+FileDescriptor.descriptorName);
            
            File updatesDir = getUpdatesDir();
    
            List<String> files = new ArrayList<String>();
            double totalSize = 0;
            for (String file : resources) {
                file = file.substring(4);
                String testFile = null;
                if (file.startsWith(updateSubFolder+"/")) {
                    testFile = file.substring(updateSubFolder.length()+1);
                }
                if (testFile == null || remoteDesc == null ||
                        (!remoteDesc.files.containsKey(testFile) && !localDesc.files.containsKey(testFile)) ||
                        (localDesc.files.containsKey(testFile) && remoteDesc.files.containsKey(testFile) && remoteDesc.files.get(testFile).equals(localDesc.files.get(testFile)))) {
            
                    if (remoteDesc != null && remoteDesc.files.containsKey(testFile)) {
                        Long size = remoteDesc.sizes.get(testFile);
                        if (size != null && size > 0) {
                            totalSize += size;
                        }
                    } else {
                        Long size = localDesc.sizes.get(testFile);
                        if (size != null && size > 0) {
                            totalSize += size;
                        }
                    }
                    File f = new File(updatesDir, file);
                    if (!f.exists()) {
                        files.add(file);
                    }
                }
            }
    
            double percentPerByte = 0;
            if (totalSize > 0) {
                percentPerByte = ((double) copyFilesPercent) / totalSize;
            }
            for (String file : files) {
                ResourceCopier rsc = new ResourceCopier(this, am, file, percentPerByte, updatesDir);
                updaters.put(executor.submit(rsc), rsc);
            }
            
            if (waitForExecutorSuccess(updaters)) {
                updaters.clear();
                ResourceCopier rsc = new ResourceCopier(this, am, updateSubFolder+"/"+FileDescriptor.descriptorName, 0, updatesDir);
                updaters.put(executor.submit(rsc), rsc);
                return waitForExecutorSuccess(updaters);
            } else {
                return false;
            }
        }
    }
    
    private Collection<String> buildResourcesList(AssetManager am, String path) throws IOException {
        Collection<String> res = new HashSet<String>();
        
        if (path == null) {
            path = "www";
        }
        String[] resources = am.list(path);
        
        if (resources.length == 0) { //file
            res.add(path);
        } else { //dir
            for (String p : resources) {
                res.addAll(buildResourcesList(am, path+"/"+p));
            }
        }
        
        return res;
    }
    
    private boolean moveDownloadedFiles(FileDescriptor localDesc, FileDescriptor remoteDesc) throws ExecutionException, InterruptedException {
        Map<Future, ExecutorRunnable> updaters = new HashMap<Future, ExecutorRunnable>();
        
        File sourceDir = getDownloadsDir();
        File updatesDir = getSubUpdatesDir();
    
        double totalFiles = 0;
        for (String file : remoteDesc.files.keySet()) {
            if (!localDesc.files.containsKey(file) || !remoteDesc.files.get(file).equals(localDesc.files.get(file))) {
                totalFiles++;
            }
        }
        double percentPerFile = 0;
        if (totalFiles > 0) {
            percentPerFile = ((double) moveFilesPercent) / totalFiles;
        }
        
        for (String file : remoteDesc.files.keySet()) {
            if (!localDesc.files.containsKey(file) || !remoteDesc.files.get(file).equals(localDesc.files.get(file))) {
                FileMover mvr = new FileMover(this, percentPerFile, new File(sourceDir, file), new File(updatesDir, file));
                updaters.put(executor.submit(mvr), mvr);
            }
        }
    
        return waitForExecutorSuccess(updaters);
    }
    
    private boolean waitForExecutorSuccess(Map<Future, ExecutorRunnable> updaters) throws InterruptedException, ExecutionException {
        boolean success = true;
        for (Future f : updaters.keySet()) {
            f.get();
            ExecutorRunnable r = updaters.get(f);
            if (!r.isSuccess()) {
                success = false;
                break;
            }
        }
        if (!success) {
            executorQueue.clear();
        }
        return success;
    }
    
    private void removeRecursively(File file) {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File f : files) {
                    removeRecursively(f);
                }
            }
            file.delete();
        }
    }
    
    private boolean needUpdate(FileDescriptor localDesc) {
        if (localDesc != null) {
            long timeSinceUpdate = System.currentTimeMillis() - localDesc.updateTime;
            LogDebug(timeSinceUpdate + "ms Since last update check");
            return (localDesc.type == FileDescriptor.Type.ASSETS || localDesc.updateTime <= 0 || timeSinceUpdate <= 0 || timeSinceUpdate > MIN_UPDATE_CHECK_INTERVAL);
        } else {
            return false;
        }
    }
    
    private String readFile(InputStream is) throws IOException {
        return readFile(new BufferedReader(new InputStreamReader(is)));
    }
    
    private String readFile(File file) throws IOException {
        return readFile(new BufferedReader(new FileReader(file)));
    }
    
    private String readFile(BufferedReader reader) throws IOException {
        String         line          = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls            = System.getProperty("line.separator");
        
        try {
            while((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }
            
            return stringBuilder.toString();
        } finally {
            reader.close();
        }
    }
    
    private FileDescriptor getLocalFileDescriptor() throws Exception {
        FileDescriptor assetsDesc = null, updatesDesc = null;
        File                        updatesDir = getUpdatesDir();
        
        File localDescriptorFile = new File(new File(updatesDir, updateSubFolder), FileDescriptor.descriptorName);
        if (localDescriptorFile.isFile()) {
            try {
                String json = readFile(localDescriptorFile);
                updatesDesc = new FileDescriptor(new JSONObject(json), FileDescriptor.Type.UPDATES);
            } catch (Exception e) {
                LogError(formStackTrace(e));
            }
        } else {
            LogDebug("Updates desscriptor does not exist");
        }
        
        AssetManager am = context.getAssets();
        InputStream  is = am.open("www/"+updateSubFolder+"/"+FileDescriptor.descriptorName);
        if (is != null) {
            String json = readFile(is);
            assetsDesc = new FileDescriptor(new JSONObject(json), FileDescriptor.Type.ASSETS);
            is.close();
        }
        
        if (updatesDesc == null || !updatesDesc.isCompatibleOrEqual(assetsDesc)) {
            removeRecursively(updatesDir);
            return assetsDesc;
        }
        
        return updatesDesc;
    }
    
    private FileDescriptor getRemoteFileDescriptor(OkHttpClient client) throws JSONException, IOException {
        FileDescriptor remoteDesc = null;
        Request      request  = new Request.Builder().url(updateURL + FileDescriptor.descriptorName+"?revision="+System.currentTimeMillis()).build();
        Response     response = client.newCall(request).execute();
        /*LogDebug(request.toString());
        Map<String, List<String>> headers = response.networkResponse().request().headers().toMultimap();
        LogDebug("Request headers:");
        for (String h : headers.keySet()) {
            LogDebug(h+": "+headers.get(h));
        }
        LogDebug("Headers end");
        
        headers = response.networkResponse().headers().toMultimap();
        LogDebug("Response headers:");
        for (String h : headers.keySet()) {
            LogDebug(h+": "+headers.get(h));
        }
        LogDebug("Headers end");*/
        if (response.code() == 200) {
            try {
                JSONObject desc = new JSONObject(response.body().string());
                remoteDesc = new FileDescriptor(desc, FileDescriptor.Type.REMOTE);
            } catch (Exception e) {
                LogError(formStackTrace(e));
            }
        } else {
            LogError(response.code()+" response code for "+updateURL + FileDescriptor.descriptorName);
        }
        if (response.body() != null) {
            response.body().close();
        }
        return remoteDesc;
    }
    
    private void launchAppNow() {
        this.publishProgress(true);
    }
    
    @Override
    protected void onProgressUpdate(Object[] values) {
        if (!onlyPerformUpdatesCheck && values != null && values.length == 1 && values[0] != null && (values[0] instanceof Boolean) && ((Boolean) values[0])) {
            LogDebug("ResourceLoader finished half way");
            alreadyStarting = true;
            updateProgress();
            splash.chooseNextUrlAndLoad(cacheIsValid, getUpdatesDir().getAbsolutePath());
        }
    }
    
    @Override
    protected void onPostExecute(Object o) {
        updateProgress();
        LogDebug("ResourceLoader finished");
        if (!alreadyStarting && !onlyPerformUpdatesCheck) {
            alreadyStarting = true;
            splash.chooseNextUrlAndLoad(cacheIsValid, getUpdatesDir().getAbsolutePath());
        }
        if (onlyPerformUpdatesCheck && callbackContext != null) {
            splash.sendHasUpdates(updatesRequired, callbackContext);
        }
    }
    
    private static class Downloader extends ExecutorRunnable {
        private final String file, hash, updateURL;
        private final File destinationDir;
        private final OkHttpClient client;
        private final ResourceLoader loader;
        private final double percentPerByte;
        
        public Downloader(OkHttpClient client, ResourceLoader loader, String file, String hash, double percentPerByte, String updateURL, File destinationDir) {
            this.file = file;
            this.hash = hash;
            this.client = client;
            this.loader = loader;
            this.updateURL = updateURL;
            this.destinationDir = destinationDir;
            this.percentPerByte = percentPerByte;
        }
        
        @Override
        public void run() {
            FileOutputStream    fOut     = null;
            BufferedInputStream input    = null;
            Response            response = null;
            try {
                LogDebug("Downloading file "+updateURL + file);
                Request      request  = new Request.Builder().url(updateURL + file + "?revision=" + hash).build();
                response = client.newCall(request).execute();
                if (response.code() == 200) {
                    File destination = new File(destinationDir, file);
                    destination.getParentFile().mkdirs();
    
                    fOut = new FileOutputStream(destination);
    
                    InputStream is = response.body().byteStream();
                    input = new BufferedInputStream(is);
    
                    byte[] data = new byte[1024];
                    int count;
    
                    while ((count = input.read(data)) != -1) {
                        fOut.write(data, 0, count);
                        loader.progress += percentPerByte * count;
                        loader.updateProgress();
                    }
                    
                    setSuccess(true);
                } else {
                    LogError(response.code()+" response code for "+updateURL + file);
                }
            } catch (Exception e) {
                LogError(formStackTrace(e));
            } finally {
                if (fOut != null) {
                    try {
                        fOut.close();
                    } catch (IOException e) {
                        setSuccess(false);
                    }
                }
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        LogError(formStackTrace(e));
                    }
                }
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
        }
    }
    
    private static class FileMover extends ExecutorRunnable {
        private final File source, destination;
        private final ResourceLoader loader;
        private final double percentPerFile;
        
        public FileMover(ResourceLoader loader, double percentPerFile, File source, File destination) {
            this.source = source;
            this.destination = destination;
            this.loader = loader;
            this.percentPerFile = percentPerFile;
        }
    
        @Override
        public void run() {
            if (source.isFile()) {
                LogDebug("Moving file "+source.getAbsolutePath());
                if (!destination.getParentFile().exists()) {
                    destination.getParentFile().mkdirs();
                }
                loader.progress += percentPerFile;
                loader.updateProgress();
                setSuccess(source.renameTo(destination));
            }
        }
    }
    
    private static class ResourceCopier extends ExecutorRunnable {
        private final File updatesDir;
        private final String file;
        private final AssetManager am;
        private final ResourceLoader loader;
        private final double percentPerByte;
    
        public ResourceCopier(ResourceLoader loader, AssetManager am, String file, double percentPerByte, File updatesDir) {
            this.file = file;
            this.updatesDir = updatesDir;
            this.am = am;
            this.loader = loader;
            this.percentPerByte = percentPerByte;
        }
    
        @Override
        public void run() {
            if (updatesDir.isDirectory()) {
                LogDebug("Coping resource "+file);
                BufferedReader reader = null;
                FileOutputStream fOut = null;
                InputStream is = null;
                File destination = new File(updatesDir, file);
                destination.getParentFile().mkdirs();
                try {
                    is = am.open("www/"+file);
                    if (is != null) {
                        fOut = new FileOutputStream(destination);
    
                        byte[] buffer = new byte[1024];
                        int    bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fOut.write(buffer, 0, bytesRead);
                            loader.progress += percentPerByte * bytesRead;
                            loader.updateProgress();
                        }
    
                        setSuccess(true);
                    }
                } catch (Exception e) {
                    LogError(formStackTrace(e));
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
        
                        }
                    }
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            
                        }
                    }
                    if (fOut != null) {
                        try {
                            fOut.close();
                        } catch (IOException e) {
                            LogError(formStackTrace(e));
                            setSuccess(false);
                        }
                    }
                }
            }
        }
    }
    
    private static abstract class ExecutorRunnable implements Runnable {
        private volatile boolean success = false;
        
        protected void setSuccess(boolean success) {
            this.success = success;
        }
    
        public boolean isSuccess() {
            return success;
        }
    }
    
    private static class FileDescriptor {
        public static final String descriptorName = "file_desc.json";
        public final long version, baseSetup, updateTime;
        public final Type                type;
        public final Map<String, String> files;
        public final Map<String, Long> sizes;
        
        public enum Type {
            ASSETS, UPDATES, REMOTE;
        };
        
        public FileDescriptor(JSONObject desc, Type type) throws JSONException {
            this.type = type;
            version = desc.getLong("version");
            baseSetup = desc.getLong("baseSetup");
            if (desc.has("updateTime")) {
                updateTime = desc.getLong("updateTime");
            } else {
                updateTime = 0;
            }
            
            files = new HashMap<String, String>();
            sizes = new HashMap<String, Long>();
            
            JSONArray fs = desc.getJSONArray("files");
            if (fs.length() < 10) {
                throw new JSONException("invalid file array");
            }
            for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.getJSONObject(i);
                String name = f.getString("name");
                String hash = f.getString("hash");
                Long   size = null;
                if (f.has("size")) {
                    size = f.getLong("size");
                }
                
                if (name != null && !name.isEmpty() && hash != null && !hash.isEmpty()) {
                    files.put(name, hash);
                    sizes.put(name, size);
                } else {
                    throw new JSONException("invalid name or hash");
                }
            }
        }
        
        public boolean isCompatible(FileDescriptor desc) {
            return desc != null && version > desc.version && baseSetup == desc.baseSetup;
        }
    
        public boolean isCompatibleOrEqual(FileDescriptor desc) {
            return desc != null && version >= desc.version && baseSetup == desc.baseSetup;
        }
    
        public boolean equals(FileDescriptor desc) {
            return desc != null && version == desc.version && baseSetup == desc.baseSetup && type == desc.type && files.equals(desc.files);
        }
        
        public boolean save(File dir) {
            boolean success = false;
            if (dir != null && dir.isDirectory()) {
                FileOutputStream fOut = null;
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("version", version);
                    obj.put("baseSetup", baseSetup);
                    obj.put("updateTime", System.currentTimeMillis());
                    JSONArray fs = new JSONArray();
                    for (String name : files.keySet()) {
                        JSONObject f = new JSONObject();
                        f.put("name", name);
                        f.put("hash", files.get(name));
                        f.put("size", sizes.get(name));
                        fs.put(f);
                    }
                    obj.put("files", fs);
    
                    File destination = new File(dir, FileDescriptor.descriptorName);
    
                    fOut        = new FileOutputStream(destination);
                    OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                    myOutWriter.append(obj.toString());
                    myOutWriter.close();
    
                    success = true;
                } catch (Exception e) {
                    LogError(formStackTrace(e));
                } finally {
                    if (fOut != null) {
                        try {
                            fOut.close();
                        } catch (IOException e) {
                            LogError(formStackTrace(e));
                            success = false;
                        }
                    }
                }
            }
            return success;
        }
    }
    
    private static String formStackTrace(Throwable e) {
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
    
    private static void LogDebug(String message) {
        if (LOGS_ENABLED)
            Log.d(LOG_TAG, message);
    }
    
    private static void LogError(String message) {
        if (LOGS_ENABLED)
            Log.e(LOG_TAG, message);
    }
}