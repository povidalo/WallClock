package ru.povidalo.dashboard.command;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.StatFs;
import android.text.format.Formatter;

import com.j256.ormlite.dao.CloseableIterator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler;
import nl.bravobit.ffmpeg.FFmpeg;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.povidalo.dashboard.DB.DatabaseHelper;
import ru.povidalo.dashboard.DB.ImageEntry;
import ru.povidalo.dashboard.DB.IndexEntry;
import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.PreferencesController;
import ru.povidalo.dashboard.util.UserAgentInterceptor;
import ru.povidalo.dashboard.util.Utils;

public class SunLoaderCommand extends Command {
    private static boolean running = false;
    
    private static final String  MOVIE_FILE_NAME_PREF = "sun_movie";
    private static final String  INSTRUMENT_ID      = "0171";
    private static final int     IMG_SIZE           = 512;
    private static final boolean  TEST_IMAGES           = true;
    
    private static final String  BASE_URL           = "https://sdo.gsfc.nasa.gov/assets/img/browse/";
    private static final String  IMG_REGEXP         = "<a href=\"((%s_.*?_"+IMG_SIZE+"_"+INSTRUMENT_ID+"\\....))\">";
    private static final int TIMEOUT = 30;
    private static final int RETRIES_COUNT = 3;
    private long quotaSpace;
    private long occupiedSpace;
    
    private static final int MAX_IMAGES_COUNT = 4000;//Integer.MAX_VALUE;
    
    private final SimpleDateFormat dfUrl    = new SimpleDateFormat("yyyy/MM/dd/");
    private final SimpleDateFormat dfRegexp = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat dfFile   = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
    private String imgPath;
    private String fileName;
    
    private OkHttpClient client;
    
    public SunLoaderCommand(ICommand listener) {
        super(listener);
        setAutoFinish(false);
    }
    
