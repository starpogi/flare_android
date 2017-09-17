package com.example.flare.map;

import android.Manifest;
import android.os.Bundle;
import android.os.Build;
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
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.Locale;

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
import com.mapbox.mapboxsdk.MapboxAccountManager;

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
    private boolean mHasLoadedMap = false;
    private boolean mGatherOrientationSensorData = true;

    // Speech to Text
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private boolean mHeldDown = false;
    private Icon mPositionIcon;
    private ViewSwitcher mViewSwitcher;

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

        mPositionIcon = IconFactory.getInstance(MainActivity.this).fromResource(R.drawable.default_marker);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

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
                            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, new Long(0));
                            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(1));
                            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(1));

                            try {
                                startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
                            } catch (ActivityNotFoundException a) {
                                Toast.makeText(getApplicationContext(),
                                        getString(R.string.speech_not_supported),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

                mMapView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        if(mHeldDown) {
                            if(motionEvent.getActionMasked() == motionEvent.ACTION_UP) {
                                mHeldDown = false;
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

                    // TODO: Send message here!
                    // TODO: Do some fancy schamcy stuff
                    mMarkerOptions = new MarkerOptions()
                            .position(mLatLang)
                            .title(result.get(0));
                    Marker marker = mMap.addMarker(mMarkerOptions);
                    marker.showInfoWindow(mMap, mMapView);

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
        mHasLoadedMap = true;
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
        if(mHasLoadedMap) {
            if (mSensorOrientation != null) {
                if (mGatherOrientationSensorData) {
                    mCurrentDegree = Math.round(event.values[0]);
                    updateCamera();
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
        }
    }

}