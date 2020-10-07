package com.example.atry;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "MyBroadcastReceiver";
    Context context = this;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() called.");
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(input)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        // create a broadcast receiver that takes a photo when called
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG,"Going to take a photo.");
                try {
                    takePhoto();
                } catch (Exception e) {
                    Log.e(TAG,"Taking a photo failed.");
                    e.printStackTrace();
                }
            }
        };

        // what kind of Intents are we interested in?
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);

        // register our broadcastreceiver to get the messages from the intents
        // that we are interested in
        this.registerReceiver(br, filter);

        // TODO: what is this return code?
        return START_NOT_STICKY;
    }

    private void takePhoto() throws Exception {

        System.out.println("Preparing to take photo");
        Camera camera = null;

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); camIdx++) {

            // TODO: why sleep here?
            SystemClock.sleep(1000);

            Camera.getCameraInfo(camIdx, cameraInfo);

            // is the camera facing front?
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    camera = Camera.open(camIdx);
                } catch (RuntimeException e) {
                    Log.e(TAG,"Could not get the camera!");
                    Log.e(TAG, e.toString());
                    throw e;
                }

                Log.d(TAG, "Got the camera, creating the dummy surface texture.");

                // get a surface texture to prevent the user from seeing the image
                SurfaceTexture dummySurfaceTextureF = new SurfaceTexture(camIdx);
                try {
                    camera.setPreviewTexture(dummySurfaceTextureF);
                    camera.setPreviewTexture(new SurfaceTexture(camIdx));
                    camera.startPreview();
                } catch (Exception e) {
                    Log.e(TAG,"Could not set the surface preview texture.");
                    Log.e(TAG, e.toString());
                    throw e;
                }

                camera.takePicture(null, null, new Camera.PictureCallback() {

                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {

                        Log.d(TAG, "Starting to handle new picture.");
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        camera.release();

                        //Module model = Module.load("file:///android_asset/model.pt");
                        try {
                            Module model = Module.load(assetFilePath(context, "model.pt"));
                            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

                            final Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
                            final float[] scores = outputTensor.getDataAsFloatArray();
                            float maxScore = -Float.MAX_VALUE;
                            int maxScoreIdx = -1;
                            for (int i = 0; i < scores.length; i++) {
                                if (scores[i] > maxScore) {
                                    maxScore = scores[i];
                                    maxScoreIdx = i;
                                }
                            }

                            String className = ImageNetClasses.IMAGENET_CLASSES[maxScoreIdx];
                            Log.d(TAG, "Picture taken!");
                            Log.d(TAG,"Class is: " + className);
                            notifyClassificationResult(className);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    private void notifyClassificationResult(String className) {
        Log.d(TAG,"Going to send a notifcation.");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification n  = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            n = new Notification.Builder(this)
                    .setContentTitle("Photo taken!")
                    .setContentText("The detected class is: " + className)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setChannelId(CHANNEL_ID)
                    //.setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_MAX)
                    .build();

            Log.d(TAG,"Really, really going to send the notification.");

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(42, n);

            Log.d(TAG,"Notification sent!");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}