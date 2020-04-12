package ru.povidalo.dashboard.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.povidalo.dashboard.R;

/**
 * Created by povidalo on 29.06.18.
 */

public class WeatherFragment extends Fragment {
    @BindView(R.id.wu_chart) public LineChart wuChart;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //new YaRadarCrawlerCommand(this, null).execute();
        View rootView = inflater.inflate(R.layout.weather_fragment, container, false);
        ButterKnife.bind(this, rootView);

        wuChart.setBackgroundColor(Color.BLACK);
        wuChart.setGridBackgroundColor(Color.TRANSPARENT);
        wuChart.setDrawGridBackground(true);
    
        wuChart.setDrawBorders(false);
    
        // no description text
        wuChart.getDescription().setEnabled(false);
    
        // if disabled, scaling can be done on x- and y-axis separately
        wuChart.setPinchZoom(false);
    
        Legend l = wuChart.getLegend();
        l.setEnabled(false);
    
        XAxis xAxis = wuChart.getXAxis();
        xAxis.setEnabled(false);
    
        YAxis leftAxis = wuChart.getAxisLeft();
        leftAxis.setAxisMaximum(100f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawAxisLine(true);
        leftAxis.setDrawZeroLine(true);
        leftAxis.setDrawGridLines(true);
        leftAxis.setEnabled(false);
    
        wuChart.getAxisRight().setEnabled(false);
    
        // add data
        setData(100, 100);
    
        wuChart.invalidate();
        
        return rootView;
    }
    
    private void setData(int count, float range) {
        
        ArrayList<Entry> yVals1 = new ArrayList<Entry>();
        
        for (int i = 0; i < count; i++) {
            float val = (float) (Math.random() * range);// + (float)
            // ((mult *
            // 0.1) / 10);
            yVals1.add(new Entry(i, val));
        }
        
        LineDataSet set1;
        
        if (wuChart.getData() != null &&
                wuChart.getData().getDataSetCount() > 0) {
            set1 = (LineDataSet)wuChart.getData().getDataSetByIndex(0);
            set1.setValues(yVals1);
            wuChart.getData().notifyDataChanged();
            wuChart.notifyDataSetChanged();
        } else {
            // create a dataset and give it a type
            set1 = new LineDataSet(yVals1, "DataSet 1");
            
            set1.setAxisDependency(YAxis.AxisDependency.LEFT);
            set1.setColor(Color.rgb(0, 100, 255));
            set1.setDrawCircles(false);
            set1.setLineWidth(2f);
            set1.setCircleRadius(3f);
            set1.setFillAlpha(100);
            set1.setDrawFilled(true);
            set1.setFillColor(Color.argb(100, 0, 100, 255));
            set1.setHighLightColor(Color.RED);
            set1.setDrawCircleHole(false);
            set1.setFillFormatter(new IFillFormatter() {
                @Override
                public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                    return 0;
                }
            });
            
            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1); // add the datasets
            
            // create a data object with the datasets
            LineData data = new LineData(dataSets);
            data.setDrawValues(false);
            
            // set data
            wuChart.setData(data);
        }
    }
}
