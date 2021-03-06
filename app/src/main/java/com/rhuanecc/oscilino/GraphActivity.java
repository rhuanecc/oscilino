package com.rhuanecc.oscilino;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;
import com.rhuanecc.oscilino.BT.BtReceiverThread;

import java.util.ArrayList;

public class GraphActivity extends AppCompatActivity {
    public static final int SET_CH0_DATA = 0;
    public static final int SET_CH1_DATA = 1;
    public static final float TIME_SCALE = (float) 0.112;          //112us a cada ponto -> 0.112ms
    public static final float VOLTAGE_SCALE = (float) 0.0048875;     //4.88mV
    public static final int SCREEN_REFRESH_INTERVAL = 100;         //intervalo entre cada atualização da tela (ms)
    public static final int MAX_GRAPH_POINTS = 700;

    public static boolean paused = false;
    public static int graphPointsNumber = MAX_GRAPH_POINTS;     //quantidade de pontos no gráfico, reduzir para reduzir escala de tempo
    public static int takeSampleEvery = 1;                      //quantidade de amostras a serem ignoradas para aumentar escala de tempo
    public static float voltageScale = VOLTAGE_SCALE;           //escala de tensão utilizada no circuito de condicionamento
    public static boolean isAC = false;

    ToggleButton pauseButton;
    ToggleButton acButton;
    Spinner timeSpinner;
    Spinner voltageSpinner;

    GraphView graph;
    LineGraphSeries<DataPoint> ch0;
    LineGraphSeries<DataPoint> ch1;
    BtReceiverThread receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        //======================================== Controls ========================================
        pauseButton = (ToggleButton) findViewById(R.id.pauseButton);
        pauseButton.setOnCheckedChangeListener(pauseListener);

        acButton = (ToggleButton) findViewById(R.id.acButton);
        acButton.setOnCheckedChangeListener(acListener);

        timeSpinner = (Spinner) findViewById(R.id.timeSpinner);
        ArrayAdapter<CharSequence> timeAdapter = ArrayAdapter.createFromResource(this, R.array.time_array, android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);
        timeSpinner.setOnItemSelectedListener(timeSpinnerListener);
        timeSpinner.setSelection(2);        //80ms

        voltageSpinner = (Spinner) findViewById(R.id.voltageSpinner);
        ArrayAdapter<CharSequence> voltageAdapter = ArrayAdapter.createFromResource(this, R.array.voltage_array, android.R.layout.simple_spinner_dropdown_item);
        voltageSpinner.setAdapter(voltageAdapter);
        voltageSpinner.setOnItemSelectedListener(voltageSpinnerListener);
        voltageSpinner.setSelection(1);     //5v

        //========================================= Graph ==========================================
        graph = (GraphView) findViewById(R.id.graph);
        graph.setTitle("Voltage x Time (ms)");
        graph.setKeepScreenOn(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setScalable(true);                      //enable zoom
        graph.getGridLabelRenderer().setNumHorizontalLabels(9);
        graph.getGridLabelRenderer().setNumVerticalLabels(6);

        //Dados para grafico
        ch0 = new LineGraphSeries<>();
        ch1 = new LineGraphSeries<>();
        ch1.setColor(Color.RED);
        graph.addSeries(ch0);    //Add dados no grafico
        graph.addSeries(ch1);    //Add dados no grafico

        //Mostra info do ponto clicado
        ch0.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                double time = dataPoint.getX();
                double voltage = dataPoint.getY();

                Toast.makeText(GraphActivity.this, String.format("%.3f ms   -->   %.3f V", time, voltage), Toast.LENGTH_SHORT).show();
            }
        });

        //Inicia nova thread para receber os dados
        receiver = new BtReceiverThread(uiHandler);
        receiver.start();
    }

    Handler uiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.arg1 == SET_CH0_DATA){                          //canal 0
                ch0.resetData((DataPoint[])msg.obj);

            } else if(msg.arg1 == SET_CH1_DATA) {                  //canal 1
                ch1.resetData((DataPoint[]) msg.obj);
            }
        }
    };

    //Full screen - immersive mode
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if(hasFocus){
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        receiver.close();
    }

    private CompoundButton.OnCheckedChangeListener pauseListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            paused = isChecked;
        }
    };

    private CompoundButton.OnCheckedChangeListener acListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            isAC = isChecked;

            //updates using spinner listener
            int selectedItemPosition = voltageSpinner.getSelectedItemPosition();
            View view = voltageSpinner.getChildAt(selectedItemPosition);
            long itemID = voltageSpinner.getAdapter().getItemId(selectedItemPosition);
            voltageSpinnerListener.onItemSelected(voltageSpinner, view, selectedItemPosition, itemID);
        }
    };


    private AdapterView.OnItemSelectedListener timeSpinnerListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selected = (String) timeSpinner.getItemAtPosition(position);
            switch (selected){
                case "20 ms":
                    takeSampleEvery = 1;
                    graph.getViewport().setMaxX(20);
                    graphPointsNumber = MAX_GRAPH_POINTS/4;
                    break;
                case "40 ms":
                    takeSampleEvery = 1;
                    graph.getViewport().setMaxX(40);
                    graphPointsNumber = MAX_GRAPH_POINTS/2;
                    break;
                case "80 ms":
                    takeSampleEvery = 1;
                    graph.getViewport().setMaxX(80);
                    graphPointsNumber = MAX_GRAPH_POINTS;
                    break;
                case "160 ms":
                    takeSampleEvery = 2;
                    graph.getViewport().setMaxX(160);
                    graphPointsNumber = MAX_GRAPH_POINTS;
                    break;
                case "320 ms":
                    takeSampleEvery = 4;
                    graph.getViewport().setMaxX(320);
                    graphPointsNumber = MAX_GRAPH_POINTS;
                    break;
                case "640 ms":
                    takeSampleEvery = 8;
                    graph.getViewport().setMaxX(640);
                    graphPointsNumber = MAX_GRAPH_POINTS;
                    break;
                case "1280 ms":
                    takeSampleEvery = 16;
                    graph.getViewport().setMaxX(1280);
                    graphPointsNumber = MAX_GRAPH_POINTS;
                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    };

    //Configurar de acordo com circuito de condicionamento do sinal (atenuação/amplificação)
    private AdapterView.OnItemSelectedListener voltageSpinnerListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            float factor = 1;
            String selected = (String) voltageSpinner.getItemAtPosition(position);
            switch (selected){
                case "1 V (5x)":
                    factor = (float)(1.0/5.0);
                    if(isAC){
                        graph.getViewport().setMaxY(0.5);
                        graph.getViewport().setMinY(-0.5);
                    } else {
                        graph.getViewport().setMaxY(1.0);
                        graph.getViewport().setMinY(0);
                    }
                    break;

                case "5 V (1/1)":
                    factor = 1;
                    if(isAC){
                        graph.getViewport().setMaxY(2.5);
                        graph.getViewport().setMinY(-2.5);
                    } else {
                        graph.getViewport().setMaxY(5.0);
                        graph.getViewport().setMinY(0);
                    }
                    break;

                case "20 V (1/4)":
                    factor = 4;
                    if(isAC){
                        graph.getViewport().setMaxY(10);
                        graph.getViewport().setMinY(-10);
                    } else {
                        graph.getViewport().setMaxY(20);
                        graph.getViewport().setMinY(0);
                    }
                    break;
            }

            graph.getGridLabelRenderer().setNumVerticalLabels(6);

            voltageScale = VOLTAGE_SCALE*factor;
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    };
}
