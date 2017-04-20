package com.clemgmelc.cyberrobotbrain.UI;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.Data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

public class ManualNavigation extends AppCompatActivity {

    private ImageButton mForward, mBackward, mLeft, mRight;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothGattService mMovementGattService;
    private BluetoothGattCharacteristic mMovementCharacteristic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_navigation_activity_main);

        mDeviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");


        mForward = (ImageButton) findViewById(R.id.forward_button);
        mBackward = (ImageButton) findViewById(R.id.backward_button);
        mRight = (ImageButton) findViewById(R.id.right_button);
        mLeft = (ImageButton) findViewById(R.id.left_button);


        mForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
            }
        });

        mBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
            }
        });

        mLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
            }
        });

        mRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
            if (mDeviceAddress != null) {

                //if connected send a toast message
                if (mBluetoothLeService.connect(mDeviceAddress)) {
                    Log.v(ConstantApp.TAG, "Connected to: Cyber Robot from navigation");
                }

                mMovementGattService = mBluetoothLeService.getSupportedGattServices().get(8);
                mMovementCharacteristic = mMovementGattService.getCharacteristic(ConstantApp.UUID_MOVEMENT);
            } else {
                Toast.makeText(mBluetoothLeService, "Cyber Robot not connected. Please connect before trying to move it!", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                super.onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
