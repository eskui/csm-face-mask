package com.example.atry;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;

import static android.support.constraint.ConstraintLayoutStates.TAG;

public class ActionReceiver extends BroadcastReceiver implements LocationListener{

    @Override
    public void onReceive(Context context, Intent intent) {
        
        Toast.makeText(context, "recieved", Toast.LENGTH_SHORT).show();
        String action = intent.getStringExtra("action");
        if (action.equals("action1")) {
            doGPS();
        } else if (action.equals("action2")) {

            System.out.println("skip");
        }
        //This is used to close the notification tray
        Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(it);
    }

    protected LocationManager locationManager;
    protected LocationListener locationListener;
    protected Context context;

    String lat;
    String provider;
    protected String latitude,longitude;
    protected boolean gps_enabled,network_enabled;

    public void doGPS() {
        System.out.println("doing gps");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);

    }
    public void onLocationChanged(Location location) {

       System.out.println(location.getLatitude() );
        System.out.println(location.getLongitude() );
    }
    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Latitude","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Latitude","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Latitude","status");
    }

}