    @Override
    protected void doInBackground() {
        if (running) {
            finished();
            return;
        }
        running = true;
    
        client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .cache(null)
                .addNetworkInterceptor(new UserAgentInterceptor(Dashboard.getContext().getResources().getString(R.string.default_user_agent)))
                .build();
    
        File dir = Utils.DownloaderType.SUN.getDir();
        imgPath = dir.getAbsolutePath();
        
        Utils.log("Starting to count file sizes in "+dir.getAbsolutePath());
        occupiedSpace = Utils.getFileSize(dir);
        Utils.log("Starting to count overall quota for "+dir.getAbsolutePath());
        quotaSpace = Utils.DownloaderType.SUN.getQuota(occupiedSpace);
        
        printStat();

        Map<String, IndexEntry> index = IndexEntry.getAll();
        long time = System.currentTimeMillis();
        Date       date       = new Date(time);
        String formattedDate = dfRegexp.format(date);

        List<String> keys = new ArrayList<>();
        keys.addAll(index.keySet());
        Collections.sort(keys);

        String latestDate = keys.size() > 0 ? keys.get(keys.size()-1) : null;

        if (latestDate != null && latestDate.compareTo(formattedDate) > 0) {
            Utils.log("Invalid current date! We are in the past!");
            running = false;
            finished();
            return;
        }

        boolean isToday = true;
        while (true) {
            IndexEntry indexEntry = index.get(formattedDate);
            if (indexEntry == null) {
                indexEntry = new IndexEntry();
                indexEntry.setDate(formattedDate);
            }

            if (indexEntry.getState() != IndexEntry.IndexState.COMPLETE) {
                List<String> fileNames = loadIndex(indexEntry, date, isToday);
                if (fileNames == null) {
                    running = false;
                    failed();
                    finished();
                    return;
                }
    
                saveImagesIndex(fileNames, date);
                
                indexEntry.save();
            }

            ImageEntry quotaImage = downloadAndCheckImages();
            if (quotaImage != null) {
                Date idate = new Date(quotaImage.getTimestamp());
                String iformattedDate = dfRegexp.format(idate);
                if (formattedDate.equals(iformattedDate)) {
                    deleteIndexesPriorDate(formattedDate);
                    break;
                }
            }

            isToday = false;
            time -= Utils.ONE_DAY;
            date = new Date(time);
            formattedDate = dfRegexp.format(date);
        }
    
        index = null;
        keys = null;
    
        renameImages();
        createMovie();
        
        printStat();
    
        Utils.log("ALL DONE");
    }
    
    
    private void printStat() {
        Utils.log("");
        Utils.log("");
        Utils.log("-------------------------------------------------------");
        Utils.log("       STAT");
        Utils.log("-------------------------------------------------------");
        StatFs stat           = new StatFs(imgPath);
        long   bytesTotal = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long   bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        Utils.log("Total space : "+Formatter.formatFileSize(Dashboard.getContext(), bytesTotal));
        Utils.log("Free space : "+Formatter.formatFileSize(Dashboard.getContext(), bytesAvailable));
        Utils.log("Occupied space:"+Formatter.formatFileSize(Dashboard.getContext(), occupiedSpace));
        Utils.log("Quota space:"+Formatter.formatFileSize(Dashboard.getContext(), quotaSpace));
        Utils.log("");
        Utils.log("To be downloaded: " + ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.NOT_DOWNLOADED));
        Utils.log("Saved, not filtered: " + ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.SAVED));
        Utils.log("Already filtered as bad: " + ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.FILTERED));
        Utils.log("Already saved as good: " + ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.GOOD));
        Utils.log("Already filtered as invalid: " + ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.INVALID));
        Utils.log("-------------------------------------------------------");
        Utils.log("");
    }
    
    private List<String> loadIndex(IndexEntry indexEntry, Date date, boolean isToday) {
        List<String> fileNames = new ArrayList<String>();
    
        String baseUrl = BASE_URL + dfUrl.format(date);
    
        int retries = 0;
        while (true) {
            Utils.log("loading day index " + baseUrl);
            Response response = null;
            try {
                Request request = new Request.Builder().url(baseUrl).build();
                response = client.newCall(request).execute();
                if (response.code() == 200) {
                    InputStream       is = response.body().byteStream();
                    java.util.Scanner s  = new java.util.Scanner(is).useDelimiter("\\A");
                    if (s.hasNext()) {
                        String data = s.next();
    
                        Pattern pattern = Pattern.compile(String.format(IMG_REGEXP, dfRegexp.format(date)));
                        Matcher matcher = pattern.matcher(data);
    
                        while (matcher.find()) {
                            if (matcher.groupCount() >= 2) {
                                String img = matcher.group(1).trim();
    
                                fileNames.add(0, img);
                            }
                        }
                    }
    
                    Utils.log("loaded " + fileNames.size() + " filenames");
    
                    indexEntry.setState(isToday ? IndexEntry.IndexState.INCOMPLETE : IndexEntry.IndexState.COMPLETE);
                    break;
                } else {
                    Utils.logError(response.code() + " response code for index " + baseUrl);
                    indexEntry.setState(IndexEntry.IndexState.NOT_PARSED);
                    if (retries >= RETRIES_COUNT) {
                        break;
                    }
                }
            } catch (Exception e) {
                Utils.logError(e);
                indexEntry.setState(IndexEntry.IndexState.NOT_PARSED);
                indexEntry.save();
                if (retries >= RETRIES_COUNT) {
                    return null;
                }
            } finally {
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
            retries++;
        }
        
        return fileNames;
    }
    
    private void saveImagesIndex(List<String> fileNames, Date date) {
        List<ImageEntry> entriesToSave = new ArrayList<>();
        String baseUrl = BASE_URL + dfUrl.format(date);
        for (String fileName : fileNames) {
            ImageEntry i = ImageEntry.getByFileName(fileName);
            if (i == null) {
                i = new ImageEntry();
                i.setFileName(fileName);
                i.setBaseUrl(baseUrl);
                i.setLocalBasePath(imgPath);
                i.setSize(String.valueOf(IMG_SIZE));
                i.setInstrument(INSTRUMENT_ID);
                i.setLocalName(fileName);
                try {
                    Date fileDate = dfFile.parse(fileName.substring(0, 15));
                    i.setTimestamp(fileDate.getTime());
                } catch (ParseException e) {
                    Utils.logError(e);
                    i.setState(ImageEntry.FileState.INVALID);
                }
                entriesToSave.add(i);
            }
        }
        ImageEntry.saveBatch(entriesToSave);
    }
    
    private void deleteIndexesPriorDate(String formattedDate) {
        Utils.log("Deleteting unused indexes:");
        Map<String, IndexEntry> index = IndexEntry.getAll();
        for (String k : index.keySet()) {
            if (k.compareTo(formattedDate) < 0) {
                Utils.log("Deleteting unused index "+index.get(k).getDate());
                index.get(k).delete();
            }
        }
    }
    
    private ImageEntry downloadAndCheckImages() {
        CloseableIterator<ImageEntry> images = ImageEntry.getAllOlderThan(INSTRUMENT_ID, String.valueOf(IMG_SIZE), null, null);
        if (images == null) {
            return null;
        }
        
        int goodImagesCount = 0;
        while (images.hasNext()) {
            ImageEntry image = images.next();
    
            if (image.getState() == ImageEntry.FileState.GOOD) {
                goodImagesCount++;
            }
            
            images = testQuota(image, images, goodImagesCount);
            if (images == null) {
                if (image.getState() != ImageEntry.FileState.GOOD) {
                    occupiedSpace -= image.deleteAndGetFileSize();
                }
                return image;
            }
            
            if (image.getState() != ImageEntry.FileState.NOT_DOWNLOADED && image.getState() != ImageEntry.FileState.SAVED) {
                continue;
            }
            
            File imgFile = image.getFile();
    
            if (image.getState() == ImageEntry.FileState.NOT_DOWNLOADED && imgFile.isFile()) {
                if (checkBitmapValid(imgFile)) {
                    image.setState(ImageEntry.FileState.SAVED);
                    image.saveSync();
                }
            }
            
            if (image.getState() == ImageEntry.FileState.NOT_DOWNLOADED) {
                int retries = 0;
                while (true) {
                    Response response = null;
                    String   url      = image.getBaseUrl() + image.getFileName();
                    try {
                        Request request = new Request.Builder().url(url).build();
                        response = client.newCall(request).execute();
                        if (response.code() == 200) {
                            InputStream in = response.body().byteStream();
    
                            FileOutputStream fOut = new FileOutputStream(imgFile, false);
                            byte[]           buf  = new byte[1024 * 10];
                            int              len;
                            while ((len = in.read(buf)) > 0) {
                                fOut.write(buf, 0, len);
                            }
                            fOut.close();
                            image.setState(ImageEntry.FileState.SAVED);
    
                            occupiedSpace += Utils.getFileSize(imgFile);
    
                            Utils.log("Image downloaded: " + url);
                            break;
                        } else {
                            Utils.logError(response.code() + " response code for image " + url);
                            if (response.code() > 0 && response.code() < 500) {
                                image.setState(ImageEntry.FileState.INVALID);
                            }
                            if (retries >= RETRIES_COUNT) {
                                break;
                            }
                        }
                        image.saveSync();
                    } catch (Exception e) {
                        Utils.logError("Error downloading image " + url, e);
                        if (retries >= RETRIES_COUNT) {
                            break;
                        }
                    } finally {
                        if (response != null && response.body() != null) {
                            response.body().close();
                        }
                    }
                    retries++;
                }
            }
    
            if (!checkBitmapValid(imgFile)) {
                if (image.getState() == ImageEntry.FileState.SAVED) {
                    image.setState(ImageEntry.FileState.NOT_DOWNLOADED);
                    image.saveSync();
                }
            } else {
                image.setState(ImageEntry.FileState.SAVED);
                image.saveSync();
            }
    
            if (image.getState() == ImageEntry.FileState.SAVED) {
                if (TEST_IMAGES) {
                    if (testImage(image, imgFile)) {
                        goodImagesCount++;
                    }
                } else {
                    image.setState(ImageEntry.FileState.GOOD);
                    goodImagesCount++;
                }
                image.saveSync();
            }
        }
        images.closeQuietly();
        return null;
    }
    
    private CloseableIterator<ImageEntry> testQuota(ImageEntry currentImg, CloseableIterator<ImageEntry> images, int goodImagesCount) {
        if (goodImagesCount >= MAX_IMAGES_COUNT) { //count quota reached
            //delete elder images
            CloseableIterator<ImageEntry> backwardsImages = ImageEntry.getAllOldestFirst(INSTRUMENT_ID, String.valueOf(IMG_SIZE), null);
            int deletedImagesCount = 0;
            int remainingImagesCount = 0;
            boolean thresholdReached = false;
            if (backwardsImages != null) {
                while (backwardsImages.hasNext()) {
                    ImageEntry oldestImg = backwardsImages.next();
                    if (currentImg.getTimestamp() <= oldestImg.getTimestamp()) {
                        thresholdReached = true;
                    }
                    if (!thresholdReached) {
                        occupiedSpace -= oldestImg.deleteAndGetFileSize();
                        deletedImagesCount++;
                    } else {
                        remainingImagesCount++;
                    }
                }
            }
            Utils.log("Images count quota reached ("+MAX_IMAGES_COUNT+") deleted: "+deletedImagesCount+", remaining: "+remainingImagesCount);
            backwardsImages.closeQuietly();
            images.closeQuietly();
            return null;
        }
        
        if (occupiedSpace < quotaSpace) {
            return images;
        }
    
        CloseableIterator<ImageEntry> backwardsImages = ImageEntry.getAllOldestFirst(INSTRUMENT_ID, String.valueOf(IMG_SIZE), null);
        boolean modified = false;
        while (occupiedSpace >= quotaSpace && backwardsImages.hasNext()) {
            ImageEntry oldestImg = backwardsImages.next();
            if (currentImg.getTimestamp() <= oldestImg.getTimestamp()) {
                break;
            }
            
            modified = true;
            Utils.log("Rm image to free space "+oldestImg.getFileName());
            occupiedSpace -= oldestImg.deleteAndGetFileSize();
        }
    
        backwardsImages.closeQuietly();
        
        if (occupiedSpace >= quotaSpace) {
            Utils.log("Images space quota reached ( "+
                    Formatter.formatFileSize(Dashboard.getContext(), occupiedSpace)+" / "+
                    Formatter.formatFileSize(Dashboard.getContext(), quotaSpace)+" )");
        }
        
        if (occupiedSpace >= quotaSpace) {
            images.closeQuietly();
            return null;
        }
    
        if (modified) {
            images.closeQuietly();
            images = ImageEntry.getAllOlderThan(INSTRUMENT_ID, String.valueOf(IMG_SIZE), currentImg.getTimestamp(), null);
        }
        
        return images;
    }
    
    private boolean testImage(ImageEntry image, File imgFile) {
        long fileSize = Utils.getFileSize(imgFile);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        final int SAMPLE_SIZE = 4;
        opts.inSampleSize = SAMPLE_SIZE;
        Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), opts);
        if (bmp != null) {
            if (bmp.getWidth() > 1 && bmp.getHeight() > 1) {
                
                final int DEFAULT_SIZE = 4096;
                final double DEVIATION = 40;//Math.pow(170 * bmp.getWidth() / DEFAULT_SIZE, 2);
                final int normalPosX = 54;//2045 * bmp.getWidth() / DEFAULT_SIZE;
                final int normalPosY = 56;//2115 * bmp.getHeight() / DEFAULT_SIZE;
                final int paddingX = 330 * bmp.getWidth() / DEFAULT_SIZE;
                final int paddingY = 270 * bmp.getHeight() / DEFAULT_SIZE;
                final int width = bmp.getWidth()-paddingX*2;
                final int height = bmp.getHeight()-paddingY*2;
                
                double weight = 0;
                double posX = 0;
                double posY = 0;
                
                int[] pixels = new int[width * height];
                bmp.getPixels(pixels, 0, width, paddingX, paddingY, width, height);
            
                for (int i = 0; i < pixels.length; i++) {
                    int x = i % width;
                    int y = i / width;
                    double color = (pixels[i] & 0x0000ff + ((pixels[i] & 0x00ff00) >> 8) + ((pixels[i] & 0xff0000) >> 16)) / 3.0;
                    posX += color * x;
                    posY += color * y;
                    weight += color;
                }
                
                if (weight == 0) {
                    image.setState(ImageEntry.FileState.FILTERED);
                    imgFile.delete();
                    Utils.log("Image filtered, no color");
                } else {
                    posX = posX / weight;
                    posY = posY / weight;
    
                    double devX = posX-normalPosX;
                    double devY = posY-normalPosY;
                    
                    if (devX*devX + devY*devY > DEVIATION) {
                        image.setState(ImageEntry.FileState.FILTERED);
                        imgFile.delete();
                        Utils.log("Image filtered " +
                            "center(" + posX + ", " + posY + ") vs norm(" + normalPosX + ", " + normalPosY + ") and dev(" + devX + ", " + devY + ") vs " + DEVIATION);
                    } else {
                        //valid colorfull pic
                        image.setState(ImageEntry.FileState.GOOD);
                        Utils.log("Image is good");
                        return true;
                    }
                }
            } else {
                Utils.log("Invalid size");
                image.setState(ImageEntry.FileState.INVALID);
                imgFile.delete();
            }
            bmp.recycle();
        } else {
            image.setState(ImageEntry.FileState.INVALID);
            Utils.log("Invalid bitmap");
            imgFile.delete();
        }
        
        if (!imgFile.exists()) {
            occupiedSpace -= fileSize;
        }
        return false;
    }
    
    private boolean checkBitmapValid(File imgFile) {
        if (imgFile.isFile()) {
            long fileSize = Utils.getFileSize(imgFile);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 128;
            Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), opts);
            if (bmp == null) {
                Utils.log(imgFile.getName() + " sheduled for redownload");
                if (imgFile.delete()) {
                    occupiedSpace -= fileSize;
                }
                return false;
            } else {
                bmp.recycle();
                return true;
            }
        }
        return false;
    }
    
    private void renameImages() {
        Utils.log("Renaming images back to original");
        try {
            final CloseableIterator<ImageEntry> images = ImageEntry.getAllOldestFirst(INSTRUMENT_ID, String.valueOf(IMG_SIZE), null);
            DatabaseHelper.getCachedDao(ImageEntry.class).callBatchTasks(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    while (images.hasNext()) {
                        ImageEntry img = images.next();
                        img.rename(img.getFileName());
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Utils.log("Renaming images to 1-2-3");
        try {
            final CloseableIterator<ImageEntry> imagesToRaname = ImageEntry.getAllOldestFirst(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.GOOD);
            DatabaseHelper.getCachedDao(ImageEntry.class).callBatchTasks(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    int count = 1;
                    while (imagesToRaname.hasNext()) {
                        ImageEntry img = imagesToRaname.next();
                        img.rename(count + ".jpg");
                        count++;
                    }
                    Utils.log("Renamed "+count+" GOOD images");
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    
    private void createMovie() {
        File moviesDir = Dashboard.getContext().getExternalCacheDir();
        if (moviesDir == null) {
            Utils.logError("No place to save a movie");
            running = false;
            finished();
            return;
        }
    
        Long goodImagesCount = ImageEntry.countAll(INSTRUMENT_ID, String.valueOf(IMG_SIZE), ImageEntry.FileState.GOOD);
        CloseableIterator<ImageEntry> images = ImageEntry.getAllOlderThan(INSTRUMENT_ID, String.valueOf(IMG_SIZE), null, ImageEntry.FileState.GOOD);
        if (images.hasNext()) {
            ImageEntry mostRecentImg = images.next();
            images.closeQuietly();
            final FFmpeg ffmpeg = FFmpeg.getInstance(Dashboard.getContext());
            if (!ffmpeg.isSupported()) {
                Utils.logError("FFmpeg is NOT supported!");
                running = false;
                finished();
                return;
            }
    
            final File targetTmpFile    = new File(moviesDir.getAbsolutePath(), Utils.DownloaderType.SUN.name() + "_tmp.mp4");
            final File targetFile       = new File(moviesDir.getAbsolutePath(), Utils.DownloaderType.SUN.name() + "_" + mostRecentImg.getTimestamp() + "_" + goodImagesCount + ".mp4");
            final File currentMovieFile = getCurrentMovieFile();
            
            if (currentMovieFile != null && currentMovieFile.isFile() && currentMovieFile.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                Utils.log("Movie "+targetFile.getAbsolutePath()+" already exists");
                running = false;
                finished();
                return;
            }
    
            if (targetTmpFile.isFile()) {
                targetTmpFile.delete();
            }
    
            // to execute "ffmpeg -version" command you just need to pass "-version"
            //final String cmd = "-threads 2 -r 30 -i "+Utils.DownloaderType.SUN.getDir().getAbsolutePath()+"/%d.jpg -r 30 -y -c:v libx264 -r 30 -threads 2 \""+targetTmpFile.getAbsolutePath()+"\"";
            //final String cmd = "-f image2 -r 30 -i " + Utils.DownloaderType.SUN.getDir().getAbsolutePath() + "/%d.jpg -r 30 -c:v libx264 -r 30 " + targetTmpFile.getAbsolutePath();
            final String[] cmd = {"-f",
                    "image2",
                    "-r",
                    "30",
                    "-i",
                    Utils.DownloaderType.SUN.getDir().getAbsolutePath() + "/%d.jpg",
                    "-r",
                    "30",
                    "-c:v",
                    "libx264",
                    "-r",
                    "30",
                    targetTmpFile.getAbsolutePath()
            };

            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @Override
                public void onStart() {
                    Utils.log("FFmpeg execute start");
                }

                @Override
                public void onProgress(String message) {
                    Utils.log("FFmpeg execute progress " + message);
                }

                @Override
                public void onFailure(String message) {
                    Utils.logError("FFmpeg execute failure " + message);
                    running = false;
                    finished();
                }

                @Override
                public void onSuccess(String message) {
                    Utils.log("FFmpeg execute success " + message);
                    if (targetTmpFile.renameTo(targetFile)) {
                        PreferencesController.putString(Dashboard.getContext(), MOVIE_FILE_NAME_PREF, targetFile.getAbsolutePath());
                        Utils.log("FFmpeg tmp movie renamed to "+targetFile.getAbsolutePath());
                        deleteUnusedMovies(targetFile);
                        fileName = targetFile.getAbsolutePath();
                        success();
                    } else {
                        Utils.log("FFmpeg tmp movie rename failed");
                    }
                    running = false;
                    finished();
                }

                @Override
                public void onFinish() {
                    Utils.log("FFmpeg execute finished");
                    running = false;
                    finished();
                }
            });
        } else {
            images.closeQuietly();
            Utils.logError("No images to create a movie");
            running = false;
            finished();
        }
    }

    private void deleteUnusedMovies(File movieToExcept) {
        File[] files = movieToExcept.getParentFile().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().equals(movieToExcept.getName()) &&
                        f.getName().startsWith(Utils.DownloaderType.SUN.name()+"_") &&
                        f.getName().endsWith(".mp4")) {
                    Utils.log("Delete unused movie "+f.getAbsolutePath());
                    f.delete();
                }
            }
        }
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public static File getCurrentMovieFile() {
        String currentMoviewFileName = PreferencesController.getString(Dashboard.getContext(), MOVIE_FILE_NAME_PREF, null);
        return currentMoviewFileName != null ? new File(currentMoviewFileName) : null;
    }
}