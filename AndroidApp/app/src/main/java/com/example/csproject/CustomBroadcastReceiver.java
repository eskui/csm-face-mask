package com.example.csproject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CustomBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBroadcastReceiver";
    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action: " + intent.getAction() + "\n");
        sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
        String log = sb.toString();
        Log.d(TAG, log);
        Log.d(TAG, "leeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeel");
        Toast.makeText(context, log, Toast.LENGTH_LONG).show();
    }

}
