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

package com.clemgmelc.cyberrobotbrain.data;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.ui.AutoNavigationActivity;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;

import java.util.List;

/**
 *
 * Service that manages the Bluetooth Low Energy connection
 */
public class BluetoothLeService extends Service {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private final IBinder mBinder = new LocalBinder();

    //Handling of the Service Binding
    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    /**
     * Get a reference of the Bluetooth Adapter
     *
     * @return The method returns true if the initialization has been made
     */
    public boolean initialize() {

        //get a reference of the bluetooth manager
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager");
                return false;
            }
        }

        //get a reference of the bluetooth adapter
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        Log.v(TAG, "Bluetooth Adapter Initialized");
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }
        return true;
    }

    /**
     * Connect to a GATT Server
     *
     * @param address   The address of the GATT Server
     * @return          The method returns true if the connection is successful
     */
    public boolean connect(final String address) {

        if (mBluetoothAdapter == null || address == null) {
            Log.v(TAG, "BluetoothAdapter not initialized or null address.");
            return false;
        }

        //The device is already connected. Try a reconnection
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.v(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.v(TAG, "Device not found. Unable to connect.");
            return false;
        }

        //autoconnect is false, so the connection is made manually every time
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.v(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnect from the Bluetooth GATT
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.v(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     *  Release the resources after using a BLE Device
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.v(TAG, "Bluetooth GATT Closed");
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    //callback that handles the actions to do when the connection state changes
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            Log.v(TAG, "Connection Status Changed");
            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ConstantApp.ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.v(TAG, "Connected to GATT server.");
                //get the service offered by the GATT Service
                boolean discoverStatus = mBluetoothGatt.discoverServices();
                Log.v(TAG, "Services of the GATT Server Discovered:" + discoverStatus);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ConstantApp.ACTION_GATT_DISCONNECTED;
                Log.v(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        //What to do when the service of the GATT Server are discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ConstantApp.ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.v(TAG, "onServicesDiscovered not success. Status: " + status);
            }
        }
    };

    /**
     * Send a broadcast when a specific situation occurs
     *
     * @param action The type of action that is occurred, e.g. connection, disconnection, etc
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * Write a value to a specific GATT Characteristic
     *
     * @param characteristic    The characteristic on which we want to write
     * @param value             The value that has to be write
     * @return                  The method returns true if the value has been written
     */
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.v(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        characteristic.setValue(value);
        mBluetoothGatt.writeCharacteristic(characteristic);
        return true;
    }

    /**
     * Return the list of the GATT Services supported
     *
     * @return  A list of the supported GATT Services
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }
}
