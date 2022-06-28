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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.Serializable;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.remotecontroller.GPSData;
import dji.sdk.products.Aircraft;
import dji.sdk.flightcontroller.FlightController;

//ADD DRONE LOCATION UPDATES TO THIS SERVICE MAYBE

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
    public static final String MY_DRONE_PREFS_NAME = "gpsDronePrefs";

    protected Handler handler;

    private NotificationManager nm;

    private final int NOTIFICATIONID = 10;
    private String strGPSStatus = "";

    public FlightController mFlightController;
    private LocationCoordinate3D droneLocation = null, prevDroneLocation = new LocationCoordinate3D(0, 0, 0);
    private int gpsCount = 0;
    private GPSSignalLevel gpsSignalLevel = null;

    //perhaps use access methods
    public static final String PAUSE_ACTION = "com.dji.simulatorDemo.PAUSE_ACTION";
    public static final String STOP_ACTION = "com.dji.simulatorDemo.STOP_ACTION";
    public static final String START_ACTION = "com.dji.simulatorDemo.START_ACTION";
    private Context context;

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
        initFlightController();
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
                initFlightController();
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

    public void initFlightController() {

        context = this;
        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

            if (mFlightController != null) {

                mFlightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(FlightControllerState state) {
                        if(droneLocation == null) {
                            droneLocation = new LocationCoordinate3D(state.getAircraftLocation().getLatitude(),state.getAircraftLocation().getLongitude(),state.getAircraftLocation().getAltitude());
                        }
                        else {
                            droneLocation.setLatitude(state.getAircraftLocation().getLatitude());
                            droneLocation.setLongitude(state.getAircraftLocation().getLongitude());
                            droneLocation.setAltitude(state.getAircraftLocation().getAltitude());
                        }
                        gpsCount = state.getSatelliteCount();
                        gpsSignalLevel = state.getGPSSignalLevel();


                        SharedPreferences.Editor editor = getSharedPreferences(MY_DRONE_PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putString("gpsDroneLatitude", ""+droneLocation.getLatitude());
                        editor.putString("gpsDroneLongitude", ""+droneLocation.getLongitude());
                        editor.putString("gpsDroneLocation", ""+droneLocation);
                        editor.apply();


                        Intent toBroadcast = new Intent("com.dji.simulatorDemo.ACTION_RECEIVE_DRONE_LOCATION");
                        //toBroadcast.putExtra(MainActivity.EXTRA_FLIGHT_CONTROLLER, (Serializable) mFlightController);
                        toBroadcast.putExtra(MainActivity.EXTRA_DRONE_LOCATION, (Serializable) droneLocation);
                        toBroadcast.putExtra(MainActivity.EXTRA_GPS_COUNT,gpsCount);
                        toBroadcast.putExtra(MainActivity.EXTRA_GPS_SIGNAL_LEVEL,gpsSignalLevel);
                        context.sendBroadcast(toBroadcast);

                        Log.e(TAG, "onDroneLocationChanged: " + droneLocation);

                    }
                });

               /* mFlightController.getCompass().setCompassStateCallback(new CompassState.Callback() {
                    @Override
                    public void onUpdate(@NonNull CompassState compassState) {
                        mCompassState = compassState;
                        if (mCompass != null && mCompassState != null) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if(mCompassState != null && mCompassState.getSensorState() != null) {
                                        String s = mCompassState.getSensorState().name();
                                        //mCompass.setText(mCompassState.getSensorState().name());
                                    }
                                }
                            });
                        }
                    }
                });*/
            }
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