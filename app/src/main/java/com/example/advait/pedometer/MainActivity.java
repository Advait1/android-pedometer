package com.example.advait.pedometer;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.math.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String TAG="Pedometer";

    // selected based on empirical results
    private final double ALPHA  = 0.08;
    private int MEDIAN_WINDOW = 50;

    private List<Double> data = new ArrayList<Double>();
    private List<Double> dataLpf = new ArrayList<Double>();

    private int stepCount = 0;
    private int zeroCounts = 0;

    private SensorManager sensorManager;

    TextView step;

    private XYPlot plot = null;

    private static final int HISTORY_SIZE = 1000;

    private SimpleXYSeries s1 = null;

    Chronometer simpleChronometer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        step = (TextView) findViewById(R.id.stepCount);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        plot = (XYPlot) findViewById(R.id.plot);

        s1 = new SimpleXYSeries("Magnitude of Accelerations");
        s1.useImplicitXVals();

        plot.setRangeBoundaries(-14, 14, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, 1000, BoundaryMode.FIXED);
        plot.addSeries(s1, new LineAndPointFormatter(
                        Color.rgb(0, 184, 212), null, null, null));
        plot.setDomainStepValue(11);
        plot.setDomainLabel("Readings");
        plot.setRangeLabel("Magnitude of Accelerations");

        simpleChronometer = (Chronometer) findViewById(R.id.simpleChronometer); // initiate a chronometer

        AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
        builder1.setMessage("Please hold the device in your hand while walking. You do not have to look at the screen while you walk." +
                " Readings may be slightly inaccurate if device is kept in the pockets.");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();

        simpleChronometer.start(); // start a chronometer

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        simpleChronometer.start(); // start a chronometer
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void getAccelerometer(SensorEvent event) {

        float[] values = event.values;

        // Save the values from the three axes into their corresponding variables
        double x = values[0];
        double y = values[1];
        double z = values[2];

        // calculate magnitude of acceleration
        double magnitude = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));

        // if should execute first time only
        if (data.size() == 0) {

            data.add(0, 0.0);
            data.add(1, 0.0);
            dataLpf.add(0, 0.0);
            dataLpf.add(1, 0.0);
        }

        double median = getMedian(data);

        if (data.size() > MEDIAN_WINDOW) {

            data.remove(0);
            dataLpf.remove(0);
            data.add(magnitude - median);
            dataLpf.add(lpf(data, dataLpf));

        // till list reaches MEDIAN_WINDOW readings
        } else {

            data.add(magnitude - median);
            dataLpf.add(lpf(data, dataLpf));

        }

        // plot filtered magnitude vs time
        if (s1.size() > HISTORY_SIZE) {
            s1.removeFirst();
        }

        // add the latest history sample
        s1.addLast(null, dataLpf.get(dataLpf.size() - 1));
        plot.redraw();


        // check zero crossings and update steps
        if (checkZeroCrossed(dataLpf) && Math.abs(median) > 2.5) {
            zeroCounts++;
        }

        // two zero crossings counts as a step
        if (zeroCounts == 2) {
            stepCount++;
            zeroCounts = 0;
        }

        step.setText(String.valueOf(stepCount));

    }

    /**
     * Finds median of list
     * @param data list containing last MEDIAN_WINDOW readings
     * @return median of input list
     */
    private double getMedian(List<Double> data) {

        Collections.sort(data);
        int length = data.size();
        double median;

        if (length % 2 != 0) {
            median = data.get(length / 4);

        } else {
            median = (data.get(length / 4) + data.get((length - 1) / 4)) / 2;
        }

        return median;
    }

    /**
     * Low pass filters input data
     * @param data list of normalized data
     * @param dataLpf list of filtered data
     * @return new data point after filtering
     */
    private double lpf(List<Double> data, List<Double> dataLpf) {

        int sizeLpf = dataLpf.size();
        int size = data.size();
        double lastElemLpf = dataLpf.get(sizeLpf - 1);
        return lastElemLpf + ALPHA * (data.get(size - 1) - lastElemLpf);
    }

    /**
     * Checks if a zero crossing occurred in the data
     * @param dataLpf list of filtered data
     * @return boolean representing if a zero crossing has occurred
     */
    private boolean checkZeroCrossed(List<Double> dataLpf) {

        double lastReading = dataLpf.get(dataLpf.size() - 1);
        double secondLastReading = dataLpf.get(dataLpf.size() - 2);

        if (lastReading < 0 && secondLastReading > 0) {
            return true;

        } else if (lastReading > 0 && secondLastReading < 0) {
            return true;

        } else {
            return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister listener
        sensorManager.unregisterListener(this);
        simpleChronometer.stop();
    }
}
