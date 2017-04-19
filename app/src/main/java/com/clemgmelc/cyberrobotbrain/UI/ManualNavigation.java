package com.clemgmelc.cyberrobotbrain.UI;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import com.clemgmelc.cyberrobotbrain.Data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

public class ManualNavigation extends AppCompatActivity {

    private ImageButton mForward;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_navigation);
        mDeviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");


        mForward = (ImageButton) findViewById(R.id.forward_button);
        mForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                BluetoothGattService mSVC = mBluetoothLeService.getSupportedGattServices().get(8);
                BluetoothGattCharacteristic mCH = mSVC.getCharacteristic(ConstantApp.UUID_MOVEMENT);


                mBluetoothLeService.writeCharacteristic(mCH, null);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    public void onPause() {

        super.onPause();


    }

    // Manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        //what to do when connected to the service
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e("", "Unable to initialize Bluetooth");
                finish();
            }

            //if connected send a toast message
            if(mBluetoothLeService.connect(mDeviceAddress)) {
                Log.v(ConstantApp.TAG, "Connected to: Cyber Robot from navigation");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
}
