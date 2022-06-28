package com.dji.simulatorDemo;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static com.dji.simulatorDemo.GuidanceService.MY_PREFS_NAME;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SystemState;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.CompassCalibrationState;
import dji.common.flightcontroller.CompassState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.product.Model;
import dji.common.remotecontroller.GPSData;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import android.view.TextureView.SurfaceTextureListener;
import static com.dji.simulatorDemo.GuidanceService.MY_PREFS_NAME;
import static com.dji.simulatorDemo.GuidanceService.MY_DRONE_PREFS_NAME;

public class MainActivity extends AppCompatActivity implements SurfaceTextureListener, View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback, Thread.UncaughtExceptionHandler {

    private static final String TAG = MainActivity.class.getName();

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    //private FlightController mFlightController;
    protected TextView mConnectStatusTextView;

    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextureView mVideoSurface = null;

    private Button mBtnEnableVirtualStick;
    //private Button mBtnDisableVirtualStick;
    private Button mBtnTakeOff;
    private Button mBtnLand;
    private Button locate, add, clear;
    private Button config, BtnToggleCam, mBtnStartMission;

    AlertDialog alertDialog = null;

    private TextView mTextView, mTextView2, mGPSCount, mGPSSignalLevel, mCompass;

    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private GPSData.GPSLocation rcLocation;
    private LocationCoordinate3D droneLocation = null, prevDroneLocation = new LocationCoordinate3D(0, 0, 0);
    private int gpsCount = 0;
    private GPSSignalLevel gpsSignalLevel = null;

    private boolean isCamDialogMaxSize = false;

    private GoogleMap gMap;

    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private boolean isAdd = false, isFollowing = false, isOnMission = false;

    //private DialogFragment newFragment = null;

    private int camScreenWidth = 150;
    private int camScreenHeight = 100;
    private int droneLocationUpdateCounter = 0;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    //private LocationManager locationManager;
    private Marker rcMarker = null;

    private CompassState mCompassState = null;
    //===========================================================
    Intent iGPSService;
    public static final String EXTRA_LOCATION = "EXTRA_LOCATION";
    //public static final String EXTRA_FLIGHT_CONTROLLER = "EXTRA_FLIGHT_CONTROLLER";
    public static final String EXTRA_DRONE_LOCATION = "EXTRA_DRONE_LOCATION";
    public static final String EXTRA_GPS_COUNT = "EXTRA_GPS_COUNT";
    public static final String EXTRA_GPS_SIGNAL_LEVEL = "EXTRA_GPS_SIGNAL_LEVEL";
    boolean mBounded;
    GuidanceService mService;
    LocationManager mLocationManager = null;
    int LOCATION_INTERVAL = 0;
    float LOCATION_DISTANCE = 0;

    private final BroadcastReceiver LocationUpdateReceiver = new BroadcastReceiver() {

        /**
         * Receives broadcast from GPS class/service.
         */
        @Override
        public void onReceive(Context context, Intent intent) {

            Bundle extras = intent.getExtras();
            //Bundle extras = intent.getBundleExtra("com.passgen.tobyatherton.pointtest.ACTION_RECEIVE_LOCATION");

            //THIS MAY BE AN ISSUE GIVEN HOW FAST THE DRONE AND THE PHONE UPDATES
            if(intent.getAction().equals("com.dji.simulatorDemo.ACTION_RECEIVE_LOCATION"))  {
                //GET THE CONTROLLER LOCATION.
                Location location = (Location) extras.get(MainActivity.EXTRA_LOCATION);

                rcLocation = new GPSData.GPSLocation(location.getLatitude(), location.getLongitude());
                if (mTextView2 != null && rcLocation != null) {
                    mTextView2.setText("Controller gps data: set");

                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(new LatLng(location.getLatitude(), location.getLongitude()));
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                    if (rcMarker != null) {
                        rcMarker.remove();
                    }
                    rcMarker = gMap.addMarker(markerOptions);
                }
            } else if (intent.getAction().equals("com.dji.simulatorDemo.ACTION_RECEIVE_DRONE_LOCATION")) {
                //GET THE DRONE LOCATION
                droneLocation = (LocationCoordinate3D) getIntent().getExtras().getSerializable(MainActivity.EXTRA_DRONE_LOCATION);
                gpsSignalLevel = (GPSSignalLevel) extras.get(MainActivity.EXTRA_GPS_SIGNAL_LEVEL);
                gpsCount = (int) extras.get(MainActivity.EXTRA_GPS_COUNT);

                //TEST
                //Update map to drones location
                if(droneLocationUpdateCounter == 10) {
                    updateDroneLocation();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if(gMap != null) {
                                //Map doing something weird like going blank at runtime
                                mapCameraUpdate();
                            }// Locate the drone's place
                        }
                    });
                    droneLocationUpdateCounter = 0;
                } else {
                    droneLocationUpdateCounter += 1;
                }

