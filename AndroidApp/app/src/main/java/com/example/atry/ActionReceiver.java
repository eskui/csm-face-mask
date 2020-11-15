package com.example.atry;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Toast.makeText(context, "recieved", Toast.LENGTH_SHORT).show();
        String action = intent.getStringExtra("action");
        if (action.equals("nothere")) {
            Double latitude =  intent.getDoubleExtra("latitude",0.5);
            Double longitude =  intent.getDoubleExtra("longitude",0.5);
            System.out.println(latitude);
            System.out.println(longitude);
            addNoGPSList(latitude,longitude);
        } else if (action.equals("ignore")) {
            System.out.println("ignore");
        }
        //This is used to close the notification tray
        Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(it);
    }

    public void addNoGPSList(double latitude, double longitude) {
        //TODO save location for later
    }


}

