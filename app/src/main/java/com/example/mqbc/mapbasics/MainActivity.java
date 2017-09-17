package com.example.mqbc.mapbasics;

import com.example.mqbc.simplemap.R;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapquest.mapping.maps.MapboxMap;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapquest.mapping.MapQuestAccountManager;
import com.mapquest.mapping.constants.Style;
import com.mapquest.mapping.maps.MapView;
import com.mapquest.mapping.maps.OnMapReadyCallback;

public class MainActivity extends AppCompatActivity {
    private MapboxMap map;
    private MapView mapView;
    private final LatLng SAN_FRAN = new LatLng(37.7749, -122.4194);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapQuestAccountManager.start(getApplicationContext());
        mapView = (MapView) findViewById(R.id.mapquestMapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;

                // create and add marker
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(SAN_FRAN);
                markerOptions.title("San Francisco");
                markerOptions.snippet("Welcome to Frisco!");
                map.addMarker(markerOptions);

                // set map center and zoom
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(SAN_FRAN, 14));

                // turn on traffic
                map.setTrafficFlowLayerOn();
                map.setTrafficIncidentLayerOn();

                // set map style
                mapView.setStyleUrl(Style.MAPQUEST_SATELLITE);
            }
        });
    }

    @Override
    public void onResume()
    { super.onResume(); mapView.onResume(); }

    @Override
    public void onPause()
    { super.onPause(); mapView.onPause(); }

    @Override
    protected void onDestroy()
    { super.onDestroy(); mapView.onDestroy(); }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
}