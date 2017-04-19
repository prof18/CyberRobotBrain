package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {

    private LeDeviceAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private boolean mConnected = false;

    private RecyclerView mRecyclerView;
    private ArrayList<BluetoothDevice> bDevices;

    private SwipeRefreshLayout mSwipeRefreshLayout;

    private final String TAG = ConstantApp.TAG;

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private DeviceScanActivity mActivity;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_scan_main);
        mActivity = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getResources().getString(R.string.title_activity_scan));

        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setHasFixedSize(true);

        //setup swipe for refresh
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.container);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {

                if (!mScanning) {
                    mLeDeviceListAdapter.clear();
                    mSwipeRefreshLayout.setRefreshing(true);
                    scanLeDevice(true);
                } else {
                    Toast.makeText(mActivity, getResources().getText(R.string.connect_dialog_rescan_error), Toast.LENGTH_SHORT).show();
                    mSwipeRefreshLayout.setRefreshing(false);

                }
            }
        });

        mHandler = new Handler();
        initializeBluetooth();
    }


    //check if bluetooth is enabled and set the adapter. If not, open a dialog.
    private void initializeBluetooth() {

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();


        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        } else {

            //ask runtime permission only for api higher than 23
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                verifyStoragePermissions(this);
            } else {
                scanLeDevice(true);
            }
        }
    }

    //check if the user has activated the bluetooth. If yes, ask permission for BLE instead show a dialog
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            showNegativeDialog(getResources().getString(R.string.blue_error_title),
                    getResources().getString(R.string.blue_error_msg));

        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                verifyStoragePermissions(mActivity);
            } else {

                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();

                scanLeDevice(true);
            }
        }
    }

    //check permissions.
    public void verifyStoragePermissions(Activity activity) {
        // Check if we have read or write permission
        int coarseLocation = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION);
        int fineLocation = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION);

        if (coarseLocation != PackageManager.PERMISSION_GRANTED || fineLocation != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_LOCATION,
                    REQUEST_LOCATION_PERMISSION
            );
        } else {
            if (mBluetoothAdapter == null) {
                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();
            }
            scanLeDevice(true);
        }
    }

    //handle the response of requesting permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {

            case REQUEST_LOCATION_PERMISSION:

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Permission Granted");
                    if (mBluetoothAdapter == null) {
                        final BluetoothManager bluetoothManager =
                                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                        mBluetoothAdapter = bluetoothManager.getAdapter();
                    }
                    scanLeDevice(true);

                } else {

                    showNegativeDialog(getResources().getString(R.string.perm_error_title),
                            getResources().getString(R.string.perm_error_msg));

                }
                break;
        }
    }

    //show a dialog
    private void showNegativeDialog(String title, String message) {

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(title);
        dialogBuilder.setMessage(message);
        dialogBuilder.setCancelable(false);
        dialogBuilder.setPositiveButton(android.R.string.ok, null);
        dialogBuilder.show();
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


    //clear the adapter and unregister the receiver
    @Override
    protected void onPause() {
        super.onPause();
        if (mScanning || mLeDeviceListAdapter != null) {
            scanLeDevice(false);
            mLeDeviceListAdapter.clear();
        }
    }

    //Enable bluetooth scanning
    private void scanLeDevice(boolean enabled) {

        if (enabled) {

            Log.v(TAG, "Start Scanning");
            Toast.makeText(this, getResources().getString(R.string.scanning), Toast.LENGTH_SHORT).show();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    if (mScanning) {
                        mScanning = false;
                        //TODO: Deal with deprecated method
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        Toast.makeText(getApplicationContext(), R.string.scan_stopped, Toast.LENGTH_LONG).show();

                        //show a message in the dialog if there isn't any BLE device
                        if (mLeDeviceListAdapter.getItemCount() == 0) {


                        }
                    }

                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);


            mScanning = true;

            bDevices = new ArrayList<BluetoothDevice>();
            mLeDeviceListAdapter = new LeDeviceAdapter(bDevices, R.layout.device_scan_row);
            mRecyclerView.setAdapter(mLeDeviceListAdapter);


            //TODO: Deal with deprecated method
            mBluetoothAdapter.startLeScan(mLeScanCallback);

        } else {

            mScanning = false;
            //TODO: Deal with deprecated method
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();

    }

    // Device scan callback: what to when a new device is discovered
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            //add the new device to the list
                            mSwipeRefreshLayout.setRefreshing(false);
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    //adapter for the device list.    
    private class LeDeviceAdapter extends RecyclerView.Adapter<LeDeviceAdapter.ViewHolder> {

        private ArrayList<BluetoothDevice> mLeDevices;

        private int rowLayout;


        private LeDeviceAdapter(ArrayList<BluetoothDevice> list, int rowLayout) {

            this.mLeDevices = list;
            this.rowLayout = rowLayout;
        }

        private void addDevice(BluetoothDevice device) {

            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        private void clear() {
            mLeDevices.clear();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {

            View v = LayoutInflater.from(viewGroup.getContext()).inflate(rowLayout, viewGroup, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {

            final BluetoothDevice mDevice = mLeDevices.get(position);
            final String deviceName = mDevice.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.no_name_device);
            viewHolder.deviceAddress.setText(mDevice.getAddress());

            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    BluetoothDevice device = mLeDevices.get(position);
                    mActivity.scanLeDevice(false);
                    Intent intent = new Intent();
                    intent.putExtra("DEVICE_ADDRESS", device.getAddress());
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            });
        }

        @Override
        public int getItemCount() {

            return mLeDevices.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView deviceName;
            public TextView deviceAddress;
            ImageView image;

            public ViewHolder(View itemView) {

                super(itemView);

                deviceName = (TextView) itemView.findViewById(R.id.device_name);
                deviceAddress = (TextView) itemView.findViewById(R.id.device_address);
                image = (ImageView) itemView.findViewById(R.id.image);
            }
        }
    }
}