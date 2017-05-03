package com.clemgmelc.cyberrobotbrain.UI;

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.Data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import java.util.List;

public class MainActivity extends AppCompatActivity {


    //request code
    public static final int SCAN_DEVICE_REQUEST = 1;
    public String mDeviceAddress = null;
    public BluetoothLeService mBluetoothLeService;
    private MainActivity mainActivity;
    private FloatingActionButton mFab;
    private boolean mConnected = false;
    private boolean mReady = false;
    private Button mManualNav, mAutoNavigation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mainActivity = this;

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mManualNav = (Button) findViewById(R.id.manual_nav_btn);
        mAutoNavigation = (Button) findViewById(R.id.auto_navigation_btn);
        mManualNav.setEnabled(false);


        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.v(ConstantApp.TAG, "TAP on mFAB");
                if (!mConnected || mBluetoothLeService == null) {
                    Log.v(ConstantApp.TAG, "no connected--->reopen scan activity");
                    Intent launchScan = new Intent(MainActivity.this, DeviceScanActivity.class);
                    startActivityForResult(launchScan, SCAN_DEVICE_REQUEST);
                } else {
                    Log.v(ConstantApp.TAG, "INTENTIOLA REMOVAL OF CONNECTION");
                    //if (mBluetoothLeService != null)
                        mBluetoothLeService.disconnect();
                        mConnected = false;
                        mFab.setEnabled(false);
                    }
                }
        });


        mManualNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                List<BluetoothGattService> list = mBluetoothLeService.getSupportedGattServices();

                if (mConnected && list.size() == 9) {
                    Intent startManualNav = new Intent(MainActivity.this, ManualNavigation.class);
                    startManualNav.putExtra("DEVICE_ADDRESS", mDeviceAddress);
                    startActivity(startManualNav);
                } else if (!mConnected){
                    Toast.makeText(mainActivity, getResources().getString(R.string.action_disconnected), Toast.LENGTH_SHORT).show();
                } else if (list.size() != 9) {
                    Toast.makeText(mainActivity, "non ci sono service", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mAutoNavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startMan = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(startMan);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCAN_DEVICE_REQUEST) {

            if (resultCode == Activity.RESULT_OK) {

                String address = data.getStringExtra("DEVICE_ADDRESS");
                mDeviceAddress = address;
                mFab.setEnabled(false);
                //Toast.makeText(this, address, Toast.LENGTH_SHORT).show();

                registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

            }
        }
    }

    //manage connected, disconnected and discovered action
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

                //mConnected = true;

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {


                Log.v(ConstantApp.TAG, "sono scollegato ");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_disconnecting), Toast.LENGTH_SHORT).show();
                mManualNav.setEnabled(false);

                //bad thing to respect low timing
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mFab.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.ic_bluetooth_standard));
                                mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mainActivity, R.color.red)));
                                mFab.setEnabled(true);
                                mDeviceAddress = null;


                            }
                        });
                    }
                }, 2000);


                unregisterReceiver(mGattUpdateReceiver);
                unbindService(mServiceConnection);
                mBluetoothLeService = null;

                Log.v(ConstantApp.TAG, "unregistred");

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                //check if the connected device is an iBlio Device and write the characteristic

                Log.v(ConstantApp.TAG, "gatt discovered");
                mReady = true;

            }
        }
    };


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
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
            if (mBluetoothLeService.connect(mDeviceAddress)) {
                Log.v(ConstantApp.TAG, "Connected to: Cyber Robot");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_connecting), Toast.LENGTH_SHORT).show();

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mFab.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.ic_bluetooth_connected));
                                mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mainActivity, R.color.green)));
                                Toast.makeText(getApplicationContext(), getResources().getString(R.string.message_connected), Toast.LENGTH_SHORT).show();
                                mFab.setEnabled(true);
                                mManualNav.setEnabled(true);
                                mConnected = true;
                                //mReady = true;
                            }
                        });
                    }
                }, 2000);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    public void onBackPressed() {

        super.onBackPressed();

        if (mBluetoothLeService != null) {

            mBluetoothLeService.disconnect();
        }
    }

}