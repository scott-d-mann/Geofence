package com.example.geofence;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

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

import java.util.Arrays;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    private String TAG = "MapAct";
    private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 345;
    private Double myLat = -37.722358; //Default to latrobe
    private Double myLong = 145.049592;
    private FusedLocationProviderClient fusedLocationClient;
    private final static int GEOFENCE_RADIUS_IN_METERS = 100; //(Note: Android does not recommend using a smaller radius than 100 meters as it cannot guarantee the accuracy.)
    private final static long GEOFENCE_EXPIRATION_IN_MILLISECONDS = Geofence.NEVER_EXPIRE;
    private PendingIntent geofencePendingIntent;

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
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //once map is ready get user location to set marker & geofence
        initUserLocation();
    }

    private void initUserLocation() {
        String[] Permissions = new String[0];
        //If else has been included as requesting access background location in SDK < Q can cause errors but is required for >= API level 29
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        {
            Permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION, };
        }
        else
        {
            Permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,};
        }
        if(!hasPermissions(this, Permissions)){
            ActivityCompat.requestPermissions(this, Permissions, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        //No permissions need to be requested continue app function
        Log.d(TAG, "Permissions passed setting location");
        getLastLocation();

    }

    // Get results from permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (grantResults.length == 2) //if your app targets Android 10 (API level 29) or higher
            {
                boolean fineLocationPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean bgLocationPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, permissions[0] + " " + fineLocationPermission);
                Log.d(TAG, permissions[1] + " " + bgLocationPermission);
                if (fineLocationPermission && bgLocationPermission) {
                    Toast.makeText(this, "Permission Granted for both background & fine location", Toast.LENGTH_LONG).show();
                    getLastLocation();
                } else {
                    Toast.makeText(this, "Permission Denied for required permissions", Toast.LENGTH_LONG).show();
                }
            }
            else if(grantResults.length == 1) // if your app targets < API 29
            {
                boolean fineLocationPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, permissions[0] + " " + fineLocationPermission);
                if (fineLocationPermission) {
                    Toast.makeText(this, "Permission Granted for fine location", Toast.LENGTH_LONG).show();
                    getLastLocation();
                } else {
                    Toast.makeText(this, "Permission Denied for fine location", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    //helper to check if permissions are all granted
    public boolean hasPermissions(Context context, String... permissions){
        boolean passed = true;
        if(context != null && permissions != null){
            for(String permission: permissions){
                if(ActivityCompat.checkSelfPermission(context, permission)!=PackageManager.PERMISSION_GRANTED){
                    Log.d(TAG, "has permission check failed for:" + permission);
                    passed = false;
                }
            }
        }
        Log.d(TAG, "has permission check passed for:" + Arrays.toString(permissions));
        return passed;
    }

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
    private void addGeofence()
    {
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
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        geofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
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
        circleOptions.strokeColor(Color.argb(255, 255, 0,0));
        circleOptions.fillColor(Color.argb(64, 255, 0,0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }

    // I have inc luded this to remove the geofence when activity is destroyed, if you want the app
    // to run in the background you may want to move this code to a button or other UI element
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Toast.makeText(getApplicationContext(), "Geofence removed", Toast.LENGTH_SHORT).show();
        //remove geofences
        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Geofences removed
                        Toast.makeText(getApplicationContext(), "Geofence removed", Toast.LENGTH_SHORT).show();
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
}