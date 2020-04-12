package ru.povidalo.dashboard.fragment;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.applandeo.materialcalendarview.CalendarView;
import com.applandeo.materialcalendarview.exceptions.OutOfDateRangeException;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.Event;
import ru.povidalo.dashboard.util.PreferencesController;
import ru.povidalo.dashboard.util.Utils;

public class TimerFragment extends Fragment implements View.OnClickListener, NumberPicker.OnValueChangeListener {
    private static final String H_PREF = "timer_h_pref";
    private static final String M_PREF = "timer_m_pref";
    private static final String S_PREF = "timer_s_pref";

    private static final long FLICKER_PERIOD = 100;
    private static final int  FLICKER_VIBRATE_PERIODS = 8;
    private static final int  FLICKER_QUITE_PERIODS = 1;

    @BindView(R.id.close_btn) public View closeBtn;

    @BindView(R.id.h_picker) public NumberPicker hPicker;
    @BindView(R.id.m_picker) public NumberPicker mPicker;
    @BindView(R.id.s_picker) public NumberPicker sPicker;
    @BindView(R.id.timer_clear) public TextView clearBtn;
    @BindView(R.id.timer_toggle) public TextView toggleBtn;
    @BindView(R.id.timer_label) public TextView timerLabel;

    @BindView(R.id.picker_cap) public ViewGroup pickerCaps;

    @BindView(R.id.calendar_view) public CalendarView calendar;

