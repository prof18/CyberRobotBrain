package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;

import com.clemgmelc.cyberrobotbrain.R;

/**
 * Created by mattia on 25/04/17.
 */

public class Camera extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    protected void onCreate(Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            //tv.setText(R.string.perm_denied);
            requestCameraPermission();
        }
    }

    private void requestCameraPermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show dialog to the user explaining why the permission is required
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                //Log.i(TAG, "CAMERA permission has been DENIED.");
                // Handle lack of permission here
            }else{
                //Log.i(TAG, "CAMERA permission has been GRANTED.");
                // You can now access the camera
            }
        }else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

