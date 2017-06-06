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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.Data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

public class ManualNavigation extends AppCompatActivity {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigation.class.getSimpleName();
    private ImageButton mForward, mBackward, mLeft, mRight;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothGattService mMovementGattService;
    private BluetoothGattCharacteristic mMovementCharacteristic;
    private boolean pressed = true;

    private Handler mHandlerF;
    private Handler mHandlerB;
    private Handler mHandlerL;
    private Handler mHandlerR;

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

        //add listeners
        mForward.setOnTouchListener(movementListener(ConstantApp.CODE_FORWARD));
        mBackward.setOnTouchListener(movementListener(ConstantApp.CODE_BACKWARD));
        mLeft.setOnTouchListener(movementListener(ConstantApp.CODE_LEFT));
        mRight.setOnTouchListener(movementListener(ConstantApp.CODE_RIGHT));
    }


    @Override
    public void onResume() {
        super.onResume();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        //mBluetoothLeService.disconnect();
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

             /*   //if connected send a toast message
                if (mBluetoothLeService.connect(mDeviceAddress)) {
                    Log.v(TAG, "Connected to: Cyber Robot from navigation");
                }*/


                mMovementGattService = mBluetoothLeService.getSupportedGattServices().get(mBluetoothLeService.getSupportedGattServices().size() - 1);
                mMovementCharacteristic = mMovementGattService.getCharacteristic(ConstantApp.UUID_MOVEMENT);

            } else {
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.action_disconnected), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.v(TAG, "onServiceDisconnected");

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

    private View.OnTouchListener movementListener(int movement) {

        View.OnTouchListener listener = null;

        switch (movement) {

            case ConstantApp.CODE_FORWARD: {

                listener = new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerF != null) {
                                return true;
                            }

                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerF = new Handler();
                                Log.d(TAG, "FORWARD1");
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(mActionF, 200);
                            }
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerF == null) {
                                return true;
                            }
                            mHandlerF.removeCallbacks(mActionF);
                            mHandlerF = null;
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            if (mHandlerF != null) {
                                return true;
                            }

                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerF = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(mActionF, 200);
                            }
                            return false;
                        }

                        return false;
                    }

                    Runnable mActionF = new Runnable() {
                        @Override
                        public void run() {
                            if (mHandlerF == null)
                                mHandlerF = new Handler();
                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                Log.d(TAG, "FORWARD2");
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(this, 200);
                            }
                        }
                    };

                };
                break;
            }

            case ConstantApp.CODE_BACKWARD: {

                listener = new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerB != null) {
                                return true;
                            }

                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(mActionB, 200);
                            }
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerB == null) {
                                return true;
                            }
                            mHandlerB.removeCallbacks(mActionB);
                            mHandlerB = null;
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerB != null) {
                                return true;
                            }
                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(mActionB, 200);
                            }
                            return false;
                        }

                        return false;
                    }

                    Runnable mActionB = new Runnable() {
                        @Override
                        public void run() {
                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                if (mHandlerB == null)
                                    mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(this, 200);
                            }
                        }
                    };

                };
                break;
            }

            case ConstantApp.CODE_LEFT: {

                listener = new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerL != null) {
                                return true;
                            }

                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(mActionL, 200);
                            }
                            return false;
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerL == null) {
                                return true;
                            }
                            mHandlerL.removeCallbacks(mActionL);
                            mHandlerL = null;
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerL != null) {
                                return true;
                            }
                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(mActionL, 200);
                            }
                            return false;
                        }
                        return false;
                    }

                    Runnable mActionL = new Runnable() {
                        @Override
                        public void run() {
                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                if (mHandlerL == null)
                                    mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(this, 200);
                            }
                        }
                    };

                };
                break;
            }

            case ConstantApp.CODE_RIGHT: {

                listener = new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerR != null) {
                                return true;
                            }

                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(mActionR, 200);
                            }
                            return false;
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerR == null) {
                                return true;
                            }
                            mHandlerR.removeCallbacks(mActionR);
                            mHandlerR = null;
                            return false;

                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerR != null) {
                                return true;
                            }
                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(mActionR, 200);
                            }
                            return false;
                        }
                        return false;
                    }

                    Runnable mActionR = new Runnable() {
                        @Override
                        public void run() {
                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                if (mHandlerR == null)
                                    mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(this, 200);
                            }
                        }
                    };

                };
                break;
            }
            default:
                break;

        }

        return listener;
    }
}
