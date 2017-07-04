package com.clemgmelc.cyberrobotbrain.ui;

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import com.clemgmelc.cyberrobotbrain.data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;

import java.util.List;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();
    //request code
    public static final int SCAN_DEVICE_REQUEST = 1;
    public String mDeviceAddress = null;
    public BluetoothLeService mBluetoothLeService;
    private MainActivity mainActivity;
    private FloatingActionButton mFab;
    private boolean mConnected = false;
    private Button mManualNav, mAutoNavigation;

    //With this boolean at true, the buttons are enabled even if the robot isn't connected
    private boolean isDebug = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_main);
        mainActivity = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mManualNav = (Button) findViewById(R.id.manual_nav_btn);
        mAutoNavigation = (Button) findViewById(R.id.auto_navigation_btn);

        mManualNav.setEnabled(false);
        mAutoNavigation.setEnabled(false);

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.v(TAG, "****************************************************TAP on mFAB***********************");
                mFab.setEnabled(false);
                if (!mConnected || mBluetoothLeService == null) {
                    Log.v(TAG, "no connected--->reopen scan activity");
                    Intent launchScan = new Intent(MainActivity.this, DeviceScanActivity.class);
                    startActivityForResult(launchScan, SCAN_DEVICE_REQUEST);
                } else {
                    Log.v(TAG, "INTENTIONAL REMOVAL OF CONNECTION");
                    mBluetoothLeService.disconnect();
                    mConnected = false;
                    mFab.setEnabled(false);
                }
            }
        });


        mManualNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mManualNav.setClickable(false);
                if (!isDebug) {
                    List<BluetoothGattService> list = mBluetoothLeService.getSupportedGattServices();
                    if (mConnected) {
                        Intent startManualNav = new Intent(MainActivity.this, ManualNavigationActivity.class);
                        startManualNav.putExtra(ConstantApp.DEVICE_ADDRESS, mDeviceAddress);
                        startActivity(startManualNav);
                    } else if (!mConnected) {
                        Toast.makeText(mainActivity, getResources().getString(R.string.action_disconnected), Toast.LENGTH_SHORT).show();
                    } else if (list.size() != 9) {
                        Toast.makeText(mainActivity, getResources().getString(R.string.error_occured), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Intent startManualNav = new Intent(MainActivity.this, ManualNavigationActivity.class);
                    startActivity(startManualNav);
                }
            }
        });

        mAutoNavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mAutoNavigation.setClickable(false);

                if (!isDebug) {

                    List<BluetoothGattService> list = mBluetoothLeService.getSupportedGattServices();

                    if (mConnected) {
                        Intent startMan = new Intent(MainActivity.this, AutoNavigationActivity.class);
                        startMan.putExtra(ConstantApp.DEVICE_ADDRESS, mDeviceAddress);
                        startActivity(startMan);
                    } else if (!mConnected) {
                        Toast.makeText(mainActivity, getResources().getString(R.string.action_disconnected), Toast.LENGTH_SHORT).show();
                    } else if (list.size() != 9) {
                        Toast.makeText(mainActivity, getResources().getString(R.string.error_occured), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Intent startMan = new Intent(MainActivity.this, AutoNavigationActivity.class);
                    startActivity(startMan);
                }
            }
        });

        if (isDebug) {
            mAutoNavigation.setEnabled(true);
            mManualNav.setEnabled(true);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCAN_DEVICE_REQUEST) {

            if (resultCode == Activity.RESULT_OK) {

                //If the scan has find the Cyber Robot, get the address from the scan activity,
                //bind the service and connect to the robot
                mDeviceAddress = data.getStringExtra(ConstantApp.DEVICE_ADDRESS);
                mFab.setEnabled(false);

                registerReceiver(mGattUpdateReceiver, ConstantApp.gattUpdateIntentFilter());

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

            if (ConstantApp.ACTION_GATT_CONNECTED.equals(action)) {

                Log.v(TAG, "Robot Connected");

            } else if (ConstantApp.ACTION_GATT_DISCONNECTED.equals(action)) {

                Log.v(TAG, "Robot Disconnected");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_disconnecting), Toast.LENGTH_SHORT).show();
                mManualNav.setEnabled(false);
                mAutoNavigation.setEnabled(false);

                //We change the UI after a few second to respect the timing or unexpected delay during the disconnection
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mFab.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.ic_bluetooth_standard));
                                mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mainActivity, R.color.googleRed)));
                                mFab.setEnabled(true);
                                mDeviceAddress = null;
                            }
                        });
                    }
                }, 2000);

                unregisterReceiver(mGattUpdateReceiver);
                unbindService(mServiceConnection);
                mBluetoothLeService = null;
                Log.v(TAG, "Unregistred");

            } else if (ConstantApp.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                Log.v(TAG, "gatt discovered");
                mManualNav.setEnabled(true);
                mAutoNavigation.setEnabled(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mFab.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.ic_bluetooth_connected));
                        mFab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mainActivity, R.color.green)));
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.message_connected), Toast.LENGTH_SHORT).show();
                        mFab.setEnabled(true);
                        //For a better UX, we consider the robot connected only when all the GATT Services are discovered.
                        //For a user when a device is connected has to be immediately ready to its job
                        mConnected = true;
                    }
                });
            }
        }
    };

    // Manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        //what to do when connected to the service
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.v("", "Unable to initialize Bluetooth");
                finish();
            }

            //if connected send a toast message
            if (mBluetoothLeService.connect(mDeviceAddress)) {
                Log.v(TAG, "Connected to: Cyber Robot");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_connecting), Toast.LENGTH_SHORT).show();
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

    @Override
    public void onResume() {
        super.onResume();
        mFab.setEnabled(true);
        mAutoNavigation.setClickable(true);
        mManualNav.setClickable(true);
    }
}