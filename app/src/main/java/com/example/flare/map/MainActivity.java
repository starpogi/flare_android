package com.example.flare.map;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.Manifest;
import android.location.Location;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapquest.mapping.maps.MapboxMap;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapquest.mapping.MapQuestAccountManager;
import com.mapquest.mapping.constants.Style;
import com.mapquest.mapping.maps.MapView;
import com.mapquest.mapping.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, SensorEventListener {

    private GoogleApiClient mGoogleApiClient;
    private MapboxMap mMap;
    private MapView mMapView;
    private MarkerOptions mMarkerOptions;
    private LatLng mLatLang = new LatLng(37.7749, -122.4194);
    private LocationRequest mLocationRequest;

    // Compass Sensors
    private SensorManager mSensorManager;
    private Sensor mSensorOrientation;
    private float mCurrentDegree = 180f;

    private boolean mIsDown = false;
    private boolean mGatherOrientationSensorData = true;

    private Icon mPositionIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                }
                else {
                    buildGoogleApiClient();
                }

                mMap.setOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng point) {
                        Log.d("com.example.flare", "Listening");
                        mIsDown = true;
                    }
                });

                mMap.setOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng point) {
                        if(mIsDown == true) {
                            Log.d("com.example.flare", "Released");
                            mIsDown = false;
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = createLocationRequest();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
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
            if(mGatherOrientationSensorData == true) {
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
                                        MY_PERMISSIONS_REQUEST_LOCATION );
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION );
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