package ru.povidalo.dashboard.fragment;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.Utils;

public class SlideShowFragment extends FSFragment {
    private static final File IMG_BASE_PATH = new File(Environment.getExternalStorageDirectory(), "wallpapers");

    @BindView(R.id.img_bottom) ImageView imgBottom;
    @BindView(R.id.img_top) ImageView imgTop;
    private Transformation fitScreenTransform = new CenterCropTransform(Dashboard.screenWidth(), Dashboard.screenHeight());

    private List<File> imgUrls = null;
    private int originalImagesCount;

    private Timer timer = null;
    private Handler handler = new Handler();
    private static final float INVISIBLE = 0f;
    private static final float VISIBLE = 1f;
    private static final long SWITCH_PERIOD = 60 * Utils.ONE_SECOND;
    private static final long TRANSITION_DELAY = 3000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.slide_show_fragment, container, false);
        ButterKnife.bind(this, rootView);

        checkPermission();

        return rootView;
    }

    @Override
    protected void onPermissionApproved() {
        startSlideshow();
    }

    private void startSlideshow() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(timeUpdateRunnable);
            }
        }, 0, SWITCH_PERIOD);
    }

    private void updateImg() {
        if (imgUrls == null) {
            imgUrls = getFileList(IMG_BASE_PATH,"jpg", "png", "jpeg");
            originalImagesCount = imgUrls.size();
            Utils.log("Slideshow found "+originalImagesCount+" images");
        }

        if (imgUrls.size() > 0) {
            final File path = imgUrls.get((int) (Math.random()*imgUrls.size()));

            imgTop.setVisibility(View.INVISIBLE);
            Picasso.with(getContext()).load(path).noFade().transform(fitScreenTransform).into(imgTop, new Callback() {
                @Override
                public void onSuccess() {
                    switchState(path);
                }

                @Override
                public void onError() {
                    Utils.log("Failed to load image " + path);
                    imgUrls.remove(path);
                    if (imgUrls.size() < Math.min(50, originalImagesCount/5)) {
                        imgUrls = null;
                    }
                    updateImg();
                }
            });
        } else {
            Utils.logError("No Images found");
        }
    }

    private void switchState(final File path) {
        Animation fadeIn = new AlphaAnimation(INVISIBLE, VISIBLE);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.setDuration(TRANSITION_DELAY);
        fadeIn.setFillAfter(true);
        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                imgTop.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                /*Drawable prevDrawable = imgBottom.getDrawable();
                if (prevDrawable != null) {

                }*/
                /*Bitmap bitmapToRecycle = imgBottom.getDrawable() != null ? ((BitmapDrawable) imgBottom.getDrawable()).getBitmap() : null;
                imgBottom.setImageDrawable(imgTop.getDrawable());
                imgTop.setVisibility(View.INVISIBLE);
                if (bitmapToRecycle != null && !bitmapToRecycle.isRecycled()) {
                    bitmapToRecycle.recycle();
                }*/
                Picasso.with(getContext()).load(path).noFade().transform(fitScreenTransform).into(imgBottom, new Callback() {
                    @Override
                    public void onSuccess() {
                        imgTop.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onError() {
                        imgTop.setVisibility(View.INVISIBLE);
                    }
                });
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        imgTop.startAnimation(fadeIn);
    }

    private Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateImg();
        }
    };


    private static class CenterCropTransform implements Transformation {
        float width, height;

        CenterCropTransform(int w, int h) {
            width = w;
            height = h;
        }

        @Override
        public Bitmap transform(Bitmap source) {
            if (source == null || source.isRecycled()) {
                return source;
            }

            int x = 0, y = 0, w = source.getWidth(), h = source.getHeight();

            float scale = Math.max(width / w, height / h);

            Matrix m = new Matrix();
            m.setScale(scale, scale);

            if (w * scale > width) {
                x = Math.round((w - (width / scale)) / 2);
                w = Math.round(width / scale);
            }

            if (h * scale > height) {
                y = Math.round((h - (height / scale)) / 2);
                h = Math.round(height / scale);
            }

            Bitmap scaled = null;
            try {
                scaled = Bitmap.createBitmap(source, x, y, w, h, m, true);
            } catch (Exception e) {
                Utils.logError(e);
            }

            if (scaled != null && scaled != source && !scaled.isRecycled()) {
                source.recycle();
                return scaled;
            } else {
                return source;
            }
        }

        @Override
        public String key() {
            return getClass().getName();
        }
    }
}