    private Timer timer;
    private Timer autocloseTimer;
    private Handler handler = new Handler();
    private boolean started = false;
    private int flickerState = 0;
    private int valueStarted;
    private Vibrator vibrator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.timer_widget, container, false);
        ButterKnife.bind(this, rootView);

        closeBtn.setOnClickListener(this);

        setPickerData(hPicker, 24);
        setPickerData(mPicker, 59);
        setPickerData(sPicker, 59);

        hPicker.setValue(PreferencesController.getInt(getContext(), H_PREF, 0));
        mPicker.setValue(PreferencesController.getInt(getContext(), M_PREF, 0));
        sPicker.setValue(PreferencesController.getInt(getContext(), S_PREF, 0));
        toggleBtn.setEnabled(hPicker.getValue() != 0 || mPicker.getValue() != 0 || sPicker.getValue() != 0);
        clearBtn.setEnabled(hPicker.getValue() != 0 || mPicker.getValue() != 0 || sPicker.getValue() != 0);

        hPicker.setOnValueChangedListener(this);
        mPicker.setOnValueChangedListener(this);
        sPicker.setOnValueChangedListener(this);

        setCalendarTransparentBg(calendar);
        calendar.setHeaderColor(R.color.calendar_header);
        calendar.setBackgroundResource(R.color.calendar_bg);

        clearBtn.setTypeface(Utils.robotoRegular);
        clearBtn.setOnClickListener(this);
        toggleBtn.setTypeface(Utils.robotoRegular);
        toggleBtn.setOnClickListener(this);

        timerLabel.setTypeface(Utils.robotoBold);

        for (int i = 0; i < pickerCaps.getChildCount(); i++) {
            View c = pickerCaps.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTypeface(Utils.robotoRegular);
            }
        }

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        resetAutocloseTimer(rootView);

        return rootView;
    }

    private void setCalendarTransparentBg(View v) {
        if (v.getId() == com.applandeo.materialcalendarview.R.id.forwardButton ||
            v.getId() == com.applandeo.materialcalendarview.R.id.previousButton) {
            return;
        }
        v.setBackgroundResource(android.R.color.transparent);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                setCalendarTransparentBg(g.getChildAt(i));
            }
        }
    }

    private void setPickerData(NumberPicker picker, int max) {
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(true);
        String[] data = new String[max+1];
        for (int i = 0; i <= max; i++) {
            data[i] = String.format("%02d", i);
        }
        picker.setDisplayedValues(data);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.timer_clear:
                if (!started) {
                    hPicker.setValue(0);
                    mPicker.setValue(0);
                    sPicker.setValue(0);
                    toggleBtn.setEnabled(false);
                    clearBtn.setEnabled(false);
                    PreferencesController.putInt(getContext(), H_PREF, hPicker.getValue());
                    PreferencesController.putInt(getContext(), M_PREF, mPicker.getValue());
                    PreferencesController.putInt(getContext(), S_PREF, sPicker.getValue());
                    break;
                }
            case R.id.timer_toggle:
                if (started && timer != null) {
                    timer.cancel();
                    timer.purge();
                    timer = null;
                }
                started = !started;
                if (started) {
                    valueStarted = hPicker.getValue()*60*60 + mPicker.getValue()*60 + sPicker.getValue();
                    timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            handler.post(timeUpdateRunnable);
                        }
                    }, 0, Utils.ONE_SECOND);
                }
                toggleBtn.setText(started?R.string.stop:R.string.start);

                hPicker.setEnabled(!started);
                mPicker.setEnabled(!started);
                sPicker.setEnabled(!started);
                EventBus.getDefault().post(new Event.TimerStateUpdated(started, valueStarted));
                break;
            case R.id.close_btn:
                setVisible(false);
                break;
        }
        resetAutocloseTimer(null);
    }

    private void resetAutocloseTimer(View v) {
        if (v == null) {
            v = getView();
        }
        if (v == null) {
            return;
        }
        if (autocloseTimer != null) {
            autocloseTimer.cancel();
            autocloseTimer.purge();
            autocloseTimer = null;
        }
        if (!started && v.getVisibility() == View.VISIBLE) {
            autocloseTimer = new Timer();
            autocloseTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.post(autocloseRunnable);
                }
            }, 30 * Utils.ONE_SECOND);
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        resetAutocloseTimer(null);
        if (!started) {
            toggleBtn.setEnabled(hPicker.getValue() != 0 || mPicker.getValue() != 0 || sPicker.getValue() != 0);
            clearBtn.setEnabled(hPicker.getValue() != 0 || mPicker.getValue() != 0 || sPicker.getValue() != 0);
            PreferencesController.putInt(getContext(), H_PREF, hPicker.getValue());
            PreferencesController.putInt(getContext(), M_PREF, mPicker.getValue());
            PreferencesController.putInt(getContext(), S_PREF, sPicker.getValue());
        }
    }

    private void changeValueByOne(final NumberPicker higherPicker, final boolean increment) {
        Method method;
        try {
            method = higherPicker.getClass().getDeclaredMethod("changeValueByOne", boolean.class);
            method.setAccessible(true);
            method.invoke(higherPicker, increment);
        } catch (Exception e) {
            Utils.logError(e);
        }
    }

    private void decTimer() {
        valueStarted--;
        if (valueStarted < 0) {
            valueStarted = 0;
        }
        if (valueStarted == 0) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
            hPicker.setValue(PreferencesController.getInt(getContext(), H_PREF, 0));
            mPicker.setValue(PreferencesController.getInt(getContext(), M_PREF, 0));
            sPicker.setValue(PreferencesController.getInt(getContext(), S_PREF, 0));
            timerAlert();
        } else {
            int currentVal = hPicker.getValue()*60*60 + mPicker.getValue()*60 + sPicker.getValue();
            if (Math.abs(currentVal - valueStarted) > 1) {
                hPicker.setValue(valueStarted/3600);
                mPicker.setValue((valueStarted%3600)/60);
                sPicker.setValue(valueStarted%60);
            } else {
                if (sPicker.getValue() == 0) {
                    if (mPicker.getValue() == 0 && hPicker.getValue() != 0) {
                        changeValueByOne(hPicker, false);
                    }
                    if (mPicker.getValue() != 0 || hPicker.getValue() != 0) {
                        changeValueByOne(mPicker, false);
                    }
                }
                changeValueByOne(sPicker, false);
            }
        }
        EventBus.getDefault().post(new Event.TimerStateUpdated(started, valueStarted));
    }

    private void timerAlert() {
        getView().setVisibility(View.VISIBLE);
        resetAutocloseTimer(null);
        if (started) {
            flickerState++;
            getView().setBackgroundResource(flickerState%2==0 ? R.color.timer_default_bg : R.color.timer_alert_flicker_bg);
            if (flickerState == 1) {
                vibrate(FLICKER_VIBRATE_PERIODS * FLICKER_PERIOD);
            }
            if (flickerState >= FLICKER_VIBRATE_PERIODS+FLICKER_QUITE_PERIODS) {
                flickerState = 0;
            }
            handler.postDelayed(flickerRunnable, FLICKER_PERIOD);
        } else {
            flickerState = 0;
            getView().setBackgroundResource(R.color.timer_default_bg);
        }
    }

    private void vibrate(long milliseconds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, 255));
        } else {
            //deprecated in API 26
            vibrator.vibrate(milliseconds);
        }
    }

    public void setVisible(boolean visible) {
        getView().setVisibility(visible?View.VISIBLE:View.INVISIBLE);
        if (visible) {
            try {
                calendar.setDate(new Date());
            } catch (OutOfDateRangeException e) {
                Utils.log(e);
            }
        }
        resetAutocloseTimer(null);
    }

    private Runnable flickerRunnable = new Runnable() {
        @Override
        public void run() {
            timerAlert();
        }
    };

    private Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            decTimer();
        }
    };

    private Runnable autocloseRunnable = new Runnable() {
        @Override
        public void run() {
            setVisible(false);
        }
    };
}
