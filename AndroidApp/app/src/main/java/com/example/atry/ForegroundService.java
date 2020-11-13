package com.example.atry;
import com.example.atry.ActionReceiver;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = "MaskApp";
    private Module face_model, mask_model;
    private Context context = this;

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
                        camera.release();

                        try {
                            boolean hasFace = detectFace(bitmap);

                            // nothing to do, no face in the image
                            if (!hasFace) {
                                return;
                            }

                            boolean hasMask = detectMask(bitmap);
                            if (!hasMask) {
                                noitfyUserNotWearingMask();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    private Bitmap prepareImage(Bitmap bitmap) {
        // front camera takes pictures sideways, so flip it
        bitmap = rotateImage(bitmap,270);

        // resize to 224x224 for the model to use
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        return resized;
    }

    private boolean detectMask(Bitmap bitmap) {
        Bitmap image = prepareImage(bitmap);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(image,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        final Tensor outputTensor = mask_model.forward(IValue.from(inputTensor)).toTensor();
        final float[] scores = outputTensor.getDataAsFloatArray();

        double score1 = Math.exp(scores[0]);
        double score2 = Math.exp(scores[1]);

        boolean hasMask = score1 > score2;

        if (hasMask) {
            Log.d(TAG, "DETECTED MASK, SCORES: " + score1 + " / " + score2);

            if (BuildConfig.DEBUG) {
                notifyClassificationResult("DETECTED MASK, SCORES: ", scores);
            }

        } else {
            Log.d(TAG, "DETECTED -NO- MASK, SCORES: " + score1 + " / " + score2);

            if (BuildConfig.DEBUG) {
                notifyClassificationResult("DETECTED -NO- MASK, SCORES: ", scores);
            }
        }

        if (BuildConfig.DEBUG) {
            saveImage(image, "MASK-MODEL", scores);
        }

        return hasMask;
    }

    private boolean detectFace(Bitmap bitmap) {
        Bitmap image = prepareImage(bitmap);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(image,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        final Tensor outputTensor = face_model.forward(IValue.from(inputTensor)).toTensor();
        final float[] scores = outputTensor.getDataAsFloatArray();

        double score1 = Math.exp(scores[0]);
        double score2 = Math.exp(scores[1]);

        boolean hasFace = score1 > score2;

        if (hasFace) {
            Log.d(TAG, "DETECTED FACE, SCORES: " + score1 + " / " + score2);
            if (BuildConfig.DEBUG) {
                notifyClassificationResult("DETECTED FACE, SCORES: ", scores);
            }
        } else {
            Log.d(TAG, "DETECTED -NO- FACE, SCORES: " + score1 + " / " + score2);

            if (BuildConfig.DEBUG) {
                notifyClassificationResult("DETECTED -NO- FACE, SCORES: ", scores);
            }
        }

        if (BuildConfig.DEBUG) {
            saveImage(image, "FACE-MODEL", scores);
        }
        return hasFace;
    }

    private void saveImage(Bitmap image, String modelString, float[] scores) {

        if (!BuildConfig.DEBUG) {
            return;
        }

        File pictureFile = getOutputMediaFile(modelString, scores);
        if (pictureFile == null) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private static File getOutputMediaFile(String type, float[] scores) {
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MaskApp");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory for stroring debug images.");
                return null;
            }
        }
        String fileName = type + "-" + Math.exp(scores[0]) + "-" + Math.exp(scores[1]) + ".jpg";
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return mediaFile;
    }

    private void noitfyUserNotWearingMask() {
        String title = getResources().getString(R.string.no_mask_notification_title);
        String notification = getResources().getString(R.string.no_mask_notification_msg);
        notify(title, notification);
    }

    private void notifyClassificationResult(String className, float[] scores) {
        String title = "Photo taken! Result: " + className;
        String notification = "Result: " + className + ", scores: " + Math.exp(scores[0]) + " / " + Math.exp(scores[1]);
        notify(title, notification);
    }

    private void notify(String titleText, String notificationText) {
        Log.d(TAG,"Going to send a notification.");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Intent intentAction = new Intent(context, ActionReceiver.class);

        //This is optional if you have more than one buttons and want to differentiate between two
        intentAction.putExtra("action","action2");
        PendingIntent pIntentlogin;
        pIntentlogin = PendingIntent.getBroadcast(context,1,intentAction,PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentAction2 = new Intent(context, ActionReceiver.class);
        intentAction2.putExtra("action","action1");
        PendingIntent pIntentlogin2;
        pIntentlogin2 = PendingIntent.getBroadcast(context,2,intentAction2,PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification n = new Notification.Builder(this)
                    .setContentTitle(titleText)
                    .setContentText(notificationText)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setChannelId(CHANNEL_ID)
                    .addAction(R.drawable.ic_launcher_foreground, "ok",pIntentlogin)
                    .addAction(R.drawable.ic_launcher_foreground, "not here",pIntentlogin2)
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