                //TESTING
                if(rcLocation != null && droneLocation != null) {
                    Location rcLoc = new Location("");
                    Location droneLoc = new Location("");
                    rcLoc.setLatitude(rcLocation.getLatitude());
                    rcLoc.setLongitude(rcLocation.getLongitude());

                    droneLoc.setLatitude(droneLocation.getLatitude());
                    droneLoc.setLongitude(droneLocation.getLongitude());
                    //droneLoc.bearingTo(rcLoc);
                    float bearingTest =  getBearing(rcLocation.getLatitude(),rcLocation.getLongitude(),droneLocation.getLatitude(),droneLocation.getLongitude());

                    if(!Float.isNaN(bearingTest)) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mConnectStatusTextView.setText("Bearing is: " + bearingTest);
                            }
                        });
                    }
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent mIntent = new Intent(this, GuidanceService.class);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            //if(mBounded) {
                mService.createNotification(); //TEST
            //}
        }
    }

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //Toast.makeText(MapsActivity.this, "Service is disconnected", Toast.LENGTH_LONG).show();
            mBounded = false;
            mService = null;
            //mService.stopService(iGPSService);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Toast.makeText(MapsActivity.this, "Service is connected", Toast.LENGTH_LONG).show();
            GuidanceService.LocalBinder bindService = (GuidanceService.LocalBinder) service;
            mService = bindService.getService();
            mService.setMapsContext(MainActivity.this);
            mBounded = true;

            //test
            iGPSService = new Intent(MainActivity.this, GuidanceService.class);
            //startService(iGPSService);
            mService.startService(iGPSService);
            //test
            mLocationManager = mService.getmLocationManager();
            LOCATION_INTERVAL = mService.getLOCATION_INTERVAL();
            LOCATION_DISTANCE = mService.getLOCATION_DISTANCE();

            //use this to get location?
            SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
            String spGPSLocation = prefs.getString("gpsLocation", "");
            if (!spGPSLocation.equalsIgnoreCase("")) {
                //mLastLocation = prefs.getAll("gpsLocation", "")//spGPSLocation;  /* Edit the value here*/
            }


            try {

                if (Build.VERSION.SDK_INT >= 23 && mService != null) {
                    if (!checkIfAlreadyhavePermission()) {
                        requestForSpecificPermission();
                    } else {
                        mLocationManager.requestLocationUpdates(NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mService);
                    }
                } else if (mService != null) {
                    mLocationManager.requestLocationUpdates(
                            NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            mService);
                }

            } catch (SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "network provider does not exist, " + ex.getMessage());
            }

            try {

                //do all this in mapsactivity
                if (Build.VERSION.SDK_INT >= 23 && mService != null) {
                    if (!checkIfAlreadyhavePermission()) {
                        requestForSpecificPermission();
                    } else {
                        mLocationManager.requestLocationUpdates(
                                GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                                mService);
                    }
                } else if (mService != null) {
                    mLocationManager.requestLocationUpdates(
                            GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            mService);
                }

            } catch (SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "gps provider does not exist " + ex.getMessage());
            }
            //end test

        }
    };
    //===========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        initUI();
        //TEST
        if(mService != null) {
            mService.initFlightController();
        }
        //initFlightController();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int UI_OPTIONS = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            getWindow().getDecorView().setSystemUiVisibility(UI_OPTIONS);
        }


        LinearLayout camLayout = findViewById(R.id.camLayout);
        camLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ADD your action here
                //int res=(Integer) v.getTag();
                //ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
                //showToast("Width: " + camLayout.getLayoutParams().width + "Height: " + camLayout.getLayoutParams().height + "x: ");
                if (!isCamDialogMaxSize) {
                    /*RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(camLayout.getLayoutParams());
                    lp.width = 450;
                    lp.height = 300;
                    camLayout.setLayoutParams(lp);*/
                    ViewGroup.LayoutParams params = camLayout.getLayoutParams();
                    uninitPreviewer();
                    params.height = params.height + 450;
                    params.width = params.width + 600;
                    camLayout.setLayoutParams(params);
                    mVideoSurface.setLayoutParams(new LinearLayout.LayoutParams(params.width, params.height));
                    initPreviewer();
                    isCamDialogMaxSize = true;
                } else {
                    /*RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(camLayout.getLayoutParams());
                    lp.width = 150;
                    lp.height = 100;
                    camLayout.setLayoutParams(lp);*/
                    ViewGroup.LayoutParams params = camLayout.getLayoutParams();
                    uninitPreviewer();
                    params.height = params.height - 450;
                    params.width = params.width - 600;
                    mVideoSurface.setLayoutParams(new LinearLayout.LayoutParams(params.width, params.height));
                    camLayout.setLayoutParams(params);
                    initPreviewer();
                    isCamDialogMaxSize = false;
                }
            }
        });

        //CAMERA FEED VIEW

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        Camera camera = DJISimulatorApplication.getCameraInstance();

        if (camera != null) {

            camera.setSystemStateCallback(new SystemState.Callback() {
                @Override
                public void onUpdate(SystemState cameraSystemState) {
                    if (null != cameraSystemState) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        //final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                //recordingTime.setText(timeString);

                                /*
                                 * Update recordingTime TextView visibility and mRecordBtn's check state
                                 */
   /*                             if (isVideoRecording){
                                    recordingTime.setVisibility(View.VISIBLE);
                                }else
                                {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }*/
                            }
                        });
                    }
                }
            });
        }
    }

    //USE THIS TO GET EVENTS FROM THE BUTTONS ON THE NOTIFICATION FROM THE SERVICE
    public class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if (GuidanceService.PAUSE_ACTION.equals(action)) {
                Toast.makeText(context, "PAUSE CALLED", Toast.LENGTH_SHORT).show();
            } else if (GuidanceService.START_ACTION.equals(action)) {
                Toast.makeText(context, "START CALLED", Toast.LENGTH_SHORT).show();
            } else if (GuidanceService.STOP_ACTION.equals(action)) {
                Toast.makeText(context, "STOP CALLED", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void requestForSpecificPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 123);
    }

    public boolean checkIfAlreadyhavePermission() {
        int resultAFL = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int resultACL = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        return resultAFL == PackageManager.PERMISSION_GRANTED && resultACL == PackageManager.PERMISSION_GRANTED;
    }

    private void isLocationEnabled() {

        if (!mLocationManager.isProviderEnabled(GPS_PROVIDER)) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle("Enable Location");
            alertDialog.setMessage("Your locations setting is not enabled. Please enabled it in settings menu.");
            alertDialog.setPositiveButton("Location Settings", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            AlertDialog alert = alertDialog.create();
            alert.show();
        }
        /*else{
            AlertDialog.Builder alertDialog=new AlertDialog.Builder(this);
            alertDialog.setTitle("Confirm Location");
            alertDialog.setMessage("Your Location is enabled, and ready to use");
            alertDialog.setNegativeButton("Back to interface",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    dialog.cancel();
                }
            });
            AlertDialog alert=alertDialog.create();
            alert.show();
        }*/
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("Register Success");
                            } else {
                                showToast("Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            showToast("Product Disconnected");

                        }

                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            showToast("Product Connected");

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }

                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }
    }

    private void initPreviewer() {

        BaseProduct product = DJISimulatorApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast("Disconnected");
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
            }
        }
    }

    private void uninitPreviewer() {
        Camera camera = DJISimulatorApplication.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object

    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        //SET THIS UP TO WORK WITHOUT USING WAYPOINT API
        if (isAdd == true) {
            markWaypoint(point);
/*            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }*/
        } else {
            setResultToToast("Cannot Add Waypoint");
        }
    }

    private double distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = sin(deg2rad(lat1))
                * sin(deg2rad(lat2))
                + cos(deg2rad(lat1))
                * cos(deg2rad(lat2))
                * cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = dist * 180.0 / Math.PI;
        dist = dist * 60 * 1.1515 * 1000;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private void FollowRCUser() {
        //If location has been updated
        //Remove this
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mThrottle = 0;
        //end remove
        if (prevDroneLocation != null && droneLocation != null && !prevDroneLocation.equals(droneLocation) && isFollowing) {
            showToast("Follow mode engaged");
            //do logic
            //while drone greater than 2 metres from controller
            while (distanceBetween(droneLocation.getLatitude(), droneLocation.getLongitude(), rcLocation.getLatitude(), rcLocation.getLongitude()) > 2 && isFollowing) {
                mYaw = (float) getBearing(droneLocation.getLatitude(), droneLocation.getLongitude(), rcLocation.getLatitude(), rcLocation.getLongitude());

                if (mService != null && mService.mFlightController != null) {
                    mService.mFlightController.sendVirtualStickFlightControlData(
                            new FlightControlData(
                                    mPitch, mRoll, mYaw, mThrottle
                            ), djiError -> {

                            }
                    );
                }
            /*if (null == mSendVirtualStickDataTimer) {
                mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                mSendVirtualStickDataTimer = new Timer();
                mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
            }*/
            }
            //Default values if not following
            mPitch = 0;
            mRoll = 0;
            mYaw = 0;
            mThrottle = 0;
            prevDroneLocation = droneLocation;
        }
        //Default values if not following
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mThrottle = 0;
    }

    private void RunGPSMission() {
        //If location has been updated
        //Remove this
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mThrottle = 0;
        //end remove
        if (droneLocation != null && isOnMission) {
            showToast("Mission mode engaged");
            //do logic
            //while drone greater than 5 metres from controller

            Iterator it = mMarkers.entrySet().iterator();
            while (it.hasNext() && isOnMission) {
                Map.Entry<Integer, Marker> pair = (Map.Entry<Integer, Marker>) it.next();
                Marker m = pair.getValue();

                while (distanceBetween(droneLocation.getLatitude(), droneLocation.getLongitude(), m.getPosition().latitude, m.getPosition().longitude) > 5 && isOnMission) {
                    mYaw = getBearing(droneLocation.getLatitude(), droneLocation.getLongitude(), m.getPosition().latitude, m.getPosition().longitude);

                    if (mService != null && mService.mFlightController != null) {
                        mService.mFlightController.sendVirtualStickFlightControlData(
                                new FlightControlData(
                                        mPitch, mRoll, mYaw, mThrottle
                                ), djiError -> {

                                }
                        );
                    }
                    wait(1000);
                }
                it.remove(); // avoids a ConcurrentModificationException
            }

            //Default values if not following
            mPitch = 0;
            mRoll = 0;
            mYaw = 0;
            mThrottle = 0;
            //prevDroneLocation = droneLocation;
        }
        //Default values if not following
        mPitch = 0;
        mRoll = 0;
        mYaw = 0;
        mThrottle = 0;
    }

    public static void wait(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {
        if (droneLocation != null) {
            LatLng pos = new LatLng(droneLocation.getLatitude(), droneLocation.getLongitude());
            //Create MarkerOptions object
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (droneMarker != null) {
                        droneMarker.remove();
                    }

                    if (gMap != null && markerOptions != null && checkGpsCoordination(droneLocation.getLatitude(), droneLocation.getLongitude())) {
                        droneMarker = gMap.addMarker(markerOptions);
                        if (mService != null && mService.mFlightController != null && !mService.mFlightController.getCompass().isCalibrating() && !mService.mFlightController.getCompass().hasError()) {
        /*                    if(mFlightController.getCompass().getCalibrationState() != CompassCalibrationState.NOT_CALIBRATING || mFlightController.getCompass().getCalibrationState() != CompassCalibrationState.SUCCESSFUL) {
                                mFlightController.getCompass().startCalibration();
                            }*/
                            droneMarker.setRotation(mService.mFlightController.getCompass().getHeading()); //mYaw Set the image rotation to the yaw of the aircraft
                        }
                    }
                }
            });
        }
    }

    private void markWaypoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
        if (rcLocation != null) {
            LatLng userLocation = new LatLng(rcLocation.getLatitude(), rcLocation.getLongitude());
            gMap.addMarker(new MarkerOptions().position(userLocation).title("You are here"));
            gMap.moveCamera(CameraUpdateFactory.newLatLng(userLocation));
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateTitleBar() {
        if (mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJISimulatorApplication.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJISimulatorApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
            if (mTextView != null && droneLocation != null && droneLocation.getLatitude() != 0 && droneLocation.getLongitude() != 0)
                mTextView.setText("Aircraft gps set");
            if (mTextView2 != null && rcLocation != null)
                mTextView2.setText("Controller gps set");
            if (mGPSSignalLevel != null && gpsSignalLevel != null)
                mGPSSignalLevel.setText("Signal Level: " + String.valueOf(gpsSignalLevel.value()));
            if (mGPSCount != null)
                mGPSCount.setText("Satellites connected: " + Integer.toString(gpsCount));
        }

        if (!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateTitleBar();
        //TEST
        if(mService != null) {
            mService.initFlightController();
        }
        loginAccount();
        initPreviewer();
        isLocationEnabled();

        if (mLocationManager != null) {
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
            mLocationManager.requestLocationUpdates(
                    NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mService);

            mLocationManager.requestLocationUpdates(GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mService);
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
        uninitPreviewer();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
            //try to run this?
            //mService.performOnBackgroundThread();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mService.closeNotification();
            }
        }
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        uninitPreviewer();
        super.onDestroy();
        unregisterReceiver(LocationUpdateReceiver);
        if(mService != null) {
            mService.stopService(iGPSService);
        }
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        showToast("Login Success: " + userAccountState.name());
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //loginStatusTv.setText(userAccountState.name());
                            }
                        });
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error: " + error.getDescription());
                    }
                });
    }

    //EXTRACT LOGIC FROM HERE AND DO IN SERVICE BROADCASTER

    /*private void initFlightController() {

        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            *//*mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                            String yaw = String.format("%.2f", stateData.getYaw());
                            String pitch = String.format("%.2f", stateData.getPitch());
                            String roll = String.format("%.2f", stateData.getRoll());
                            String positionX = String.format("%.2f", stateData.getPositionX());
                            String positionY = String.format("%.2f", stateData.getPositionY());
                            String positionZ = String.format("%.2f", stateData.getPositionZ());

                            mTextView.setText("Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                                    ", PosY : " + positionY +
                                    ", PosZ : " + positionZ);
                        }
                    });
                }
            });*//*
            // GET RC GPS position
            aircraft.getRemoteController().setGPSDataCallback(new GPSData.Callback() {
                @Override
                public void onUpdate(@NonNull GPSData gpsData) {
                    rcLocation = gpsData.getLocation();
                    //String msg="controller Latitude: "+gpsData.getLocation().getLatitude() + "controller Longitude: "+gpsData.getLocation().getLongitude();
                    //Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
                }
            });

            if (mFlightController != null) {
*//*                mFlightController.setStateCallback(
                        djiFlightControllerCurrentState -> {
                            droneLocation.setLatitude(djiFlightControllerCurrentState.getAircraftLocation().getLatitude());
                            droneLocation.setLongitude(djiFlightControllerCurrentState.getAircraftLocation().getLongitude());
                            droneLocation.setAltitude(djiFlightControllerCurrentState.getAircraftLocation().getAltitude());
                            gpsCount = djiFlightControllerCurrentState.getSatelliteCount();
                            gpsSignalLevel = djiFlightControllerCurrentState.getGPSSignalLevel();
                            //updateDroneLocation(); //update map marker in this method

                            //TESTING
                            if(rcLocation != null) {
                                double bearingTest = this.getBearing(rcLocation.getLatitude(),rcLocation.getLongitude(),droneLocation.getLatitude(),droneLocation.getLongitude());
                                mConnectStatusTextView.setText("Bearing is: " + bearingTest);
                            }
                        });*//*
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

                        //TEST
                        //Update map to drones location
                        if(droneLocationUpdateCounter == 10) {
                            updateDroneLocation();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if(gMap != null) {
                                        //Map doing something weird like going blank at runtime
                                        mapCameraUpdate();
                                    }// Locate the drone's place
                                }
                            });
                            droneLocationUpdateCounter = 0;
                        } else {
                            droneLocationUpdateCounter += 1;
                        }

                        //TESTING
                        if(rcLocation != null && droneLocation != null) {
                            Location rcLoc = new Location("");
                            Location droneLoc = new Location("");
                            rcLoc.setLatitude(rcLocation.getLatitude());
                            rcLoc.setLongitude(rcLocation.getLongitude());

                            droneLoc.setLatitude(droneLocation.getLatitude());
                            droneLoc.setLongitude(droneLocation.getLongitude());
                            //droneLoc.bearingTo(rcLoc);
                            float bearingTest =  getBearing(rcLocation.getLatitude(),rcLocation.getLongitude(),droneLocation.getLatitude(),droneLocation.getLongitude());

                            if(!Float.isNaN(bearingTest)) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        mConnectStatusTextView.setText("Bearing is: " + bearingTest);
                                    }
                                });
                            }
                        }
                    }
                });

               *//* mFlightController.getCompass().setCompassStateCallback(new CompassState.Callback() {
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
                });*//*
            }
        }
    }*/

