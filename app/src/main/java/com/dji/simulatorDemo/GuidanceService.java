package com.dji.simulatorDemo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.os.Handler;
import com.google.android.gms.maps.model.LatLng;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

//http://stackoverflow.com/questions/28535703/best-way-to-get-user-gps-location-in-background-in-android
public class GuidanceService extends Service implements android.location.LocationListener {
    private static final String TAG = "GPSService";
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 0;
    private static final float LOCATION_DISTANCE = 1f;

    private IBinder mBinder = new LocalBinder();

    private MainActivity mMainContext = null; //test


    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    //private int NOTIFICATION = R.string.cast_notification_connecting_message;



    private Location mLocLastLocation = null;
    private double mLastLocationMillis;


    private boolean isGPSGood;
    public static final String MY_PREFS_NAME = "gpsPrefs";

    protected Handler handler;

    private NotificationManager nm;

    private final int NOTIFICATIONID = 10;
    private String strGPSStatus = "";



    //perhaps use access methods
    public static final String PAUSE_ACTION = "com.dji.simulatorDemo.PAUSE_ACTION";
    public static final String STOP_ACTION = "com.dji.simulatorDemo.STOP_ACTION";
    public static final String START_ACTION = "com.dji.simulatorDemo.START_ACTION";


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder { //move this to it's own class
        public GuidanceService getService() {
            return GuidanceService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate");

        initializeLocationManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                //showNotification();
                //TEST
                if (ActivityCompat.checkSelfPermission(GuidanceService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(GuidanceService.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                mLocationManager.requestLocationUpdates(NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, GuidanceService.this);
                mLocationManager.requestLocationUpdates(GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, GuidanceService.this);
                //END TEST
// write your code to post content on server
            }
        });
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            //for (int i = 0; i < mLocationListeners.length; i++) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                mLocationManager.removeUpdates(this);
                Log.e(TAG, "removed updates");
            } catch (Exception ex) {
                Log.i(TAG, "fail to remove location listners, ignore", ex);
            }
            //}
        }
    }

    public Location getmLocLastLocation() {
        return mLocLastLocation;
    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Log.e(TAG, "initialized LocationManager");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void createNotification() {
        Notification.Builder notif;
        notif = new Notification.Builder(getApplicationContext());
        notif.setSmallIcon(R.mipmap.ic_launcher);
        notif.setContentTitle("GPS Service running");
        notif.setContentText("Options").setSmallIcon(R.mipmap.ic_launcher);

        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

/*        Intent IntentPause = new Intent(this, MainActivity.class);
        IntentPause.setAction(PAUSE_ACTION);
        PendingIntent pendingIntentPause = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), IntentPause, PendingIntent.FLAG_UPDATE_CURRENT);
        notif.addAction(R.mipmap.ic_launcher, "Pause Journey", pendingIntentPause);

        Intent IntentStop = new Intent(this, MainActivity.class);
        IntentStop.setAction(STOP_ACTION);
        PendingIntent pendingIntentStop = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), IntentStop, PendingIntent.FLAG_UPDATE_CURRENT);
        notif.addAction(R.mipmap.ic_launcher, "End Journey", pendingIntentStop);

        Intent IntentStart = new Intent(this, MainActivity.class);
        IntentStart.setAction(START_ACTION);
        PendingIntent pendingIntentStart = PendingIntent.getBroadcast(this, (int) System.currentTimeMillis(), IntentStart, PendingIntent.FLAG_UPDATE_CURRENT);
        notif.addAction(R.mipmap.ic_launcher, "Start Journey", pendingIntentStart).build();*/

        nm.notify(NOTIFICATIONID, notif.getNotification());

    }

    public void closeNotification() {
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATIONID);
    }

    public void setMapsContext(MainActivity context) {
        mMainContext = context;
    }

    public LocationManager getmLocationManager() {
        return mLocationManager;
    }

    public int getLOCATION_INTERVAL() {
        return LOCATION_INTERVAL;
    }

    public float getLOCATION_DISTANCE() {
        return LOCATION_DISTANCE;
    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();



        if (location == null) return;

        mLastLocationMillis = SystemClock.elapsedRealtime();


        mLocLastLocation = location;

        //Place current location marker
        LatLng latLng = new LatLng(latitude, longitude);

        SharedPreferences.Editor editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("gpsLatitude", ""+latitude);
        editor.putString("gpsLongitude", ""+longitude);
        editor.putString("gpsLocation", ""+location);
        editor.apply();


        Intent toBroadcast = new Intent("com.dji.simulatorDemo.ACTION_RECEIVE_LOCATION");
        toBroadcast.putExtra(MainActivity.EXTRA_LOCATION,location);
        this.sendBroadcast(toBroadcast);

        Log.e(TAG, "onLocationChanged: " + location);

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //final String tvTxt = textView.getText().toString();

        switch (status) {
            case LocationProvider.AVAILABLE:
                strGPSStatus = sortColour("GPS signal is Good","00FF00");
                break;
            case LocationProvider.OUT_OF_SERVICE:
                strGPSStatus = sortColour("GPS signal is Unavailable","FF0000");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                strGPSStatus = sortColour("GPS signal is weak","FF6600");
                break;
        }
    }

    //http://stackoverflow.com/questions/15882932/change-string-colour-javamail
    public static String sortColour(String str, String color) {
        return "<font color=#'" + color + "'>" + str + "</font>";
    }

    public  String getStrGPSStatus(){
        return strGPSStatus;
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.e(TAG, "onProviderDisabled: " + provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.e(TAG, "onProviderEnabled: " + provider);
    }

    public boolean isGPSGood() {
        return isGPSGood;
    }

}