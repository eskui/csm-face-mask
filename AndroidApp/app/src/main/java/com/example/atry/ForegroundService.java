package com.example.atry;

import android.Manifest;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ForegroundService extends Service {
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "MyBroadcastReceiver";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
       /* final Handler handler = new Handler();
        final Handler handler2 = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                takePhoto();
                handler2.postDelayed(new Runnable() {
                    public void run() {
                        takePhoto();
                    }
                }, 10000);
            }
        }, 10000);*/

        //do heavy work on a background thread

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                StringBuilder sb = new StringBuilder();
                sb.append("Action: " + intent.getAction() + "\n");
                sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                String log = sb.toString();
                Log.d(TAG, log);
                Log.d(TAG, "leeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeel");
                //Toast.makeText(context, log, Toast.LENGTH_LONG).show();
                takePhoto();
            }
        };

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        this.registerReceiver(br, filter);

        //stopSelf();

        return START_NOT_STICKY;
    }

    private void takePhoto() {

        System.out.println("Preparing to take photo");
        Camera camera = null;

        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            SystemClock.sleep(1000);

            Camera.getCameraInfo(camIdx, cameraInfo);
            if ( cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT  ) {
                try {
                    camera = Camera.open(camIdx);
                } catch (RuntimeException e) {
                    System.out.println("Camera not available: " + camIdx);
                    camera = null;
                    //e.printStackTrace();
                }
                try {
                    if (null == camera) {
                        System.out.println("Could not get camera instance");
                    } else {
                        System.out.println("Got the camera, creating the dummy surface texture");
                        SurfaceTexture dummySurfaceTextureF = new SurfaceTexture(camIdx);
                        try {
                            camera.setPreviewTexture(dummySurfaceTextureF);
                            camera.setPreviewTexture(new SurfaceTexture(camIdx));
                            camera.startPreview();
                        } catch (Exception e) {
                            System.out.println("Could not set the surface preview texture");
                            e.printStackTrace();
                        }
                        camera.takePicture(null, null, new Camera.PictureCallback() {

                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {

                                System.out.println("image saved");
                                File pictureFile = getOutputMediaFile();
                                if (pictureFile == null) {
                                    return;
                                }
                                try {
                                    FileOutputStream fos = new FileOutputStream(pictureFile);
                                    fos.write(data);
                                    fos.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                camera.release();
                            }
                        });
                    }
                } catch (Exception e) {
                    camera.release();
                }
            }

        }
    }

    private static File getOutputMediaFile() {
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "MaskApp");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MaskApp", "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "IMG_" + timeStamp + ".jpg");
        System.out.println(mediaFile);
        return mediaFile;
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}