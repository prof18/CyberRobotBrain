package com.clemgmelc.cyberrobotbrain.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();

    //UI
    private DeviceScanActivity mActivity;
    private RecyclerView mRecyclerView;
    private ArrayList<BluetoothDevice> bDevices;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ProgressBar mProgress;
    private TextView mNoDevice;

    //GPS
    private LocationManager mLocationManager;
    private AlertDialog mAlertGps;
    private static final int ALERT_GPS = 1, REQUEST_LOCATION_PERMISSION = 1;

    //BLE
    private static final int REQUEST_ENABLE_BT = 1, ALERT_BLUE = 2;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private BluetoothLeScanner mLeScanner;
    private LeDeviceAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private AlertDialog mAlertBlue;
    private ScanSettings mSettings;
    private Handler mHandler;
    private boolean mScanning;
    private static final long SCAN_PERIOD = 3000;
    private BluetoothManager mBluetoothManager;

    /* ####### UI METHODS ####### */

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_scan_main);
        mActivity = this;

        //Progress Bar
        mProgress = (ProgressBar) findViewById(R.id.progressBar);
        mProgress.setIndeterminate(true);
        mProgress.setVisibility(View.INVISIBLE);

        //Text message for no device found
        mNoDevice = (TextView) findViewById(R.id.nodevice);
        mNoDevice.setVisibility(View.INVISIBLE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getResources().getString(R.string.title_activity_scan));

        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setHasFixedSize(true);

        //swipe down to refresh
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.container);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mNoDevice.setVisibility(View.INVISIBLE);
                if (!mScanning) {
                    Log.d(TAG, "mLeDeviceListAdapter cleared after swipe down");
                    mLeDeviceListAdapter.clear();
                    scanLeDevice(true);
                }
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.white);
        mSwipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.grey);

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        //This type of control (here and below) is useless because the minApi are 21, due to Camera2 API.
        //We have decided to maintain all in case of future back-compatibility with "Camera1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .build();
        }

        //handler necessary for the bluetooth scan
        mHandler = new Handler();

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //We need a receiver to listen for bluetooth connection state changes.
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(blueBroadcastReceiver, filter);

        //Runtime Permission Handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            verifyPermissions(mActivity);

        else {

            int coarseLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[0]);
            int fineLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[1]);

            if (coarseLocation == PackageManager.PERMISSION_GRANTED || fineLocation == PackageManager.PERMISSION_GRANTED) {

                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 10, locationListener);

                //Send an alert if the bluetooth or the GPS aren't enabled
                if (!mBluetoothAdapter.isEnabled()) {
                    if (mAlertBlue == null) {
                        mAlertBlue = showAlert(ALERT_BLUE).create();
                    }
                }

                if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    if (mAlertGps == null) {
                        mAlertGps = showAlert(ALERT_GPS).create();
                        mAlertGps.show();
                    }
                }

                //launch the scanning
                if (!mScanning &&
                        mBluetoothAdapter.isEnabled() &&
                        mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    scanLeDevice(true);
            }
        }
    }

    /**
     * This method will create an AlertDialog.Builder that can be used to create and show
     * the correct Alert for gps and bluetooth enabling
     *
     * @param type can be equal to integer ALERT_GPS or ALERT_BLUE
     * @return AlertDialog.Builder for gps correctly prepared and a not null AlertDialog.Builder
     * for the bluetooth. The not null AlertDialog.Builder can be used to understand if the activity
     * is currently showing the system alert for bluetooth enabling.
     */
    private AlertDialog.Builder showAlert(int type) {

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        switch (type) {

            case (ALERT_GPS):
                Log.d(TAG, "Activation GPS ALERT started");
                alertDialog.setMessage(getResources().getString(R.string.gps_required));
                alertDialog.setCancelable(false);
                alertDialog.setPositiveButton(getResources().getString(R.string.gps_enabling),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent callGPSSettingIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
                alertDialog.setNegativeButton(getResources().getString(R.string.gps_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                if (mLocationManager != null)
                                    mLocationManager.removeUpdates(locationListener);
                                showNegativeDialog(getResources().getString(R.string.gps_error_title),
                                        getResources().getString(R.string.gps_error_msg)
                                );
                            }
                        });
                break;

            case (ALERT_BLUE):
                Log.d(TAG, "Activation BLE ALERT Started");
                if (mAlertBlue == null) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                break;

            default:
                alertDialog = null;
                break;
        }

        return alertDialog;
    }

    /**
     * This method shows a dialog of error that will close the activity after ok is pressed
     *
     * @param title   The title of the Alert Dialog
     * @param message The message of the Alert Dialog
     */
    private void showNegativeDialog(String title, String message) {

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(title);
        dialogBuilder.setMessage(message);
        dialogBuilder.setCancelable(false);
        dialogBuilder.setNegativeButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        dialogBuilder.show();
    }

    /**
     * This class creates and manages the adapter for the list of devices discovered during the scanning
     * that are showed on a Recycler View
     */
    private class LeDeviceAdapter extends RecyclerView.Adapter<LeDeviceAdapter.ViewHolder> {

        private ArrayList<BluetoothDevice> mLeDevices;
        private int rowLayout;

        private LeDeviceAdapter(ArrayList<BluetoothDevice> list, int rowLayout) {
            this.mLeDevices = list;
            this.rowLayout = rowLayout;
        }

        private void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device))
                mLeDevices.add(device);
        }

        private void clear() {
            mLeDevices.clear();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(rowLayout, viewGroup, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ViewHolder viewHolder, int position) {

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

                    if (mScanning) {
                        Toast.makeText(mActivity, getResources().getText(R.string.wait_during_scan), Toast.LENGTH_SHORT).show();
                    } else {
                        BluetoothDevice device = mLeDevices.get(viewHolder.getAdapterPosition());
                        mActivity.scanLeDevice(false);
                        //send back the address to the main activity
                        Intent intent = new Intent();
                        intent.putExtra("DEVICE_ADDRESS", device.getAddress());
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mLeDevices.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            private TextView deviceName;
            private TextView deviceAddress;

            public ViewHolder(View itemView) {

                super(itemView);
                deviceName = (TextView) itemView.findViewById(R.id.device_name);
                deviceAddress = (TextView) itemView.findViewById(R.id.device_address);
            }
        }
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

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume() called");
        mNoDevice.setVisibility(View.INVISIBLE);
        if (!mScanning && mBluetoothAdapter.isEnabled() && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.v(TAG, "scanLeDevice(true) in onResume()");
            scanLeDevice(true);
            mSwipeRefreshLayout.setEnabled(false);
            mProgress.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPaused() called");
        if (mScanning) {
            Log.v(TAG, "scanLeDevice(false) in onPause()");
            scanLeDevice(false);
        }
        //clear the list of devices
        if (mLeDeviceListAdapter != null && mLeDeviceListAdapter.getItemCount() > 0) {
            Log.v(TAG, "mLeDeviceListAdapter cleared in onPause()");
            mLeDeviceListAdapter.clear();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.v(TAG, "onStop() called");

       /* if (blueBroadcastReceiver != null)
            unregisterReceiver(blueBroadcastReceiver);*/
        if (mScanning || (mLeDeviceListAdapter != null && mLeDeviceListAdapter.getItemCount() > 0)) {
            Log.d(TAG, "scanLeDevice(false) in onStop()");
            Log.d(TAG, "mLeDeviceListAdapter cleared in onStop()");
            scanLeDevice(false);
            mLeDeviceListAdapter.clear();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        if (mLocationManager != null)
            mLocationManager.removeUpdates(locationListener);

        if (blueBroadcastReceiver != null)
            unregisterReceiver(blueBroadcastReceiver);

    }


    /* ####### PERMISSION METHODS ####### */

        /**
         * This method checks location permissions and control if gps and bluetooth are enabled
         *
         * @param activity Activity Reference
         */

    public void verifyPermissions(Activity activity) {

        int coarseLocation = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_LOCATION[0]);
        int fineLocation = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_LOCATION[1]);

        if (coarseLocation != PackageManager.PERMISSION_GRANTED || fineLocation != PackageManager.PERMISSION_GRANTED) {

            // No permission, so ask them
            ActivityCompat.requestPermissions(activity, PERMISSIONS_LOCATION, REQUEST_LOCATION_PERMISSION);

        } else {

            // Permission are granted. Check if GPS and bluetooth are enabled or send an alert to activate them
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 10, locationListener);
            if (!mBluetoothAdapter.isEnabled())
                if (mAlertBlue == null) {
                    mAlertBlue = showAlert(ALERT_BLUE).create();
                }
            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (mAlertGps == null) {
                    mAlertGps = showAlert(ALERT_GPS).create();
                    mAlertGps.show();
                }
            }
        }
    }

    //Handling of the response of the permissions request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        int coarseLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[0]);
        int fineLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[1]);

        switch (requestCode) {

            case (REQUEST_LOCATION_PERMISSION):

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Permission Granted");

                    if (coarseLocation == PackageManager.PERMISSION_GRANTED && fineLocation == PackageManager.PERMISSION_GRANTED) {

                        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 10, locationListener);

                        // Permission are granted. Check if GPS and bluetooth are enabled or send an alert to activate them
                        if (!mBluetoothAdapter.isEnabled() && mAlertBlue == null)
                            mAlertBlue = showAlert(ALERT_BLUE).create();
                        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && mAlertGps == null) {
                            mAlertGps = showAlert(ALERT_GPS).create();
                            mAlertGps.show();
                        }
                        if (mBluetoothAdapter.isEnabled() && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Log.d(TAG, "scanLeDevice(true) after permission and all enabled");
                            scanLeDevice(true);
                        }
                    }

                } else {

                    Log.v(TAG, "Permission NOT Granted");
                    showNegativeDialog(getResources().getString(R.string.perm_error_title),
                            getResources().getString(R.string.perm_error_msg)
                    );
                    if (mLocationManager != null)
                        mLocationManager.removeUpdates(locationListener);
                }
                break;

            default:
                break;
        }
    }

    /* ####### BLUETOOTH METHODS ####### */

    //handling of the response of the system request to turn on bluetooth
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult called");

        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {

            // User choose NOT to enable Bluetooth.
            mAlertBlue = null;
            showNegativeDialog(getResources().getString(R.string.blue_error_title),
                    getResources().getString(R.string.blue_error_msg)
            );

        } else {

            // User choose to enable Bluetooth.
            mAlertBlue = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                verifyPermissions(mActivity);
            else {
                mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = mBluetoothManager.getAdapter();
                scanLeDevice(true);
            }
        }
    }

    //This Broadcast Receiver manages the changes of the Bluetooth connection state
    BroadcastReceiver blueBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {

                    case BluetoothAdapter.STATE_ON:
                        Log.v(TAG, "Change ----> BLUE ACTIVATED");

                        //start the scan
                        if (mLeScanner == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                        if (!mScanning && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Log.d(TAG, "SCAN start from broadcast Receiver");
                            scanLeDevice(true);
                        }
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Change ---->BLUE DEACTIVATED");

                        //show a dialog to enable the bluetooth
                        if (mAlertBlue == null)
                            mAlertBlue = showAlert(ALERT_BLUE).create();
                        break;

                    default:
                        break;
                }
            }
        }
    };


    /**
     * This method starts or stops the device scan
     *
     * @param enabled This parameter indicates if the scan has to be started or stopped
     */
    private void scanLeDevice(boolean enabled) {

        if (enabled) {

            Log.v(TAG, "SCANNING START");

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    // if scanning stop previous scanning operation
                    if (mScanning) {
                        mScanning = false;
                        mSwipeRefreshLayout.setRefreshing(false);
                        mSwipeRefreshLayout.setEnabled(true);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.v(TAG, "stopLeScan STOP scan is ended after SCAN_PERIOD");
                            mLeScanner.stopScan(mScanCallback);
                        } else {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        }

                        mProgress.setVisibility(View.INVISIBLE);

                        if (mLeDeviceListAdapter.getItemCount() == 0 && mAlertBlue == null && mAlertGps == null) {
                            Log.v(TAG, "NO DEVICES FOUND");
                            mNoDevice.setVisibility(View.VISIBLE);
                        }
                    }
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mSwipeRefreshLayout.setRefreshing(true);
            mSwipeRefreshLayout.setEnabled(true);

            //setup the list of device and the adapter for the recycler view.
            bDevices = new ArrayList<>();
            mLeDeviceListAdapter = new LeDeviceAdapter(bDevices, R.layout.device_scan_row);
            mRecyclerView.setAdapter(mLeDeviceListAdapter);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "StartLeScan START NEW SCAN");
                mLeScanner.startScan(null, mSettings, mScanCallback);
            } else {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }

        } else {

            //stop the scan
            mScanning = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "stopLeScan STOP scan is ended after SCAN_PERIOD");
                //You can stop the scan only if the bluetooth is yet ON
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON)
                    mLeScanner.stopScan(mScanCallback);
            } else {
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON)
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }
        invalidateOptionsMenu();
    }

    // Device scan callback: what to do when a new device is discovered
    private ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //add the new device to the list
                    if (device.getName() != null && device.getName().equals(ConstantApp.ROBOT_BLE_NAME))
                        mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    // Device scan callback: what to do when a new device is discovered
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    //add the new device to the list
                    if (device.getName() != null && device.getName().equals(ConstantApp.ROBOT_BLE_NAME))
                        mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    /* ####### GPS METHODS ####### */

    //This Location Listener manages the changes of the GPS connection state
    LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {

            Log.d(TAG, "Change ----> GPS Activated");
            mAlertGps.dismiss();
            mAlertGps = null;
            //start the scan
            if (!mScanning && mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "SCAN start from gps");
                scanLeDevice(true);
            }
        }

        @Override
        public void onProviderDisabled(String provider) {

            Log.d(TAG, "Change ----> GPS Deactivated");
            if (mAlertGps == null) {
                mAlertGps = showAlert(ALERT_GPS).create();
                mAlertGps.show();
            }
        }
    };
}