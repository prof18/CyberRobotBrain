/*
*   Copyright 2017 Biasin Mattia, Dominutti Giulio, Gomiero Marco
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*
*/

package com.clemgmelc.cyberrobotbrain.ui;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
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

import com.clemgmelc.cyberrobotbrain.data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;

public class ManualNavigationActivity extends AppCompatActivity {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();
    private ImageButton mForward, mBackward, mLeft, mRight;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothGattService mMovementGattService;
    private BluetoothGattCharacteristic mMovementCharacteristic;
    private Handler mHandlerF, mHandlerB, mHandlerL, mHandlerR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_navigation_activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDeviceAddress = getIntent().getStringExtra(ConstantApp.DEVICE_ADDRESS);
        mForward = (ImageButton) findViewById(R.id.forward_button);
        mBackward = (ImageButton) findViewById(R.id.backward_button);
        mRight = (ImageButton) findViewById(R.id.right_button);
        mLeft = (ImageButton) findViewById(R.id.left_button);

        //add listeners for the 4 different movements
        mForward.setOnTouchListener(movementListener(ConstantApp.CODE_FORWARD));
        mBackward.setOnTouchListener(movementListener(ConstantApp.CODE_BACKWARD));
        mLeft.setOnTouchListener(movementListener(ConstantApp.CODE_LEFT));
        mRight.setOnTouchListener(movementListener(ConstantApp.CODE_RIGHT));
    }

    @Override
    public void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, ConstantApp.gattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                super.onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // Manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        //what to do when connected to the service
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            if (mDeviceAddress != null) {
                //get the Characteristic for the Movement
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


    private View.OnTouchListener movementListener(int movement) {

        View.OnTouchListener listener = null;
        switch (movement) {

            case ConstantApp.CODE_FORWARD:

                listener = new View.OnTouchListener() {

                    /*
                     * This is the action of movement_FORWARD executed inside a new Runnable
                     * The Runnable execute itself with a delay. When pressure on the button ends
                     * the run will be destroyed
                     */
                    Runnable mActionF = new Runnable() {
                        @Override
                        public void run() {

                            //Controls that no other action are in execution checking their corresponding handler == null
                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                if (mHandlerF == null)
                                    mHandlerF = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(this, 200);
                            }
                        }
                    };

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        //On the pressure of the button: handler is created, a single movement is executed and the runnable is started
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerF != null)
                                return true;

                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerF = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(mActionF, 200);
                            }
                            return false;

                            //On the release of the button destroy the runnable
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerF == null)
                                return true;

                            mHandlerF.removeCallbacks(mActionF);
                            mHandlerF = null;
                            return false;

                            /*
                             * This action is returned when something appends during a press gesture (between ACTION_DOWN and ACTION_UP)
                             * Here it's used to recognize a pressure on other buttons in order to be able to manage multiple pressures
                             */
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            if (mHandlerF != null)
                                return true;

                            if (mHandlerB == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerF = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                mHandlerF.postDelayed(mActionF, 200);
                            }
                            return false;
                        }
                        return false;
                    }
                };
                break; // Forward


            case ConstantApp.CODE_BACKWARD:

                listener = new View.OnTouchListener() {

                    /*
                     * This is the action of movement_BACKWARD executed inside a new Runnable
                     * The Runnable execute itself with a delay. When pressure on the button ends
                     * the run will be destroyed
                     */
                    Runnable mActionB = new Runnable() {
                        @Override
                        public void run() {

                            //Controls that no other action are in execution checking their corresponding handler == null
                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                if (mHandlerB == null)
                                    mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(this, 200);
                            }
                        }
                    };

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerB != null)
                                return true;

                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(mActionB, 200);
                            }
                            return false;

                            //On the release of the button destroy the runnable
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerB == null)
                                return true;

                            mHandlerB.removeCallbacks(mActionB);
                            mHandlerB = null;
                            return false;

                            /*
                             * This action is returned when something appends during a press gesture (between ACTION_DOWN and ACTION_UP)
                             * Here it's used to recognize a pressure on other buttons in order to be able to manage multiple pressures
                             */
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerB != null)
                                return true;

                            if (mHandlerF == null && mHandlerL == null && mHandlerR == null) {
                                mHandlerB = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.backward);
                                mHandlerB.postDelayed(mActionB, 200);
                            }
                            return false;
                        }
                        return false;
                    }
                };
                break; // Backward

            case ConstantApp.CODE_LEFT:

                listener = new View.OnTouchListener() {

                    /*
                     * This is the action of movement_LEFT executed inside a new Runnable
                     * The Runnable execute itself with a delay. When pressure on the button ends
                     * the run will be destroyed
                     */
                    Runnable mActionL = new Runnable() {
                        @Override
                        public void run() {

                            //Controls that no other action are in execution checking their corresponding handler == null
                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                if (mHandlerL == null)
                                    mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(this, 200);
                            }
                        }
                    };

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        //On the pressure of the button: handler is created, a single movement is executed and and the runnable is started
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerL != null)
                                return true;


                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(mActionL, 200);
                            }
                            return false;

                            //On the release of the button destroy the runnable
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerL == null)
                                return true;

                            mHandlerL.removeCallbacks(mActionL);
                            mHandlerL = null;
                            return false;

                            /*
                             * This action is returned when something appends during a press gesture (between ACTION_DOWN and ACTION_UP)
                             * Here it's used to recognize a pressure on other buttons in order to be able to manage multiple pressures
                             */
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerL != null)
                                return true;

                            if (mHandlerF == null && mHandlerB == null && mHandlerR == null) {
                                mHandlerL = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                mHandlerL.postDelayed(mActionL, 200);
                            }
                            return false;
                        }
                        return false;
                    }
                };
                break; // Left

            case ConstantApp.CODE_RIGHT:

                listener = new View.OnTouchListener() {

                    /*
                     * This is the action of movement_RIGHT executed inside a new Runnable
                     * The Runnable execute itself with a delay. When pressure on the button ends
                     * the run will be destroyed
                     */
                    Runnable mActionR = new Runnable() {
                        @Override
                        public void run() {

                            //Controls that no other action are in execution checking their corresponding handler == null
                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                if (mHandlerR == null)
                                    mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(this, 200);
                            }
                        }
                    };

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        //On the pressure of the button: handler is created, a single movement is executed and and the runnable is started
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {

                            if (mHandlerR != null)
                                return true;

                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(mActionR, 200);
                            }
                            return false;

                            //On the release of the button destroy the runnable
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {

                            if (mHandlerR == null)
                                return true;

                            mHandlerR.removeCallbacks(mActionR);
                            mHandlerR = null;
                            return false;

                            /*
                             * This action is returned when something appends during a press gesture (between ACTION_DOWN and ACTION_UP)
                             * Here it's used to recognize a pressure on other buttons in order to be able to manage multiple pressures
                             */
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

                            if (mHandlerR != null)
                                return true;

                            if (mHandlerF == null && mHandlerB == null && mHandlerL == null) {
                                mHandlerR = new Handler();
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                mHandlerR.postDelayed(mActionR, 200);
                            }
                            return false;
                        }
                        return false;
                    }
                };
                break; // Right

            default:
                break;
        }

        return listener;
    }

    //manage connected, disconnected and discovered action
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            if (ConstantApp.ACTION_GATT_DISCONNECTED.equals(action)) {

                Log.v(TAG, "disconnected");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_disconnecting), Toast.LENGTH_SHORT).show();
                mBluetoothLeService.disconnect();
                mBluetoothLeService = null;
                onBackPressed();
                Log.v(TAG, "unregistered");
            }
        }
    };
}