/*    private double getBearing(double startLat, double startLng, double endLat, double endLng){
        double latitude1 = Math.toRadians(startLat);
        double latitude2 = Math.toRadians(endLat);
        double longDiff= Math.toRadians(endLng - startLng);
        double y= Math.sin(longDiff)*Math.cos(latitude2);
        double x=Math.cos(latitude1)*Math.sin(latitude2)-Math.sin(latitude1)*Math.cos(latitude2)*Math.cos(longDiff);

        return Math.toDegrees(Math.atan2(y, x));//+360)%360;
    }*/

    public float getBearing(double startLatitude,double startLongitude, double endLatitude, double endLongitude){
        double Phi1 = Math.toRadians(startLatitude);
        double Phi2 = Math.toRadians(endLatitude);
        double DeltaLambda = Math.toRadians(endLongitude - startLongitude);

        double Theta = atan2((sin(DeltaLambda)*cos(Phi2)) , (cos(Phi1)*sin(Phi2) - sin(Phi1)*cos(Phi2)*cos(DeltaLambda)));
        return (float)Math.toDegrees(Theta);
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        mBtnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        mBtnStartMission = (Button) findViewById(R.id.btn_start_mission);
        //mBtnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_land);
        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mTextView2 = (TextView) findViewById(R.id.textview_simulator2);
        mGPSCount = (TextView) findViewById(R.id.textview_gpscount);
        mGPSSignalLevel = (TextView) findViewById(R.id.textview_gpssignallevel);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        mCompass = (TextView) findViewById(R.id.textview_compass);
        //mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        //mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
        locate = (Button) findViewById(R.id.locate);
        BtnToggleCam = (Button) findViewById(R.id.toggleCam);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        BtnToggleCam.setOnClickListener(this);

        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnStartMission.setOnClickListener(this);
        //mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);

        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

/*        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                mRoll = (float)(rollJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }

            }

        });

        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                }

            }
        });*/
    }

    private boolean isAircratConnected() {
        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft != null && aircraft.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                if(!isAircratConnected()) {
                    showToast("Aircraft not connected");
                    break;
                }
                if (!isFollowing) {
                    if(droneLocation != null && rcLocation != null) {
                        new AlertDialog.Builder(this)
                                .setTitle("Confirm")
                                .setMessage("Do you want to put the aircraft into follow mode?")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        isFollowing = true;
                                        mBtnEnableVirtualStick.setText("Stop Following");
                                        //Enabled virtual simulator
                                        if (mService != null && mService.mFlightController != null) {

                                            mService.mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                                                @Override
                                                public void onResult(DJIError djiError) {
                                                    if (djiError != null) {
                                                        showToast(djiError.getDescription());
                                                    } else {
                                                        showToast("Enable Virtual Stick Success");
                                                        //Call Start Following code here
                                                        FollowRCUser();
                                                    }
                                                }
                                            });

                                        }
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    } else {
                        showToast("Drone location or controller location not set.");
                    }
                }else{
                    isFollowing = false;
                    mBtnEnableVirtualStick.setText("Start Follow");
                    //Call Stop following code here

                    //Disable virtual simulator
                    if (mService != null && mService.mFlightController != null){
                        mService.mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Follow mode disable");
                                }
                            }
                        });
                    }
                }
                break;

