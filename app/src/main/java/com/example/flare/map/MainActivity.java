package com.example.flare.map;

import android.Manifest;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.app.AlertDialog;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.location.Location;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.Random;
import java.util.Locale;
import java.io.IOException;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapquest.mapping.MapQuestAccountManager;
import com.mapquest.mapping.constants.Style;
import com.mapquest.mapping.maps.MapView;
import com.mapquest.mapping.maps.OnMapReadyCallback;
import com.mapquest.mapping.maps.MapboxMap;

import com.example.flare.microphone.MicrophoneActivity;
import android.util.Log;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener {

    private GoogleApiClient mGoogleApiClient;
    private MapboxMap mMap;
    private MapView mMapView;
    private MarkerOptions mMarkerOptions;
    private LatLng mLatLang = new LatLng(37.7749, -122.4194);
    private LocationRequest mLocationRequest;

    // Hardware Sensors
    private SensorManager mSensorManager;
    private Sensor mSensorOrientation;
    private float mCurrentDegree = 180f;
    private boolean mGatherOrientationSensorData = true;

    // Audio Recorder
    private MediaRecorder mMediaRecorder;
    private MediaPlayer mMediaPlayer;
    private String mMediaSavePath;

    // Speech to Text
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private boolean mHeldDown = false;
    private Icon mPositionIcon;
    private ViewSwitcher mViewSwitcher;

    private Random mRandom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.viewSwitcher);
        // Declare in and out animations and load them using AnimationUtils class
        Animation in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        Animation out = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);

        mViewSwitcher.setInAnimation(in);
        mViewSwitcher.setOutAnimation(out);

        mPositionIcon = IconFactory.getInstance(MainActivity.this).fromResource(R.drawable.default_marker);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        MapQuestAccountManager.start(getApplicationContext());
        mMapView = (MapView) findViewById(R.id.mapquestMapView);
        mMapView.onCreate(savedInstanceState);
        mLocationRequest = createLocationRequest();

        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mMap = mapboxMap;
                mMapView.setStyleUrl(Style.MAPQUEST_NIGHT);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        buildGoogleApiClient();
                    } else {
                        checkLocationPermission();
                    }

                    mRandom = new Random();
                    mMediaSavePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                            createRandomAudioFileName(5) + "Transmission.3gp";

                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        buildMediaRecorder();
                    } else {
                        checkAudioPermissions();
                    }

                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        checkWritePermissions();
                    }
                }
                else {
                    buildGoogleApiClient();
                }

                mMap.setOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng point) {
                        if(!mHeldDown) {
                            mHeldDown = true;
                            mViewSwitcher.showNext();

                            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                                    getString(R.string.speech_prompt));
                            try {
                                startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
                            } catch (ActivityNotFoundException a) {
                                Toast.makeText(getApplicationContext(),
                                        getString(R.string.speech_not_supported),
                                        Toast.LENGTH_SHORT).show();
                            }

//                            try {
//                                mMediaRecorder.prepare();
//                                mMediaRecorder.start();
//                            }
//                            catch (IllegalStateException e) {
//                                // TODO Auto-generated catch block
//                                e.printStackTrace();
//                            }
//                            catch (IOException e) {
//                                // TODO Auto-generated catch block
//                                e.printStackTrace();
//                            }

                        }
                    }
                });

                mMapView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        if(mHeldDown) {
                            if(motionEvent.getActionMasked() == motionEvent.ACTION_UP) {
                                mHeldDown = false;
//                                mMediaRecorder.stop();
//
//                                mMediaPlayer = new MediaPlayer();
//                                try {
//                                    mMediaPlayer.setDataSource(mMediaSavePath);
//                                    mMediaPlayer.prepare();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//
//                                mMediaPlayer.start();
//
//                                while(mMediaPlayer.isPlaying()) {
//                                    Log.d("com.example.flare.play", "" + mMediaPlayer.getCurrentPosition());
//                                }
//
//                                mMediaPlayer.stop();
//                                mMediaPlayer.release();

                                MediaPlayer beepPlayer = MediaPlayer.create(MainActivity.this, R.raw.end);
                                beepPlayer.start();
                                while(beepPlayer.isPlaying()) {}
                                beepPlayer.stop();
                                beepPlayer.release();

                                buildMediaRecorder();

                                mViewSwitcher.showPrevious();
                                return true;
                            }
                        }
                        return false;
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Toast.makeText(this, result.get(0), Toast.LENGTH_LONG).show();
                }
                break;
            }

        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = createLocationRequest();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private LocationRequest createLocationRequest() {
        // TODO: Battery level adjustments, and interval adjustments
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    private void updateCamera() {
        mMapView.setCameraDistance(10);
        CameraPosition position = new CameraPosition.Builder()
                .target(mLatLang) // Sets the new camera position
                .zoom(15) // Sets the zoom
                .bearing(mCurrentDegree) // Rotate the camera
                .tilt(30) // Set the camera tilt
                .build(); // Creates a CameraPosition from the builder

        mMap.animateCamera(CameraUpdateFactory
                .newCameraPosition(position), 7000);
    }

    private void updateMarker() {
        mMap.clear();
        mMarkerOptions = new MarkerOptions()
                .position(mLatLang)
                .icon(mPositionIcon);
        mMap.addMarker(mMarkerOptions);
    }

    private void addMarker() {
        mMarkerOptions = new MarkerOptions()
                .position(mLatLang)
                .icon(mPositionIcon);
        mMap.addMarker(mMarkerOptions);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLatLang = new LatLng(location.getLatitude(), location.getLongitude());

        updateCamera();
        if (mMarkerOptions != null) {
            updateMarker();
        }
        else{
            addMarker();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(mSensorOrientation != null) {
            if(mGatherOrientationSensorData) {
                mCurrentDegree = Math.round(event.values[0]);
                updateCamera();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if(sensor == mSensorOrientation) {
            switch (accuracy) {
                case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                    mGatherOrientationSensorData = true;
                    break;
                default:
                    mGatherOrientationSensorData = false;
                    break;
            }
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "You may experience a lag, but service will resume shortly.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(this, "You may experience a lag, but service will resume shortly.", Toast.LENGTH_LONG).show();
    }

    public void buildMediaRecorder(){
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        mMediaRecorder.setOutputFile(mMediaSavePath);
    }

    public String createRandomAudioFileName(int string){
        StringBuilder stringBuilder = new StringBuilder(string);
        int i = 0;

        while(i < string ) {
            stringBuilder.append("0123456789ABCDEFG".
                    charAt(mRandom.nextInt("0123456789ABCDEFG".length())));

            i++;
        }
        return stringBuilder.toString();
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Location Permission Needed")
                        .setMessage("This app needs the Location permission, please accept to use location functionality")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    public static final int MY_PERMISSIONS_WRITE_EX_STORAGE = 97;
    private void checkWritePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Storing to External Storage Permission Needed")
                        .setMessage("This app needs the store audio files for processing.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_WRITE_EX_STORAGE);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        MY_PERMISSIONS_WRITE_EX_STORAGE);
            }
        }
    }

    public static final int MY_PERMISSIONS_AUDIO = 98;
    private void checkAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Recording Audio Permission Required")
                        .setMessage("This app needs permission to record audio only when requested.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        MY_PERMISSIONS_AUDIO);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        MY_PERMISSIONS_AUDIO);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
        mSensorManager.registerListener(this, mSensorOrientation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                    }

                }
                else {
                    Toast.makeText(this, "You will need permissions to pinpoint your location.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case MY_PERMISSIONS_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

                        if (mMediaRecorder == null) {
                            buildMediaRecorder();
                        }
                    }

                }
                else {
                    Toast.makeText(this, "You will need permissions to record audio.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case MY_PERMISSIONS_WRITE_EX_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

//                        if (mMediaPlayer == null) {
//                            buildMediaRecorder();
//                        }
                    }

                }
                else {
                    Toast.makeText(this, "You will need permissions to store files.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

}