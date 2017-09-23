package com.flare.main;

import android.Manifest;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Build;
import android.app.AlertDialog;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.location.Location;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.DialogInterface;
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
import android.support.design.widget.FloatingActionButton;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.mapboxsdk.annotations.Marker;
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

import android.util.Log;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener, RecognitionListener {

    public final String MEDICAL = "medical";
    public final String FOOD = "food";
    public final String HOSPITAL = "medical";
    public final String FIRE = "fire";
    public final String TRANSIT = "transit";

    private GoogleApiClient mGoogleApiClient;
    private MapboxMap mMap;
    private MapView mMapView;
    private MarkerOptions mMarkerOptions;
    private LatLng mLatLang = new LatLng(37.7749, -122.4194);
    private LocationRequest mLocationRequest;
    private FloatingActionButton mFollowButton;

    // Hardware Sensors
    private SensorManager mSensorManager;
    private Sensor mSensorOrientation;
    private float mCurrentDegree = 180f;
    private boolean mHasLoadedMap = false;
    private boolean mFollow = true;
    private boolean mHasFlown = false;
    private boolean mGatherOrientationSensorData = true;
    private Marker mLastMarker;

    // Speech to Text
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private boolean mHeldDown = false;
    private Icon mPositionIcon;
    private ViewSwitcher mViewSwitcher;

    private SpeechRecognizer mSpeechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapQuestAccountManager.start(getApplicationContext());
        setContentView(R.layout.activity_main);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.viewSwitcher);
        Animation in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        Animation out = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);

        mViewSwitcher.setInAnimation(in);
        mViewSwitcher.setOutAnimation(out);

        mMapView = (MapView) findViewById(R.id.mapquestMapView);
        mMapView.onCreate(savedInstanceState);
        mLocationRequest = createLocationRequest();

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
        mSpeechRecognizer.setRecognitionListener(this);

        Intent intent = getIntent();

        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mMap = mapboxMap;
                mMap.setMyLocationEnabled(true);
                mMapView.setStyleUrl(Style.MAPQUEST_NIGHT);

                mPositionIcon = IconFactory.getInstance(MainActivity.this).fromResource(R.drawable.default_marker);
                mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                mSensorOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

                mFollowButton = (FloatingActionButton) findViewById(R.id.centerButton);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int location_permission = ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION);
                    int audio_permission = ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.RECORD_AUDIO);

                    if (location_permission == PackageManager.PERMISSION_GRANTED
                            && audio_permission == PackageManager.PERMISSION_GRANTED) {
                        buildGoogleApiClient();
                        flyOver();
                    } else {
                        checkLocationPermission();
                        checkAudioPermission();
                    }
                }
                else {
                    buildGoogleApiClient();
                }
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = createLocationRequest();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
        mHasLoadedMap = true;
        Log.d("com.flare", "Damnit");

        try {
            int location_on = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            if(location_on == 0) {
                new AlertDialog.Builder(this)
                        .setTitle("To continue, let your device turn on location.")
                        .setMessage("Your device will need to Use GPS to help pinpoint your location.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

                                try {
                                    Location location = mMap.getMyLocation();
                                    mLatLang = new LatLng(location.getLatitude(), location.getLongitude());
                                    flyOver();
                                }
                                catch (NullPointerException e) {
                                    // pass
                                }
                            }
                        })
                        .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        })
                        .create()
                        .show();
            }
            else {
                try {
                    Location location = mMap.getMyLocation();
                    mLatLang = new LatLng(location.getLatitude(), location.getLongitude());
                    flyOver();
                }
                catch (NullPointerException e) {
                    // pass
                }
            }

            mFollowButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    flyOver();
                    mFollow = true;
                    mFollowButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.mapbox_blue)));
                }
            });

            mMap.setOnScrollListener(new MapboxMap.OnScrollListener() {
                @Override
                public void onScroll() {
                    mFollow = false;
                    mFollowButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.transparent)));
                }
            });

            mMap.setOnFlingListener(new MapboxMap.OnFlingListener() {
                @Override
                public void onFling() {
                    mFollow = false;
                    mFollowButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.transparent)));
                }
            });

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
                        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, MainActivity.this.getPackageName());
                        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, new Long(0));
                        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(1));
                        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(1));

                        mSpeechRecognizer.startListening(intent);
                    }
                }
            });

            mMapView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if(mHeldDown) {
                        if(motionEvent.getActionMasked() == motionEvent.ACTION_UP) {
                            mHeldDown = false;
                            mSpeechRecognizer.stopListening();
                            mViewSwitcher.showPrevious();
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }
        catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
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

    private void flyOver() {
        mHasFlown = false;

        mMapView.setCameraDistance(10);
        CameraPosition position = new CameraPosition.Builder()
                .target(mLatLang) // Sets the new camera position
                .zoom(15) // Sets the zoom
                .bearing(mCurrentDegree) // Rotate the camera
                .tilt(10) // Set the camera tilt
                .build(); // Creates a CameraPosition from the builder

        mMap.animateCamera(CameraUpdateFactory
                .newCameraPosition(position), 7000);

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        mHasFlown = true;
                    }
                },
                7100);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLatLang = new LatLng(location.getLatitude(), location.getLongitude());

        if(mFollow) {
            flyOver();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(mHasLoadedMap) {
            if (mSensorOrientation != null) {
                if (mGatherOrientationSensorData && mHasFlown && mFollow) {
                    mCurrentDegree = Math.round(event.values[0]);
                    CameraPosition current_position = mMap.getCameraPosition();

                    CameraPosition position = new CameraPosition.Builder()
                            .target(current_position.target) // Sets the new camera position
                            .zoom(current_position.zoom) // Sets the zoom
                            .bearing(mCurrentDegree) // Rotate the camera
                            .tilt(current_position.tilt) // Set the camera tilt
                            .build(); // Creates a CameraPosition from the builder

                    mMap.animateCamera(CameraUpdateFactory
                            .newCameraPosition(position), 7000);
                }
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

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        if(mHasLoadedMap && mSensorManager != null) {
            mSensorManager.registerListener(this, mSensorOrientation, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();

        if(mHasLoadedMap && mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }

        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();

        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    public static final int MY_PERMISSIONS_AUDIO = 98;
    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_AUDIO);
            }
            else {
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

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                    }

                }
                else {
                    Toast.makeText(this, "You will need permissions to use the voice feature.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i("com.flare", "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i("com.flare", "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i("com.flare", "onEndOfSpeech");
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.d("com.flare", "FAILED " + errorMessage);
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i("com.flare", "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i("com.flare", "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i("com.flare", "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i("com.flare", "onResults");
        ArrayList<String> result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        // TODO: Send message here!
        // TODO: Do some fancy schamcy stuff
        Date currentTime = Calendar.getInstance().getTime();
        mMarkerOptions = new MarkerOptions()
                .position(mLatLang)
                .snippet(""+currentTime)
                .title(result.get(0));
        mLastMarker = mMap.addMarker(mMarkerOptions);
        mLastMarker.showInfoWindow(mMap, mMapView);

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        mLastMarker.hideInfoWindow();
                    }
                },
                2100);
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i("com.flare", "onRmsChanged: " + rmsdB);
    }

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }
}