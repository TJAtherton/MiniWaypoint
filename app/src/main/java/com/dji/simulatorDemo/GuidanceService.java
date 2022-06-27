package com.dji.simulatorDemo;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class GuidanceService extends Service {

    //Drone class to send commands to the drone
    //Guidance class for heading and position constantly updating on a seperate thread. callback will have callback a few times a seconds
    //Look into wanky linear algebra for the drone stuff
    private static final String TAG = "MyService";
    Thread readthread;
    Handler mHandler=new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        //Toast.makeText(this, "My Service Created", Toast.LENGTH_LONG).show(); //is shown

        readthread = new Thread(new Runnable() { public void run() { try {
            runa();
        } catch (Exception e) {
            //TODO Auto-generated catch block
            e.printStackTrace();
        } } });

        readthread.start();

        Log.d(TAG, "onCreate");


    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onDestroy");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Service started by user.", Toast.LENGTH_LONG).show();
        return START_STICKY;
    }

    public void runa() throws Exception{
        //To hit ui thread from another thread
        mHandler.post(new Runnable(){
            public void run(){
                Toast.makeText(GuidanceService.this, "test", Toast.LENGTH_LONG).show();
            }
        });
    }
}