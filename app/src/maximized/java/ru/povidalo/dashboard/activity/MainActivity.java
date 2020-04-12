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

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.command.Command;
import ru.povidalo.dashboard.command.CommandUpdateForecast;
import ru.povidalo.dashboard.command.ICommand;
import ru.povidalo.dashboard.command.ProtocolError;
import ru.povidalo.dashboard.command.YaRadarCrawlerCommand;
import ru.povidalo.dashboard.fragment.TimerFragment;
import ru.povidalo.dashboard.service.CrawlerService;
import ru.povidalo.dashboard.util.Event;
import ru.povidalo.dashboard.util.Utils;


public class MainActivity extends BaseActivity implements View.OnClickListener {
    
    @BindView(R.id.sun) public View sun;
    @BindView(R.id.clock) public View clock;
    //@BindView(R.id.cube) public View cube;
    //@BindView(R.id.weather) public View weather;
    @BindView(R.id.system_settings_btn) public View systemSettingsBtn;

    @BindView(R.id.instruments) public View instruments;
    @BindView(R.id.slide_show) public View slideShow;
    @BindView(R.id.timer) public View timer;


    private TimerFragment timerFragment;
    
    private static final long WEATHER_UPDATE_DELAY = 15 * Utils.ONE_MINUTE;
    private static final long RADAR_UPDATE_DELAY = 30 * Utils.ONE_MINUTE;
    private static final long ERROR_UPDATE_DELAY = 5 * Utils.ONE_MINUTE;
    
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

        //new CommandUpdateForecast(commandListener).execute();
        //new YaRadarCrawlerCommand(this, commandListener).execute();
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
    protected void onMessage(int msgCode, int value, String data) {
        if (msgCode == CrawlerService.MSG_DOWNLOADER_FINISHED && value == Utils.DownloaderType.SUN.ordinal()) {
            EventBus.getDefault().post(new Event.SunMovieUpdated(data));
        }
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
            int size = 512;//Math.min(Dashboard.screenHeight(), Dashboard.screenWidth())/2;

            size = clock.getMeasuredHeight();
            ViewGroup.LayoutParams params = sun.getLayoutParams();
            if (params.width != size || params.height != size) {
                params.width = size;
                params.height = size;
                sun.setLayoutParams(params);
            }

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

        //Animation move = new TranslateAnimation(-lastX, -nextX, lastY, nextY);
        Animation move = new MarginAnimation(instruments, lastX, lastY, nextX, nextY);
        move.setInterpolator(new LinearInterpolator());
        move.setDuration(5 * Utils.ONE_MINUTE);
        //move.setFillAfter(true);
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
    
    private ICommand commandListener = new ICommand() {
        Map<Class<? extends Command>, Boolean> succeededCommands = new HashMap<>();
        
        @Override
        public boolean onCommandProgress(Command command, int progress) {
            return false;
        }
    
        @Override
        public boolean onCommandState(Command command, CommandState state) {
            if (state == CommandState.FINISHED) {
                if (command instanceof CommandUpdateForecast) {
                    new CommandUpdateForecast(commandListener)
                            .executeDelayed(succeededCommands.get(command.getClass()) ?
                                WEATHER_UPDATE_DELAY :
                                ERROR_UPDATE_DELAY);
                } else if (command instanceof YaRadarCrawlerCommand) {
                    new YaRadarCrawlerCommand(MainActivity.this, commandListener)
                            .executeDelayed(succeededCommands.get(command.getClass()) ?
                                RADAR_UPDATE_DELAY :
                                ERROR_UPDATE_DELAY);
                }
            }
            if (state == CommandState.STARTED) {
                succeededCommands.put(command.getClass(), false);
            } else if (state == CommandState.SUCCESS) {
                succeededCommands.put(command.getClass(), true);
            } else if (state == CommandState.FINISHED) {
                succeededCommands.put(command.getClass(), false);
            }
            return false;
        }
    
        @Override
        public boolean onCommandError(Command command, ProtocolError error) {
            return false;
        }
    };

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
