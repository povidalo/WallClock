package ru.povidalo.dashboard.command;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.util.Utils;

public class YaRadarCrawlerCommand extends Command {
    private final static String RADAR_URL = "https://yandex.ru/pogoda/dubna/maps/nowcast?le_Lightning=0";
    private final static long INITIAL_PAGE_LOAD_TIMEOUT = 60 * Utils.ONE_SECOND;
    private final static long RESOURCE_LOAD_TIMEOUT = 20 * Utils.ONE_SECOND;
    private final static int VIEW_WIDTH = 1080;
    private final static int VIEW_HEIGHT = 2200;
    private final static int SUB_IMG_WIDTH = 850;
    private final static int SUB_IMG_HEIGHT = 850;
    private Activity activity;
    private Bitmap screenshot;
    private boolean pageFinished = false;
    private boolean pageError = false;
    private WebView wv;
    
    public YaRadarCrawlerCommand(Activity activity, ICommand listener) {
        super(listener);
        //setAutoFinish(false);
        this.activity = activity;
    }
    
    @Override
    protected void doInBackground() {
        screenshot = Bitmap.createBitmap(VIEW_WIDTH, VIEW_HEIGHT, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(screenshot);
        
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wv = new WebView(Dashboard.getContext());
                wv.getSettings().setJavaScriptEnabled(true);
                //wv.getSettings().setUserAgentString(Dashboard.getContext().getResources().getString(R.string.default_user_agent));
    
                int widthSpec = View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY);
                wv.measure(widthSpec, heightSpec);
                wv.layout(0,0, VIEW_WIDTH, VIEW_HEIGHT);
    
                Utils.log("Map radar crawler initialized");
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        Utils.log("Map radar crawler error: "+error);
                        pageError = true;
                        synchronized (YaRadarCrawlerCommand.this) {
                            YaRadarCrawlerCommand.this.notify();
                        }
                    }
    
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        Utils.log("Map radar crawler loading resource: "+url);
                        if (pageFinished) {
                            synchronized (YaRadarCrawlerCommand.this) {
                                YaRadarCrawlerCommand.this.notify();
                            }
                        }
                    }
        
                    @Override
                    public void onPageFinished(WebView wv, String url) {
                        Utils.log("Map radar crawler URL loaded "+url);
    
                        pageFinished = true;
    
                        synchronized (YaRadarCrawlerCommand.this) {
                            YaRadarCrawlerCommand.this.notify();
                        }
                    }
                });
    
                Utils.log("Map radar crawler loading URL "+RADAR_URL);
                wv.loadUrl(RADAR_URL);
            }
        });
        
        synchronized (this) {
            try {
                wait(INITIAL_PAGE_LOAD_TIMEOUT);
                if (!pageFinished || pageError) {
                    Utils.log("Map radar crawler failed");
                    failed();
                    return;
                }
                long time = SystemClock.uptimeMillis();
                while (SystemClock.uptimeMillis() - time < RESOURCE_LOAD_TIMEOUT) {
                    time = SystemClock.uptimeMillis();
                    wait(RESOURCE_LOAD_TIMEOUT);
                    if (pageError) {
                        Utils.log("Map radar crawler failed");
                        failed();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Utils.logError(e);
                failed();
                return;
            }
        }
    
        Utils.log("Map radar crawler rendering image");
        
        canvas.save();
        wv.draw(canvas);
        canvas.restore();
        
        Bitmap subImg = Bitmap.createBitmap(screenshot,
                (VIEW_WIDTH - SUB_IMG_WIDTH) / 2, (VIEW_HEIGHT - SUB_IMG_HEIGHT) / 2,
                SUB_IMG_WIDTH, SUB_IMG_HEIGHT);
        screenshot.recycle();
    
        Utils.log("Map radar crawler saving image");
        File             targetDir = Dashboard.getContext().getExternalCacheDir();
        File             outFile   = new File(targetDir.getAbsolutePath(), "ya_"+System.currentTimeMillis()+".png");
    
        FileOutputStream out       = null;
        try {
            out = new FileOutputStream(outFile);
            subImg.compress(Bitmap.CompressFormat.PNG, 100, out);
            subImg.recycle();
        } catch (Exception e) {
            Utils.logError(e);
            failed();
            return;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Utils.logError(e);
                }
            }
        }
    
    
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://" + outFile.getAbsolutePath()), "image/*");
        activity.startActivity(intent);
        success();
    }
}