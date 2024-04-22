package com.example.geofence;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    //Google Map api variables
    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    //debug
    private String TAG = "MapAct";
    //Required permissions array
    final String[] PERMISSIONS_29P = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION};
    final String[] PERMISSIONS_29M = {Manifest.permission.ACCESS_FINE_LOCATION};
    //Latrobe Lat & Lang
    private Double myLat = -37.722358; //Default to latrobe
    private Double myLong = 145.049592;
    //Fused location provider
    private FusedLocationProviderClient fusedLocationClient;
    //Geofence variables
    private final static int GEOFENCE_RADIUS_IN_METERS = 100; //(Note: Android does not recommend using a smaller radius than 100 meters as it cannot guarantee the accuracy.)
    private final static long GEOFENCE_EXPIRATION_IN_MILLISECONDS = Geofence.NEVER_EXPIRE;
    private PendingIntent geofencePendingIntent;
    //UI
    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // geofence client, to access geofence API
        geofencingClient = LocationServices.getGeofencingClient(this);

        //fused location client, to get your location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        button = findViewById(R.id.myButton);
        //hide button until map is ready (and geofence set up)
        button.setVisibility(View.INVISIBLE);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                removeGeofence();
            }
        });
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        Log.d(TAG, "Map ready");
        //once map is ready get user location to set marker & geofence
        initUserLocation();
        //make the stop geofences button visable
        button.setVisibility(View.VISIBLE);
    }

    private void initUserLocation() {
        //If else has been included as requesting access background location in SDK < Q can cause errors but is required for >= API level 29

            //get the initial location
            if (hasPermissions(PERMISSIONS_29M)) {
                Log.d(TAG, "Build < Q, App has required permissions, getting last location and creating location request");
                getLastLocation(); //get start location
            } else {
                Log.d(TAG, "App does not have required permissions, asking now");
                askPermissions(PERMISSIONS_29M);
            }
    }


    //helper function to check permission status
    private boolean hasPermissions(String[] perms) {
        boolean permissionStatus = true;
        for (String permission : perms) {
            if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission is granted: " + permission);
            } else {
                Log.d(TAG, "Permission is not granted: " + permission);
                permissionStatus = false;
            }
        }
        return permissionStatus;
    }

    //helper function to ask user permissions
    private void askPermissions(String[] perms) {
        if (!hasPermissions(perms)) {
            Log.d(TAG, "Launching multiple contract permission launcher for ALL required permissions");
            multiplePermissionActivityResultLauncher.launch(perms);
        } else {
            Log.d(TAG, "All permissions are already granted");
        }
    }

    //Result launcher for permissions
    private final ActivityResultLauncher<String[]> multiplePermissionActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                Log.d(TAG, "Launcher result: " + isGranted.toString());
                //permissions are granted lets get to work!
                getLastLocation(); //get start location
                if (isGranted.containsValue(false)) {
                    Log.d(TAG, "At least one of the permissions was not granted, please enable permissions to ensure app functionality");
                }
            });


    //get last location to use as centre of geofence
    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            myLat = location.getLatitude();
                            myLong = location.getLongitude();
                            Log.d(TAG, "Lat: " + myLat + " Long: " + myLong);
                            // Add a marker to users Location
                            LatLng home = new LatLng(myLat, myLong);
                            mMap.addMarker(new MarkerOptions().position(home).title("Marker At Home"));
                            float zoomLevel = 16.0f; //This goes up to 21
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(home, zoomLevel));
                            //add geofence around the home location
                            addGeofence();
                        } else {
                            Toast.makeText(getApplicationContext(), "no_location_detected, unable to establish geofence", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void addGeofence() {
        //first we build geofence object
        Geofence geofence = new Geofence.Builder()
                .setRequestId("My house")
                .setCircularRegion(myLat, myLong, GEOFENCE_RADIUS_IN_METERS)
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT |
                        Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(1)
                .build();

        //get the geofencing request & pending intent
        GeofencingRequest geofencingRequest = getGeofencingRequest(geofence);
        geofencePendingIntent = getGeofencePendingIntent();

        //use request & intent to client to add geofence
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "onSuccess: Geofence Added...");
                        LatLng home = new LatLng(myLat, myLong);
                        addCircle(home);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        String errorMessage = getErrorString(e);
                        Log.d(TAG, "onFailure: " + errorMessage);
                    }
                });
    }

    public GeofencingRequest getGeofencingRequest(Geofence geofence) {
        return new GeofencingRequest.Builder()
                .addGeofence(geofence)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);

        geofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return geofencePendingIntent;
    }

    // Get Geofence error messages (more detail)
    public String getErrorString(Exception e) {
        if (e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            switch (apiException.getStatusCode()) {
                case GeofenceStatusCodes
                        .GEOFENCE_NOT_AVAILABLE:
                    return "GEOFENCE_NOT_AVAILABLE";
                case GeofenceStatusCodes
                        .GEOFENCE_TOO_MANY_GEOFENCES:
                    return "GEOFENCE_TOO_MANY_GEOFENCES";
                case GeofenceStatusCodes
                        .GEOFENCE_TOO_MANY_PENDING_INTENTS:
                    return "GEOFENCE_TOO_MANY_PENDING_INTENTS";
            }
        }
        return e.getLocalizedMessage();
    }

    //add Circle to map that covers the area of the geofence
    private void addCircle(LatLng latLng) {
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius((float) MapsActivity.GEOFENCE_RADIUS_IN_METERS);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }

    private void removeGeofence()
    {
        //remove geofences
        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Geofences removed
                        Toast.makeText(getApplicationContext(), "Geofence removed", Toast.LENGTH_SHORT).show();
                        button.setEnabled(false);
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Failed to remove geofences
                        Log.d(TAG, "Failed to remove geofences: " + e.toString());
                    }
                });
    }

    // I have included this to remove the geofence when activity is destroyed, if you want the app
    // to run in the background you may want to remove this code as it is available via the button
    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeGeofence();
    }
}