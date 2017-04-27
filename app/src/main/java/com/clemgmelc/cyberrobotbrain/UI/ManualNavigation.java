package com.clemgmelc.cyberrobotbrain.UI;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
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
    private boolean pressed = true;
    private Handler mHandler;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_navigation_activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDeviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");


        mForward = (ImageButton) findViewById(R.id.forward_button);
        mBackward = (ImageButton) findViewById(R.id.backward_button);
        mRight = (ImageButton) findViewById(R.id.right_button);
        mLeft = (ImageButton) findViewById(R.id.left_button);

       mForward.setOnTouchListener(new View.OnTouchListener() {
           @Override
           public boolean onTouch(View v, MotionEvent event) {

               if(event.getAction() == MotionEvent.ACTION_DOWN ) {
                   if (mHandler != null) {
                       return true;
                   }
                   mHandler = new Handler();
                   mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                   mHandler.postDelayed(mAction, 200);
                   return false;
               } else if(event.getAction() == MotionEvent.ACTION_UP) {
                   if (mHandler == null) {
                       return true;
                   }
                   mHandler.removeCallbacks(mAction);
                   mHandler = null;
                   return false;
               }
               return false;
           }

           Runnable mAction = new Runnable() {
               @Override public void run() {

                   mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                   mHandler.postDelayed(this, 200);
               }
           };

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

                mMovementGattService = mBluetoothLeService.getSupportedGattServices().get(mBluetoothLeService.getSupportedGattServices().size() - 1);
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
