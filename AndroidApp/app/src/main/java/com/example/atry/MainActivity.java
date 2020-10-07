package com.example.atry;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.atry.ForegroundService;
import com.example.atry.R;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    Button btnStartService, btnStopService, btnClassify;
    TextView resultText;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnStartService = findViewById(R.id.buttonStartService);
        btnStopService = findViewById(R.id.buttonStopService);
        btnClassify = findViewById(R.id.buttonClassify);
        resultText = findViewById(R.id.resultText);

        btnStartService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService();
            }
        });
        btnStopService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService();
            }
        });

        btnClassify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String result=classify("/storage/emulated/0/Pictures/MaskApp/IMG_20201007_133200.jpg");
                resultText.setText(result);
            }
        });

        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 3);

    }
    Bitmap bitmap = null;
    Module module = null;
    String classify(String path){
        try {

            bitmap = BitmapFactory.decodeStream(getAssets().open("image.jpg"));
           // module = Module.load(assetFilePath(this, "mobilenet-v2.pt"));
            module = Module.load(assetFilePath(this, "model.pt"));
            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

            // running the model
            final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
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
            return className;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error";
        }

    }

    public void startService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public static String assetFilePath(Context context, String assetName) {
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
        } catch (IOException e) {
            Log.e("lel", "Error process asset " + assetName + " to file path");
        }
        return null;
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        stopService(serviceIntent);
    }
}