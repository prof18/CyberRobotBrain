package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {

    private LeDeviceAdapter mLeDeviceListAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    //GPS
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;

    private AlertDialog.Builder mAlertBuilderGps;
    private AlertDialog mAlertGps;


    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ALERT_GPS = 1;
    private static final int ALERT_BLUE = 2;

    // Stops scanning after 1,5 seconds.
    private static final long SCAN_PERIOD = 1500;

    private BroadcastReceiver mReceiver;

    private RecyclerView mRecyclerView;
    private ArrayList<BluetoothDevice> bDevices;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private final String TAG = "CyberRobotSCAN";

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private DeviceScanActivity mActivity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG,"onCreate() called");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_scan_main);
        mActivity = this;
        //LAYOUT
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

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        //use location manager and listener to be aware of GPS enabling/disabling
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = createLocationListener();
        //use a receiver to be aware of BLUE enabling/disabling
        mReceiver = createBroadcastReciever();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        //verify permissions
        verifyStoragePermissions(mActivity);

        mHandler = new Handler();

        //if blue and gps already on execute a scan
        scanLeDevice(true);

    }

    //
    private AlertDialog.Builder initializeGps() {

        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d(TAG, "CHECK: GPS ABILITATO PROCEDI");
            return null;
        } else {
            Log.d(TAG, "CHECK: GPS DISABILITATO STOP");
            return showAlert(ALERT_GPS);
        }
    }

    private AlertDialog.Builder initializeBlue() {

        if ( mBluetoothAdapter.isEnabled() ) {
            Log.d(TAG, "CHECK: BLUE ABILITATO PROCEDI");
            return null;
        } else {
            Log.d(TAG, "CHECK: BLUE DISABILITATO STOP");
            Log.d(TAG, "partito il messaggio di attivazione bluetooth");
            return showAlert(ALERT_BLUE);
        }


    }

    private AlertDialog.Builder showAlert(int type) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        switch (type) {
            case (ALERT_GPS):
                Log.d(TAG, "partito ALERT di attivazione GPS");
                alertDialog.setMessage(getResources().getString(R.string.gps_required));
                alertDialog.setCancelable(false);
                alertDialog.setPositiveButton(getResources().getString(R.string.gps_enabling),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
                                );
                                startActivity(callGPSSettingIntent);
                            }
                        });
                alertDialog.setNegativeButton(getResources().getString(R.string.gps_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                if (mLocationManager != null)
                                    mLocationManager.removeUpdates(mLocationListener);
                                showNegativeDialog(getResources().getString(R.string.gps_error_title),
                                        getResources().getString(R.string.blue_error_msg)
                                );
                            }
                        });

                break;
            case (ALERT_BLUE):
                Log.d(TAG, "partito ALERT di attivazione BLE");

                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                alertDialog = null;
                break;
            default:
                alertDialog = null;
                break;
        }
        return alertDialog;

    }

    private LocationListener createLocationListener() {
        LocationListener ll = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {}

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {
                Log.v(TAG, "CAMBIAMENTO UTENTE ---->GPS ATTIVATO");
                mAlertGps.dismiss();
                mAlertGps = null;
                if (!mScanning && mBluetoothAdapter.isEnabled()){
                    Log.v(TAG, "SCAN satrt from gps");
                    scanLeDevice(true);
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.v(TAG, "CAMBIAMENTO UTENTE ---->GPS DISATTIVATO.");
                if (mAlertGps == null) {
                    mAlertGps = showAlert(ALERT_GPS).create();
                    mAlertGps.show();
                }
            }
        };
        return ll;
    }

   // private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    private BroadcastReceiver createBroadcastReciever() {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_ON:
                            Toast.makeText(context, "Bluetooth on", Toast.LENGTH_LONG);
                            Log.v(TAG, "CAMBIAMENTO UTENTE ---->BLUE ATTIVATO");
                            if (!mScanning && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                Log.v(TAG, "SCAN satrt from blue");
                                scanLeDevice(true);
                            }
                            break;
                        case BluetoothAdapter.STATE_OFF:
                            Toast.makeText(context, "Bluetooth off", Toast.LENGTH_LONG);
                            Log.v(TAG, "CAMBIAMENTO UTENTE ---->BLUE DISATTIVATO");
                            showAlert(ALERT_BLUE);
                            break;
                    }
                }
            }
        };
        return br;
    }

    //check if bluetooth is enabled and set the adapter. If not, open a dialog.
    /*private void initializeBluetooth() {
        Log.d(TAG, "partito il messaggio di attivazione bluetooth");

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();


        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "partito il messaggio di attivazione bluetooth");

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
    }*/

    //check if the user has activated the bluetooth. If yes, ask permission for BLE instead show a dialog
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User chose to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED)
            showNegativeDialog(getResources().getString(R.string.blue_error_title),
                    getResources().getString(R.string.blue_error_msg)
            );
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                verifyStoragePermissions(mActivity);
            else {
                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();

                scanLeDevice(true);
            }
        }
    }

    //check location permissions.
    public void verifyStoragePermissions(Activity activity) {
        // Check if we have read or write permission
        int coarseLocation = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_LOCATION[0]);
        int fineLocation = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_LOCATION[1]);

        //if we don't have permission ask them
        if (coarseLocation != PackageManager.PERMISSION_GRANTED || fineLocation != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_LOCATION, REQUEST_LOCATION_PERMISSION);
        } else {
            //Location permission PREVIOUSLY granted so we register GPS updates
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 10, mLocationListener);

            if (!mBluetoothAdapter.isEnabled())
                initializeBlue();

            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (mAlertGps == null) {
                    mAlertGps = showAlert(ALERT_GPS).create();
                    mAlertGps.show();
                }
            }


            //scanLeDevice(true);
        }
    }

    //handle the response of requesting permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        int coarseLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[0]);
        int fineLocation = ActivityCompat.checkSelfPermission(this, PERMISSIONS_LOCATION[1]);
        switch (requestCode) {

            case REQUEST_LOCATION_PERMISSION:

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Permission Granted");

                    if (coarseLocation == PackageManager.PERMISSION_GRANTED && fineLocation == PackageManager.PERMISSION_GRANTED) {
                        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 10, mLocationListener);


                        if (!mBluetoothAdapter.isEnabled())
                            initializeBlue();

                        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            if (mAlertGps == null) {
                                mAlertGps = showAlert(ALERT_GPS).create();
                                mAlertGps.show();
                            }
                        }

                        if (mBluetoothAdapter.isEnabled() && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Log.v(TAG,"cagati addosso");
                            scanLeDevice(true);
                        }
                    }
                    //scanLeDevice(true);

                } else {
                    Log.v(TAG, "Permission **NOT** Granted");
                    showNegativeDialog(getResources().getString(R.string.perm_error_title),
                            getResources().getString(R.string.perm_error_msg));
                    if (mLocationManager !=null)
                        mLocationManager.removeUpdates(mLocationListener);

                }
                break;
            default:
                break;
        }
    }

    //show a dialog of error that will close the scan activity
    private void showNegativeDialog(String title, String message) {

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(title);
        dialogBuilder.setMessage(message);
        dialogBuilder.setCancelable(false);
        dialogBuilder.setNegativeButton(android.R.string.ok,
        new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int which){
                finish();
            }
        });
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

    //Enable bluetooth scanning
    private void scanLeDevice(boolean enabled) {

        if (enabled) {
            // TODO: METTERE ROTELLA
            Log.v(TAG, "Start Scanning");
            //Toast.makeText(this, getResources().getString(R.string.scanning), Toast.LENGTH_SHORT).show();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    if (mScanning) {
                        mScanning = false;
                        //TODO: Deal with deprecated method
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        //Toast.makeText(getApplicationContext(), R.string.scan_stopped, Toast.LENGTH_LONG).show();
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
                            if (device.getName() != null && device.getName().equals("Cyber Robot"))
                                mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    //adapter for the device list.    


    //clear the adapter and unregister the receiver
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
        if (mScanning || mLeDeviceListAdapter != null) {
            scanLeDevice(false);
            Log.d(TAG, "distruggo la lista clear");
            mLeDeviceListAdapter.clear();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        unregisterReceiver(mReceiver);
        if (mLocationManager !=null)
            mLocationManager.removeUpdates(mLocationListener);
    }



    //#####################################################################################

    //#####################################################################################


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

                    if (mScanning) {
                        Toast.makeText(mActivity, getResources().getText(R.string.wait_during_scan), Toast.LENGTH_SHORT).show();
                    } else {

                        BluetoothDevice device = mLeDevices.get(position);
                        mActivity.scanLeDevice(false);
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

