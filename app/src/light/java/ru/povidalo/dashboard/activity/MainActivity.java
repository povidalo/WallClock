package ru.povidalo.dashboard.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.fragment.TimerFragment;
import ru.povidalo.dashboard.util.Utils;


public class MainActivity extends BaseActivity implements View.OnClickListener {

    @BindView(R.id.clock) public View clock;
    @BindView(R.id.system_settings_btn) public View systemSettingsBtn;

    @BindView(R.id.instruments) public View instruments;
    @BindView(R.id.timer) public View timer;

    private TimerFragment timerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
        systemSettingsBtn.setOnClickListener(this);

        clock.setOnClickListener(this);
        timerFragment = (TimerFragment) getSupportFragmentManager().findFragmentById(R.id.timer);
        if (timerFragment != null) {
            timerFragment.setVisible(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View decorView = getWindow().getDecorView();
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        setBrightnessLevel(100);
    }

    private void setBrightnessLevel(int brightness) {
        if (brightness < 0) {
            return;
        }
        if (brightness > 100) {
            brightness = 100;
        }

        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = brightness / 100F;
        getWindow().setAttributes(layoutParams);
    }

    @Override
    protected void onDestroy() {
        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.system_settings_btn:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                break;
            case R.id.clock:
                if (timerFragment != null) {
                    timerFragment.setVisible(true);
                }
                break;
        }
    }

    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        private boolean animationStarted = false;
        @Override
        public void onGlobalLayout() {
            if (!animationStarted) {
                animateClock();
                animationStarted = true;
            }
        }
    };

    private float lastX = 0, lastY = 0, nextX = 0, nextY = 0;
    private Integer instrumentsAvailW = null, instrumentsAvailH = null;
    private void animateClock() {
        if (instrumentsAvailW == null) {
            instrumentsAvailW = Dashboard.screenWidth() - instruments.getMeasuredWidth();
        }
        if (instrumentsAvailH == null) {
            instrumentsAvailH = Dashboard.screenHeight() - instruments.getMeasuredHeight();
        }

        lastX = nextX;
        lastY = nextY;
        nextX = (float) Math.random() * instrumentsAvailW;
        nextY = (float) Math.random() * instrumentsAvailH;

        Animation move = new MarginAnimation(instruments, lastX, lastY, nextX, nextY);
        move.setInterpolator(new LinearInterpolator());
        move.setDuration(5 * Utils.ONE_MINUTE);
        move.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                animateClock();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        instruments.startAnimation(move);
    }

    private static class MarginAnimation extends Animation {
        private final View target;
        private final ViewGroup.MarginLayoutParams params;
        private final float fromX, toX, fromY, toY;

        MarginAnimation(View v, float fromX, float fromY, float toX, float toY) {
            target = v;
            params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            params.rightMargin = (int) ((toX - fromX) * interpolatedTime + fromX);
            params.topMargin = (int) ((toY - fromY) * interpolatedTime + fromY);
            target.setLayoutParams(params);
        }
    }
}