/*            case R.id.btn_disable_virtual_stick:

                if (mFlightController != null){
                    mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Disable Virtual Stick Success");
                            }
                        }
                    });
                }
                break;*/

            case R.id.btn_take_off:
                if (mService != null && mService.mFlightController != null){
                    mService.mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("Take off Success");
                                    }
                                }
                            }
                    );
                }

                break;

            case R.id.btn_land:
                if (mService != null && mService.mFlightController != null){

                    mService.mFlightController.startLanding(
                            djiError -> {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Start Landing");
                                }
                            }
                    );

                }

                break;
            case R.id.btn_start_mission:
                if(!isAircratConnected()) {
                    showToast("Aircraft not connected");
                    break;
                }
                if (!isOnMission) {
                    if(droneLocation != null && mMarkers.size() > 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("Confirm")
                                .setMessage("Do you want to start the mission?")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        //Enabled virtual simulator
                                        if (mService != null && mService.mFlightController != null) {

                                            mService.mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                                                @Override
                                                public void onResult(DJIError djiError) {
                                                    if (djiError != null) {
                                                        showToast(djiError.getDescription());
                                                    } else {
                                                        showToast("Enable Virtual Stick Success");
                                                        isOnMission = true;
                                                        runOnUiThread(new Runnable() {
                                                            public void run() {
                                                                mBtnStartMission.setText("Stop Mission");
                                                            }
                                                        });
                                                        //Call Start mission code here
                                                        RunGPSMission();
                                                    }
                                                }
                                            });

                                        }
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    } else {
                        showToast("Drone location or mission locations not set.");
                    }
                }else{

                    //Disable virtual simulator
                    if (mService != null && mService.mFlightController != null){
                        mService.mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    isOnMission = false;
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            mBtnStartMission.setText("Start Mission");
                                        }
                                    });
                                    showToast("Mission mode disabled");
                                }
                            }
                        });
                    }
                }
                break;
            case R.id.locate:{
                updateDroneLocation();
                mapCameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                mMarkers.clear();//waypointList.clear();
                //waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                break;
            }

            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.toggleCam:{

                break;
            }
            default:
                break;
        }
    }

    private void mapCameraUpdate(){
        if(droneLocation != null) {
            LatLng pos = new LatLng(droneLocation.getLatitude(), droneLocation.getLongitude());
            float zoomlevel = (float) 18.0;
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
            gMap.moveCamera(cu);
        }
    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //SET THIS UP TO WORK WITHOUT USING WAYPOINT API
                Log.d(TAG, "Select finish action");
/*                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }*/
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //SET THIS UP TO WORK WITHOUT USING WAYPOINT API
                Log.d(TAG, "Select heading");

/*                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }*/
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        //SET THIS UP TO WORK WITHOUT USING WAYPOINT API
                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        /*Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();*/
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

        String nulltoIntegerDefalt(String value){
            if(!isIntValue(value)) value="0";
            return value;
        }

        boolean isIntValue(String val)
        {
            try {
                val=val.replace(" ","");
                Integer.parseInt(val);
            } catch (Exception e) {return false;}
            return true;
        }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        Log.e("Error:", "uncaught_exception_handler: uncaught exception in thread " + thread.getName(), throwable);

        //hack to rethrow unchecked exceptions
        if(throwable instanceof RuntimeException)
            throw (RuntimeException)throwable;
        if(throwable instanceof Error)
            throw (Error)throwable;

        //this should really never happen
        Log.e("Error:", "uncaught_exception handler: unable to rethrow checked exception");
    }

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mService != null && mService.mFlightController != null) {
                mService.mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), djiError -> {

                        }
                );
            }
        }
    }
}
