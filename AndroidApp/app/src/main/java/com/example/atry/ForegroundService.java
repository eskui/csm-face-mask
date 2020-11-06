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
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
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

import java.lang.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static java.util.Collections.max;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "MyBroadcastReceiver";
    private Module face_model, mask_model;
    Context context = this;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            face_model = Module.load(assetFilePath(context, "face_model_mobile.pt"));
            mask_model = Module.load(assetFilePath(context, "mask_model_mobile.pt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
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
                        //bitmap= rotateImage(bitmap,270);
                        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
                        camera.release();

                        try {
                            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resized,
                                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

                            final Tensor faceOutputTensor = face_model.forward(IValue.from(inputTensor)).toTensor();
                            final float[] faceScores = faceOutputTensor.getDataAsFloatArray();

                            // SAVE FILE FOR DEBUGGING PURPOSES
                            File pictureFile = getOutputMediaFile("FACE-MODEL", faceScores);
                            if (pictureFile == null) {
                                return;
                            }
                            try {
                                FileOutputStream fos = new FileOutputStream(pictureFile);
                                resized.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            double faceScore1 = Math.exp(faceScores[0]);
                            double faceScore2 = Math.exp(faceScores[1]);

                            boolean hasFace = faceScore1 > faceScore2;
                            if (!hasFace) {
                                notifyClassificationResult("NO FACE", faceScores);
                                Log.d(TAG, "NO FACE: " + faceScore1 + " / " + faceScore2);
                                return;
                            }
                            Log.d(TAG, "GOT FACE: " + faceScore1 + " / " + faceScore2);

                            final Tensor maskOutputTensor = mask_model.forward(IValue.from(inputTensor)).toTensor();
                            final float[] maskScores = maskOutputTensor.getDataAsFloatArray();
                            double maskScore1 = Math.exp(maskScores[0]);
                            double maskScore2 = Math.exp(maskScores[1]);
                            boolean hasMask = maskScore1  > maskScore2;
                            if (hasMask) {
                                Log.d(TAG, "GOT MASK: " + maskScore1 + " / " + maskScore2);
                                notifyClassificationResult("MASK", maskScores);
                            } else {
                                Log.d(TAG, "NO MASK: " + maskScore1 + " / " + maskScore2);
                                notifyClassificationResult("NO MASK", maskScores);
                            }

                            // SAVE FILE FOR DEBUGGING PURPOSES
                            pictureFile = getOutputMediaFile("MASK-MODEL", maskScores);
                            if (pictureFile == null) {
                                return;
                            }
                            try {
                                FileOutputStream fos = new FileOutputStream(pictureFile);
                                resized.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    private static File getOutputMediaFile(String type, float[] scores) {
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
        String fileName = type + "-" + Math.exp(scores[0]) + "-" + Math.exp(scores[1])+".jpg";
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return mediaFile;
    }

    private void notifyClassificationResult(String className, float[] scores) {
        Log.d(TAG,"Going to send a notifcation.");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification n = new Notification.Builder(this)
                    .setContentTitle("Photo taken! Result: " + className)
                    //.setContentText("The detected class is: " + className)
                    .setContentText("Result: " + className + ", scores: " + Math.exp(scores[0]) + " / " + Math.exp(scores[1]))
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