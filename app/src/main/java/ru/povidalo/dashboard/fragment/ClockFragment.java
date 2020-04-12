package ru.povidalo.dashboard.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.Event;
import ru.povidalo.dashboard.util.Utils;

/**
 * Created by povidalo on 29.06.18.
 */

public class ClockFragment extends Fragment {
    @BindView(R.id.time_bottom) TextView timeTextBottom;
    @BindView(R.id.date_bottom) TextView dateTextBottom;
    @BindView(R.id.time_top) TextView timeTextTop;
    @BindView(R.id.date_top) TextView dateTextTop;
    private final DateFormat dateFormat = new DateFormat();
    private Handler handler = new Handler();

    private String lastTime = null, lastDate = null;
    
    private static final String ALPHA = "alpha";
    private static final float INVISIBLE = 0f;
    private static final float VISIBLE = 1f;
    private static final long CHECK_PERIOD = 1000;
    private static final long FADE_DURATION = 500;
    private static final long TIMER_FADE_DURATION = 200;

    private boolean timerRunning = false;
    private int timerSecondsLeft = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.clock, container, false);
        ButterKnife.bind(this, rootView);

        timeTextBottom.setTypeface(Utils.courierPrimeSansBold);
        timeTextTop.setTypeface(Utils.courierPrimeSansBold);
        timeTextTop.setAlpha(INVISIBLE);

        dateTextBottom.setTypeface(Utils.akrobatRegular);
        dateTextTop.setTypeface(Utils.akrobatRegular);
        dateTextTop.setAlpha(INVISIBLE);

        fillTime();
        fillDate();

        return rootView;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void timerUpdated(Event.TimerStateUpdated event) {
        timerRunning = event.running;
        timerSecondsLeft = event.secondsLeft;
    }

    private void fillTime() {
        Date date = new Date();
        
        String timeStr = String.valueOf(dateFormat.format("HH:mm", date));

        if (lastTime == null) {
            timeTextTop.setText(timeStr);
            timeTextBottom.setText(timeStr);

            lastTime = timeStr;
        } else if (!timeStr.equals(lastTime)) {
            switchTimeState(timeStr);
        }

        handler.postDelayed(timeUpdateRunnable, CHECK_PERIOD);
    }

    private void fillDate() {
        Date date = new Date();

        String dateStr;
        if (timerRunning) {
            dateTextBottom.setTypeface(Utils.courierPrimeSansBold);
            dateTextTop.setTypeface(Utils.courierPrimeSansBold);

            StringBuilder sb = new StringBuilder();
            if (timerSecondsLeft/3600 > 0) {
                sb.append(String.format("%02d", timerSecondsLeft / 3600));
                sb.append(":");
            }
            sb.append(String.format("%02d", (timerSecondsLeft%3600)/60));
            sb.append(":");
            sb.append(String.format("%02d", timerSecondsLeft%60));

            dateStr = sb.toString();
        } else {
            dateTextBottom.setTypeface(Utils.akrobatRegular);
            dateTextTop.setTypeface(Utils.akrobatRegular);

            dateStr = Utils.capitalize(String.valueOf(dateFormat.format("EEEE, d ", date))) +
                    Utils.capitalize(String.valueOf(dateFormat.format("MMMM", date)));
        }

        if (lastDate == null) {
            dateTextTop.setText(dateStr);
            dateTextBottom.setText(dateStr);

            lastDate = dateStr;
        } else if (!dateStr.equals(lastDate)) {
            switchDateState(dateStr);
        }

        handler.postDelayed(dateUpdateRunnable, CHECK_PERIOD);
    }
    
    private void switchTimeState(final String timeStr) {
        timeTextTop.setText(timeStr);
        ObjectAnimator oa = ObjectAnimator.ofFloat(timeTextTop, ALPHA, INVISIBLE, VISIBLE);
        oa.setDuration(FADE_DURATION).addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                ObjectAnimator oa = ObjectAnimator.ofFloat(timeTextBottom, ALPHA, VISIBLE, INVISIBLE);
                oa.setDuration(FADE_DURATION).addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        timeTextBottom.setAlpha(VISIBLE);
                        timeTextTop.setAlpha(INVISIBLE);
                        timeTextBottom.setText(timeStr);
                    }
                });
                oa.start();
            }
        });
        oa.start();

        lastTime = timeStr;
    }

    private void switchDateState(final String dateStr) {
        dateTextTop.setText(dateStr);
        ObjectAnimator oa = ObjectAnimator.ofFloat(dateTextTop, ALPHA, INVISIBLE, VISIBLE);
        oa.setDuration(timerRunning?TIMER_FADE_DURATION:FADE_DURATION).addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                ObjectAnimator oa = ObjectAnimator.ofFloat(dateTextBottom, ALPHA, VISIBLE, INVISIBLE);
                oa.setDuration(timerRunning?TIMER_FADE_DURATION:FADE_DURATION).addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        dateTextBottom.setAlpha(VISIBLE);
                        dateTextTop.setAlpha(INVISIBLE);
                        dateTextBottom.setText(dateStr);
                    }
                });
                oa.start();
            }
        });
        oa.start();

        lastDate = dateStr;
    }

    private Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            fillTime();
        }
    };

    private Runnable dateUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            fillDate();
        }
    };
}
