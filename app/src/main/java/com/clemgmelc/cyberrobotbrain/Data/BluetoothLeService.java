package com.clemgmelc.cyberrobotbrain.Data;

/**
 * Created by marco on 4/19/17.
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.UI.AutoNavigation;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import java.util.List;
import java.util.UUID;

/**
 *
 * Service that manages the Bluetooth Low Energy connection
 *
 */
public class BluetoothLeService extends Service {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigation.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    //never used
    private int mConnectionState;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


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

    private final IBinder mBinder = new LocalBinder();


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
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        //get a reference of the bluetooth adapter
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        Log.v(TAG, "Bluetooth Adapter Initialized");
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
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
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.v(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.v(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        //autoconnect is false, so the connection is made manually every time
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.v(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
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
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.v(TAG, "Connected to GATT server.");
                //get the service offered by the GATT Service
                boolean discoverStatus = mBluetoothGatt.discoverServices();
                Log.v(TAG, "Services of the GATT Server Discovered:" + discoverStatus);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ConstantApp.ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
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

        /*@Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ConstantApp.ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ConstantApp.ACTION_DATA_AVAILABLE, characteristic);
        }*/
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

/*    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        //?? For all other profiles, writes the data formatted in HEX. ??
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(ConstantApp.EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
        }

        sendBroadcast(intent);
    }*/


    /**
     * Read a value from a specific GATT Characteristic
     *
     * @param characteristic The characteristic from which we want to read
     */
   /* public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.v(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }*/

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
