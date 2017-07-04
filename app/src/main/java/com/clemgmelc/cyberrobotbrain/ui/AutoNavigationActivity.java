package com.clemgmelc.cyberrobotbrain.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.computation.Calibration;
import com.clemgmelc.cyberrobotbrain.computation.Navigation;
import com.clemgmelc.cyberrobotbrain.data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;
import com.clemgmelc.cyberrobotbrain.util.Utility;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AutoNavigationActivity extends AppCompatActivity {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();

    //UI
    private TextureView mTextureView;
    private FloatingActionButton mFabMenu, mFabPictureCalib, mFabStop, mFabDirect, mFabL, mFabCalib;
    private Animation mFabOpen, mFabClose, rotForward, rotBackward;
    private Button mButtonNext;
    private ImageView mTestImage;
    private Activity mActivity;
    private TextView mCalibrationInfo;
    private TextView mLMovLabel, mDirectMovLabel, mCalibrationLabel, mCalibrationNeedTitle;

    //Camera
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize, mMaxSize;
    private ImageReader mImageReader;
    //The semaphore is necessary to securely release the camera
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA
    };
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    //Movement
    private int k = 1, colorCounter;
    private Mat mOriginal, caseHsv, caseLeft, caseRight, caseTarget, caseTarget2, touchedRegionHsv, touchedRegionRgba;
    private Bitmap myBitmap;
    private Calibration mDetector;
    private boolean mIsCalibrating = false;
    private org.opencv.core.Point centerLeft, centerRight, centerTarget, centerMean;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothGattService mMovementGattService;
    private BluetoothGattCharacteristic mMovementCharacteristic;
    private NavigationThread navigationThread;
    private boolean stop = false, mIsCalibrated = false, isOpen = false;
    private int movementType;
    private Double distanceTM;

    //Enables the next button
    private boolean debug = true;

    /* ####### UI METHODS ####### */

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.auto_navigation_main);
        mActivity = this;

        //Fab
        mFabMenu = (FloatingActionButton) findViewById(R.id.fabAutoNav);
        mFabPictureCalib = (FloatingActionButton) findViewById(R.id.fabCalibration);
        mFabStop = (FloatingActionButton) findViewById(R.id.fabStop);
        mFabDirect = (FloatingActionButton) findViewById(R.id.fabDirect);
        mFabL = (FloatingActionButton) findViewById(R.id.fabL);
        mLMovLabel = (TextView) findViewById(R.id.l_mov_label);
        mFabCalib = (FloatingActionButton) findViewById(R.id.fabRecalibration);

        //Text views
        mCalibrationInfo = (TextView) findViewById(R.id.calibrationInfo);
        mDirectMovLabel = (TextView) findViewById(R.id.direct_mov_label);
        mCalibrationLabel = (TextView) findViewById(R.id.calibration_label);
        mCalibrationNeedTitle = (TextView) findViewById(R.id.calibrationNeed);

        //Animations
        mFabOpen = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open);
        mFabClose = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_close);
        rotForward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_forward);
        rotBackward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_backward);

        mTestImage = (ImageView) findViewById(R.id.imageViewTest);
        mTextureView = (TextureView) findViewById(R.id.textureView);
        if (debug) mButtonNext = (Button) findViewById(R.id.next);

        mDeviceAddress = getIntent().getStringExtra(ConstantApp.DEVICE_ADDRESS);

        if (!OpenCVLoader.initDebug()) {
            onBackPressed();
            Log.d(TAG, "opencv not init");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[0]) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_CAMERA, REQUEST_CAMERA_PERMISSION);
            }
        }

        //Check if the calibration is done
        mIsCalibrated = Utility.isCalibrationDone(getApplicationContext());

        if (!mIsCalibrated) {
            mCalibrationNeedTitle.setVisibility(View.VISIBLE);
            mCalibrationLabel.setText(getResources().getString(R.string.button_calibration));
            mFabDirect.setVisibility(View.GONE);
            mFabL.setVisibility(View.GONE);
        }

        initFabListener();

        if (debug) initMaskVisualizerButton();
    }

    /**
     * This method initialize all the actions, visualization and pressure events on the fab buttons
     */
    public void initFabListener() {

        mFabMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "MENU pressed");
                animateFab();
                mFabDirect.setEnabled(true);
                mFabL.setEnabled(true);
                mFabCalib.setEnabled(true);
            }
        });

        mFabDirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFabDirect.setEnabled(false);
                mFabL.setEnabled(false);
                mFabCalib.setEnabled(false);
                animateFab();
                movementType = ConstantApp.DIRECT_MOVEMENT;
                mFabMenu.hide();
                mFabStop.show();
                stop = false;
                distanceTM = null;
                takePicture();
            }
        });

        mFabL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFabDirect.setEnabled(false);
                mFabL.setEnabled(false);
                mFabCalib.setEnabled(false);
                animateFab();
                movementType = ConstantApp.L_MOVEMENT;
                mFabMenu.hide();
                mFabStop.show();
                stop = false;
                takePicture();
            }
        });

        mFabCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFabDirect.setEnabled(false);
                mFabL.setEnabled(false);
                mFabCalib.setEnabled(false);
                animateFab();
                mFabMenu.hide();
                mFabPictureCalib.show();
                mIsCalibrating = true;
                mCalibrationInfo.setVisibility(View.VISIBLE);
                mCalibrationInfo.setText(R.string.info_recalibration);
                mFabPictureCalib.setVisibility(View.VISIBLE);
                mCalibrationNeedTitle.setVisibility(View.GONE);
                colorCounter = 0;
            }
        });

        mFabStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "STOP pressed");
                Toast.makeText(mActivity, getResources().getString(R.string.stopping), Toast.LENGTH_SHORT).show();
                mFabStop.hide();
                mFabMenu.show();
                mCalibrationInfo.setVisibility(View.GONE);
                //return to the "classic" camera view
                mTestImage.setVisibility(View.INVISIBLE);
                mTextureView.setVisibility(View.VISIBLE);
                mTextureView.getTop();
                stop = true;
            }
        });

        mFabPictureCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "PICTURE pressed");
                mFabPictureCalib.hide();
                takePicture();
            }
        });
    }

    /**
     * This method manage the animation of the fab button (menu)
     */
    public void animateFab() {

        if (isOpen) {

            //main fab animation
            mFabMenu.startAnimation(rotBackward);
            mFabMenu.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_menu));

            if (mIsCalibrated) {

                //sub fab animation
                mFabCalib.startAnimation(mFabClose);
                mFabL.startAnimation(mFabClose);
                mFabDirect.startAnimation(mFabClose);
                //fab label animation
                mCalibrationLabel.startAnimation(mFabClose);
                mLMovLabel.startAnimation(mFabClose);
                mDirectMovLabel.startAnimation(mFabClose);
                //hide fab label
                mCalibrationLabel.setVisibility(View.GONE);
                mLMovLabel.setVisibility(View.GONE);
                mDirectMovLabel.setVisibility(View.GONE);
                //hide fab
                mFabCalib.hide();
                mFabL.hide();
                mFabDirect.hide();

            } else {
                //sub fab animation
                mFabCalib.startAnimation(mFabClose);
                //fab label animation
                mCalibrationLabel.startAnimation(mFabClose);
                //hide fab label
                mCalibrationLabel.setVisibility(View.GONE);
                //hide fab
                mFabCalib.hide();
            }
            isOpen = false;

        } else {

            //main fab animation
            mFabMenu.startAnimation(rotForward);
            mFabMenu.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_add));

            if (mIsCalibrated) {

                //sub fab animation
                mFabCalib.startAnimation(mFabOpen);
                mFabL.startAnimation(mFabOpen);
                mFabDirect.startAnimation(mFabOpen);
                //fab label animation
                mCalibrationLabel.startAnimation(mFabOpen);
                mLMovLabel.startAnimation(mFabOpen);
                mDirectMovLabel.startAnimation(mFabOpen);
                //show fab
                mFabCalib.show();
                mFabL.show();
                mFabDirect.show();
                //show fab label
                mCalibrationLabel.setVisibility(View.VISIBLE);
                mLMovLabel.setVisibility(View.VISIBLE);
                mDirectMovLabel.setVisibility(View.VISIBLE);

            } else {
                //sub fab animation
                mFabCalib.startAnimation(mFabOpen);
                //fab label animation
                mCalibrationLabel.startAnimation(mFabOpen);
                //show fab
                mFabCalib.show();
                //show fab label
                mCalibrationLabel.setVisibility(View.VISIBLE);
            }
            isOpen = true;
        }
    }

    /**
     * This method (used during developing and debugging) display in steps all the picture elaboration
     * done by the application (mask of HSV and markers recognition)
     */
    public void initMaskVisualizerButton() {

        mButtonNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Log.v(TAG, "K: " + k);

                switch (k) {

                    case 1:
                        Log.d(TAG, "HSV MASK");
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText(getResources().getString(R.string.hsv_mask));
                        Utils.matToBitmap(caseHsv, myBitmap);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTestImage.setImageBitmap(myBitmap);
                                mTextureView.setVisibility(View.INVISIBLE);
                                mTestImage.setVisibility(View.VISIBLE);
                                mTestImage.getTop();
                            }
                        });
                        k++;
                        break;

                    case 2:
                        Log.d(TAG, "CASE LEFT");
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText(getResources().getString(R.string.left_mask));

                        Utils.matToBitmap(caseLeft, myBitmap);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTestImage.setImageBitmap(myBitmap);
                                mTextureView.setVisibility(View.INVISIBLE);
                                mTestImage.setVisibility(View.VISIBLE);
                                mTestImage.getTop();
                            }
                        });
                        k++;
                        break;
                    case 3:
                        Log.d(TAG, "CASE TARGET");
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText(getResources().getString(R.string.target_mask));

                        Utils.matToBitmap(caseTarget, myBitmap);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTestImage.setImageBitmap(myBitmap);
                                mTextureView.setVisibility(View.INVISIBLE);
                                mTestImage.setVisibility(View.VISIBLE);
                                mTestImage.getTop();
                            }
                        });
                        k++;
                        break;

                    case 4:
                        Log.d(TAG, "CASE RIGHT");
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText(getResources().getString(R.string.right_mask));

                        Utils.matToBitmap(caseRight, myBitmap);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTestImage.setImageBitmap(myBitmap);
                                mTextureView.setVisibility(View.INVISIBLE);
                                mTestImage.setVisibility(View.VISIBLE);
                                mTestImage.getTop();
                            }
                        });
                        k = 1;
                        break;
                }
            }
        });
    }

    //The camera2 API require a surface on which display the camera preview.
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
            if (mTextureView.isAvailable()) {
                closeCamera();
                setupCamera(width, height);
                connectCamera();
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    //Layout parameters
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, ConstantApp.gattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        mTextureView.setSurfaceTextureListener(null);
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /* ####### BLUETOOTH METHODS ####### */

    //Manage Service lifecycle.
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

    //Manage connected, disconnected and discovered action
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            if (ConstantApp.ACTION_GATT_DISCONNECTED.equals(action)) {

                Log.v(TAG, "Robot Disconnected");
                Toast.makeText(mBluetoothLeService, getResources().getString(R.string.message_disconnecting), Toast.LENGTH_SHORT).show();

                mBluetoothLeService.disconnect();
                mBluetoothLeService = null;
                onBackPressed();
            }
        }
    };

    /* ####### CAMERA METHODS ####### */

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    /**
     * This method set up the camera.
     *
     * @param width  width of the surface available
     * @param height height of the surface available
     */
    private void setupCamera(int width, int height) {

        CameraCharacteristics cameraCharacteristics = null;
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //Scan all cameras available and obtain the index of the back camera
            for (String cameraId : cameraManager.getCameraIdList()) {

                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mCameraId = cameraId;
                    //When camera is found exit
                    break;
                }
            }

            //Obtain characteristic of the back camera
            StreamConfigurationMap mMap = null;
            try {
                mMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            } catch (NullPointerException e) {
                e.printStackTrace();
                onBackPressed();
                Toast.makeText(mActivity, getResources().getString(R.string.error_camera), Toast.LENGTH_SHORT).show();
            }

            //Obtain all available image resolution
            Size[] mSizeList = null;
            if (mMap != null)
                mSizeList = mMap.getOutputSizes(ImageFormat.JPEG);

            int maxResIndex = Utility.maxRes(mSizeList);
            if (maxResIndex != -1) {
                try {
                    mMaxSize = mSizeList[maxResIndex];
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    onBackPressed();
                    Toast.makeText(mActivity, getResources().getString(R.string.error_camera), Toast.LENGTH_SHORT).show();
                }
            }

            //Initialize a new ImageReader using the MaxSize (capture picture with max resolution) and assign the listener
            mImageReader = ImageReader.newInstance(mMaxSize.getWidth(), mMaxSize.getHeight(), ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            //To display image in landscape without stretching it image need to be adapted to the screen size available
            Point displaySize = new Point();
            this.getWindowManager().getDefaultDisplay().getSize(displaySize);
            int maxPreviewWidth = displaySize.y;
            int maxPreviewHeight = displaySize.x;

            //Select the preview size comparing available sizes and the resolution
            try {
                mPreviewSize = Utility.chooseOptimalSize(mMap.getOutputSizes(SurfaceTexture.class), width, height, maxPreviewWidth, maxPreviewHeight, mMaxSize);
            } catch (NullPointerException e) {
                e.printStackTrace();
                onBackPressed();
                Toast.makeText(mActivity, getResources().getString(R.string.error_camera), Toast.LENGTH_SHORT).show();
            }

            //Perform the rotation of the matrix image on the surface available
            Matrix matrix = new Matrix();

            //Surface dimensions saved in a RectF object
            RectF viewRect = new RectF(0, 0, width, height);
            //Image dimensions saved in a RectF object
            RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            //Align the centers of the 2 RectF
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());

            //Adapt image without stretching it
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            //Scale and rotate
            float scale = Math.max(
                    (float) height / mPreviewSize.getHeight(),
                    (float) width / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(270, centerX, centerY);

            mTextureView.setTransform(matrix);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method open the camera and connect it to the callback
     */
    private void connectCamera() {

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[0]) == PackageManager.PERMISSION_GRANTED) {
                try {

                    if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method starts the preview on the screen
     */
    private void startPreview() {

        //Initialize the surface
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            //Build the capture request and session assigning the surface
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mPreviewCaptureSession = session;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (Exception e) {
                                e.printStackTrace();
                                closeCamera();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startPreview");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.v(TAG, "IMAGE AVAILABLE");

            //If in calibration phase execute the CalibrationThread
            if (mIsCalibrating)
                mBackgroundHandler.post(new CalibrationThread(reader.acquireLatestImage()));
                //If not in calibration, robot is ready to move and the NavigationThread is started
            else {
                try {
                    navigationThread = new NavigationThread(reader.acquireLatestImage());
                    mBackgroundHandler.post(navigationThread);
                } catch (Exception e) {
                    e.printStackTrace();
                    finish();

                    Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_SHORT).show();
                    Log.v(TAG, "ERROR  in image available");
                }
            }
        }
    };

    private void takePicture() {

        k= 1;

        mCaptureState = STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, null);
            //Start capturing image, results are obtained in the mPreviewCaptureCallback
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
            finish();
            Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_SHORT).show();
            Log.v(TAG, "ERROR in take picture");
        }
    }

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        //Called when capture is completed
        private void process(CaptureResult captureResult) {
            switch (mCaptureState) {
                case STATE_PREVIEW:
                    Log.v(TAG, "STATE_PREVIEW---> stop the acquiring process");
                    // Do nothing
                    break;
                case STATE_WAIT_LOCK:
                    Log.v(TAG, "STATE_WAIT_LOCK--->continue with the acquiring process");
                    mCaptureState = STATE_PREVIEW;
                    Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        startCapturing();
                    }
                    break;
            }
        }
    };

    private void startCapturing() {
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (null != mPreviewCaptureSession) {
                mPreviewCaptureSession.close();
                mPreviewCaptureSession = null;
            }

            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }

    }

    /**
     * All the operations performed by camera are executed in a different Thread from the main Thread
     */
    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("AutoNavigationCamera");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //Handle the permission request of the camera
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {

            case (REQUEST_CAMERA_PERMISSION):
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Permission Granted");
                    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

                } else {
                    Log.v(TAG, "Permission NOT Granted");
                    showNegativeDialog(getResources().getString(R.string.perm_error_title),
                            getResources().getString(R.string.camera_perm_error));
                }
                break;

            default:
                break;
        }
    }

    //Show a dialog of error that will close the AutoNavigationActivity after ok is pressed
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

   /* ####### CALIBRATION & MOVEMENT METHODS ####### */

    /**
     * This class manage the entire calibration phase in a separated Thread
     */
    private class CalibrationThread implements Runnable {

        private final Image mImage;

        private CalibrationThread(Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            //Parse the image in bytes before obtaining a bitmap
            Log.v(TAG, "Calibration Thread RUNNING##############");
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            //Pass bytes data into a bitmap and scale it in order to display it completely on the screen
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 2;
            myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);

            //Transform Bitmap into a Mat
            mOriginal = new Mat(myBitmap.getWidth(), myBitmap.getHeight(), CvType.CV_8UC4);
            Utils.bitmapToMat(myBitmap, mOriginal);

            //Display the photo taken before
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTestImage.setImageBitmap(myBitmap);
                    mTextureView.setVisibility(View.INVISIBLE);
                    mTestImage.setVisibility(View.VISIBLE);
                    mTestImage.getTop();
                    mCalibrationInfo.setText(getResources().getString(R.string.info_target));
                }
            });

            /*
             * Handle the touch events guided by the text instructions displayed
             */
            mTestImage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    //Variable colorCounter handle the current color calibration
                    if (colorCounter > 2) {
                        colorCounter = 0;
                        touchedRegionRgba.release();
                        touchedRegionHsv.release();
                        //return to the preview of camera
                        mTestImage.setVisibility(View.INVISIBLE);
                        mTextureView.setVisibility(View.VISIBLE);
                        mTextureView.getTop();
                        mFabPictureCalib.setVisibility(View.GONE);
                        mFabMenu.show();
                        return false;
                    }

                    int cols = mOriginal.cols();
                    int rows = mOriginal.rows();


                    /*
                     * The touch event use coordinates of the screen. The image is reduced in order
                     * to be displayed on the screen, so we need to relate coordinates of the screen
                     * to those on the image
                     */

                    //Image should have two lateral black strips. Compute the dimension of one of these
                    int xOffset = (cols - mTestImage.getWidth()) / 2;
                    int yOffset = (rows - mTestImage.getHeight()) / 2;

                    //The dimension of the strip is an offset that is needed to be add at coordinates
                    int x = (int) event.getX() + xOffset;
                    int y = (int) event.getY() + yOffset;

                    /*
                     * Around the point touched identify a rect 8x8 and on that rect evaluate
                     * a mean value of color. To do this we need to be sure not to exit
                     * margins of the image
                     */
                    if (x <= 4)
                        x += 4;
                    else if (y <= 4)
                        y += 4;
                    else if (x + 4 >= cols)
                        x -= 4;
                    else if (y + 4 >= rows)
                        y -= 4;

                    if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

                    Rect touchedRect = new Rect();
                    touchedRect.x = x - 4;
                    touchedRect.y = y - 4;
                    touchedRect.width = x + 4 - touchedRect.x;
                    touchedRect.height = y + 4 - touchedRect.y;

                    //Defined the rect use submat to extract that region of image
                    touchedRegionRgba = mOriginal.submat(touchedRect);

                    //Convert color in HSV
                    touchedRegionHsv = new Mat();
                    Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV);

                    //Create new object to save color calibration
                    mDetector = new Calibration();

                    //Evaluate average HSV color of touched region (rect 8x8)
                    Scalar mBlobColorHsv = Core.sumElems(touchedRegionHsv);
                    int pointCount = touchedRect.width * touchedRect.height;
                    for (int i = 0; i < mBlobColorHsv.val.length; i++)
                        mBlobColorHsv.val[i] /= pointCount;

                    //Save bounds
                    mDetector.setHsvRange(mBlobColorHsv);

                    //UI feedback: show color selected to user with option to accept or repeat sampling
                    Scalar mBlobColorRgba = Calibration.hsvToRGBA(mBlobColorHsv);
                    int red = (int) mBlobColorRgba.val[0];
                    int green = (int) mBlobColorRgba.val[1];
                    int blue = (int) mBlobColorRgba.val[2];
                    String hex = String.format("#%02x%02x%02x", red, green, blue);
                    Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                            ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");
                    int cl = Color.parseColor(hex);
                    final GradientDrawable gd = new GradientDrawable();
                    gd.setColor(cl);
                    gd.setShape(GradientDrawable.OVAL);

                    //Dialog with color feedback
                    final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AutoNavigationActivity.this);
                    dialogBuilder.setTitle(getResources().getString(R.string.selected_color));
                    dialogBuilder.setIcon(gd);
                    dialogBuilder.setCancelable(false);
                    //Repeat sampling action
                    dialogBuilder.setNegativeButton(getString(R.string.repeat), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });

                    /*
                     * Accept color calibration and save it in the correct variables according to
                     * the current colorCounter value
                    */
                    dialogBuilder.setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                    SharedPreferences sharedpreferences = getApplicationContext().getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedpreferences.edit();

                                    switch (colorCounter) {

                                        //target
                                        case 0:
                                            editor.putString(ConstantApp.SHARED_TARGET_LOWER, mDetector.getLowerBound());
                                            Log.v(TAG, "TARGET_LOWER: " + mDetector.getLowerBound());
                                            editor.putString(ConstantApp.SHARED_TARGET_UPPER, mDetector.getUpperBound());
                                            Log.v(TAG, "TARGET_UPPER: " + mDetector.getUpperBound());
                                            editor.apply();
                                            colorCounter++;
                                            mCalibrationInfo.setText(getResources().getString(R.string.info_right));
                                            touchedRegionRgba.release();
                                            touchedRegionHsv.release();
                                            break;

                                        //right
                                        case 1:
                                            editor.putString(ConstantApp.SHARED_ROBOT_RIGHT_LOWER, mDetector.getLowerBound());
                                            Log.v(TAG, "RIGHT_LOWER: " + mDetector.getLowerBound());
                                            editor.putString(ConstantApp.SHARED_ROBOT_RIGHT_UPPER, mDetector.getUpperBound());
                                            Log.v(TAG, "RIGHT_UPPER: " + mDetector.getUpperBound());
                                            editor.apply();
                                            colorCounter++;
                                            mCalibrationInfo.setText(getResources().getString(R.string.info_left));
                                            touchedRegionRgba.release();
                                            touchedRegionHsv.release();
                                            break;

                                        //left
                                        case 2:
                                            editor.putString(ConstantApp.SHARED_ROBOT_LEFT_LOWER, mDetector.getLowerBound());
                                            Log.v(TAG, "LEFT_LOWER: " + mDetector.getLowerBound());
                                            editor.putString(ConstantApp.SHARED_ROBOT_LEFT_UPPER, mDetector.getUpperBound());
                                            Log.v(TAG, "LEFT_UPPER: " + mDetector.getUpperBound());
                                            editor.apply();

                                            //Obtain "height calibration"
                                            if (Utility.isTargetCalibrationDone(getApplicationContext()))
                                                Calibration.computeFocal(mOriginal, getApplicationContext());

                                            //When calibration is done
                                            mCalibrationInfo.setVisibility(View.GONE);
                                            colorCounter = 0;
                                            touchedRegionRgba.release();
                                            touchedRegionHsv.release();

                                            //Return to the preview of camera
                                            mTestImage.setVisibility(View.INVISIBLE);
                                            mTextureView.setVisibility(View.VISIBLE);
                                            mTextureView.getTop();
                                            mFabPictureCalib.setVisibility(View.GONE);
                                            mFabPictureCalib.setEnabled(true);
                                            mCalibrationNeedTitle.setVisibility(View.VISIBLE);
                                            mCalibrationNeedTitle.setVisibility(View.GONE);
                                            mFabMenu.show();
                                            mIsCalibrating = false;
                                            mIsCalibrated = true;
                                            break;

                                        default:
                                            break;
                                    }
                                }
                            });

                    dialogBuilder.show();
                    return false;
                }
            });
            //Close image
            mImage.close();
        }
    }

    private class NavigationThread implements Runnable {

        private final Image mImage;

        private NavigationThread(Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            //When stop button is pressed the thread does nothing ( stop == true )
            if (!stop) {

                mTestImage.setOnTouchListener(null);
                //Parse the image in bytes before obtaining a bitmap
                Log.v(TAG, "Navigation Thread RUNNING##############");
                ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);

                //Pass bytes data into a bitmap
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 2;
                myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
                mOriginal = new Mat(myBitmap.getWidth(), myBitmap.getHeight(), CvType.CV_8UC4);
                Utils.bitmapToMat(myBitmap, mOriginal);

                if (debug) {
                    //Show button next for display partial results of image processing
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mButtonNext.setVisibility(View.VISIBLE);
                        }
                    });
                }

                //Get a reference to the shared preferences
                SharedPreferences sharedpreferences = getApplicationContext().getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

                String[] leftUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_UPPER, null).split(":");
                String[] leftLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_LOWER, null).split(":");

                String[] rightUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_UPPER, null).split(":");
                String[] rightLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_LOWER, null).split(":");

                String[] targetUpper = sharedpreferences.getString(ConstantApp.SHARED_TARGET_UPPER, null).split(":");
                String[] targetLower = sharedpreferences.getString(ConstantApp.SHARED_TARGET_LOWER, null).split(":");

                //Retrieve from shared preferences focal value (scaling factor)
                String focalS = sharedpreferences.getString(ConstantApp.SHARED_FOCAL, null);
                double focal = -1;
                if (focalS != null)
                    focal = Double.valueOf(focalS);

                if (focal == -1) {
                    onBackPressed();
                    Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_SHORT).show();
                    Log.v(TAG, "ERROR Distance is -1");
                }

                //Retrieve from shared preferences lower and upper bounds
                Scalar lowTarget2 = null, upTarget2 = null;

                if (Double.valueOf(targetLower[3]) != -1) {

                    lowTarget2 = new Scalar(Double.valueOf(targetLower[3]), Double.valueOf(targetLower[1]), Double.valueOf(targetLower[2]));
                    upTarget2 = new Scalar(Double.valueOf(targetUpper[3]), Double.valueOf(targetUpper[1]), Double.valueOf(targetUpper[2]));
                }

                Scalar lowLeft = new Scalar(Double.valueOf(leftLower[0]), Double.valueOf(leftLower[1]), Double.valueOf(leftLower[2]));
                Scalar upLeft = new Scalar(Double.valueOf(leftUpper[0]), Double.valueOf(leftUpper[1]), Double.valueOf(leftUpper[2]));

                Scalar lowRight = new Scalar(Double.valueOf(rightLower[0]), Double.valueOf(rightLower[1]), Double.valueOf(rightLower[2]));
                Scalar upRight = new Scalar(Double.valueOf(rightUpper[0]), Double.valueOf(rightUpper[1]), Double.valueOf(rightUpper[2]));

                Scalar lowTarget = new Scalar(Double.valueOf(targetLower[0]), Double.valueOf(targetLower[1]), Double.valueOf(targetLower[2]));
                Scalar upTarget = new Scalar(Double.valueOf(targetUpper[0]), Double.valueOf(targetUpper[1]), Double.valueOf(targetUpper[2]));

                //Pass image in HSV
                caseHsv = new Mat();
                Imgproc.cvtColor(mOriginal, caseHsv, Imgproc.COLOR_RGB2HSV);

                //Filter color LEFT_MARKER, find contours and find centroid
                caseLeft = new Mat();
                Core.inRange(caseHsv, lowLeft, upLeft, caseLeft);
                List<MatOfPoint> contoursLeft = Navigation.findContours(caseLeft);
                centerLeft = Navigation.findCentroid(contoursLeft);

                //Filter color RIGHT_MARKER, find contours and find centroid
                caseRight = new Mat();
                Core.inRange(caseHsv, lowRight, upRight, caseRight);
                List<MatOfPoint> contoursRight = Navigation.findContours(caseRight);
                centerRight = Navigation.findCentroid(contoursRight);

                //Filter color TARGET_MARKER, find contours and find centroid
                caseTarget = new Mat();
                Core.inRange(caseHsv, lowTarget, upTarget, caseTarget);
                if (Double.valueOf(targetLower[3]) != -1) {
                    caseTarget2 = new Mat();
                    //Compute second TARGET_MASK, for second range of colors
                    Core.inRange(caseHsv, lowTarget2, upTarget2, caseTarget2);
                    //Merge the two mask in a singular one
                    Core.add(caseTarget, caseTarget2, caseTarget);
                }
                List<MatOfPoint> contoursTarget = Navigation.findContours(caseTarget);
                centerTarget = Navigation.findCentroid(contoursTarget);

                //Compute actual height from the target
                double height = Navigation.computeHeight(mOriginal, getApplicationContext());
                Log.v(TAG, "Height: " + height);

                if (height == -1) {
                    onBackPressed();
                    Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_SHORT).show();
                    Log.v(TAG, "ERROR Height is -1");
                }

                //Done with elaboration on input image, release
                mImage.close();

                //Begin the movement: control if centers are correctly acquired
                if (centerTarget != null && centerLeft != null && centerRight != null) {

                    //Compute MEAN point among CENTER_LEFT and CENTER_RIGHT
                    double meanX = (centerLeft.x + centerRight.x) / 2;
                    double meanY = (centerLeft.y + centerRight.y) / 2;
                    centerMean = new org.opencv.core.Point(meanX, meanY);

                    Log.v(TAG, "CenterLeft: " + centerLeft.x + "," + centerLeft.y);
                    Log.v(TAG, "CenterRight: " + centerRight.x + "," + centerRight.y);
                    Log.v(TAG, "The mean point is: " + centerMean.x + "," + centerMean.y);
                    Log.v(TAG, "centerTarget: " + centerTarget.x + "," + centerTarget.y);

                    /*
                     * L Movement
                     */
                    if (movementType == ConstantApp.L_MOVEMENT) {

                        /*
                         * If Y coordinates of robot and target are in bound (robot is aligned with target on Y axis), so
                         * start movements along X axis
                         */

                        if (Navigation.isInBound(centerMean, centerTarget, 5, height, focal)) {

                            //Call turnDirection method in order to obtain the next action
                            int action = Navigation.turnDirection(centerTarget, centerLeft, centerRight, true, focal, height);
                            switch (action) {

                                //Rotate LEFT
                                case 0:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                    Log.v(TAG, "Moving left on y");
                                    break;

                                //Rotate RIGHT
                                case 1:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                    Log.v(TAG, "Moving right on y");
                                    break;

                                //Is aligned (is facing the target or is turned to the wrong side)
                                case 2:
                                    //Method isWrongSide evaluate the condition, if true turn right, otherwise go forward
                                    if (Navigation.isWrongSide(centerTarget, centerRight, centerMean, true)) {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                        Log.v(TAG, "Turn right on y");

                                    } else {
                                        for (int i = 0; i < 2; i++) {
                                            try {
                                                Thread.sleep(300);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                            Log.v(TAG, "Moving forward on y");
                                        }
                                    }
                                    break;

                                default:
                                    break;
                            }

                        //When Y coordinates are not in bound (not similar), move the robot in the bound (align robot with target on Y axis)
                        } else {
                            //Compute a temporary target corresponding to the point where robot should rotate in the L movement
                            org.opencv.core.Point tempTarget = new org.opencv.core.Point(meanX, centerTarget.y);

                            //Call turnDirection method in order to obtain the next action respect to temporary target
                            int action = Navigation.turnDirection(tempTarget, centerLeft, centerRight, false, focal, height);
                            switch (action) {

                                //Rotate LEFT
                                case 0:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                    Log.v(TAG, "Moving left on x");
                                    break;

                                //Rotate RIGHT
                                case 1:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                    Log.v(TAG, "Moving right on x");
                                    break;

                                //Is aligned (is facing the temporary target or is turned to the wrong side)
                                case 2:
                                    //Method isWrongSide evaluate the condition, if true turn right, otherwise go forward
                                    if (Navigation.isWrongSide(tempTarget, centerRight, centerMean, false)) {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                        Log.v(TAG, "Turn right on x");

                                    } else {
                                        for (int i = 0; i < 2; i++) {
                                            try {
                                                Thread.sleep(300);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                            Log.v(TAG, "Moving forward on x");
                                        }

                                    }

                                    break;

                                default:
                                    break;
                            }
                        }
                    /*
                     * Direct Movement
                     */
                    } else {

                        //Compute offset as the actual scaling factor for the actual height of the device to target
                        double offset = (ConstantApp.KNOWN_WIDTH * focal) / height;
                        //Scaling factor from X pixel to one cm
                        double pixelToCm = offset / 3;

                        /*//Compute only once the initial distance in cm between CENTER_TARGET and MEAN point
                        if (distanceTM == null) {
                            distanceTM = Math.sqrt(Math.pow(centerTarget.x - centerMean.x, 2)
                                    + Math.pow(centerTarget.y - centerMean.y, 2));

                            Log.v(TAG, "distanceTM in pixel: " + distanceTM);

                            distanceTM = distanceTM / pixelToCm;
                            Log.v(TAG, "distanceTM in cm: " + distanceTM);
                        }*/

                        //Slope of line passing through CENTER_RIGHT and CENTER_LEFT
                        double m1 = (centerRight.y - centerLeft.y) / (centerRight.x - centerLeft.x);

                        //Slope of line passing through MEAN and CENTER_TARGET
                        double m2 = (centerMean.y - centerTarget.y) / (centerMean.x - centerTarget.x);
                        Log.v(TAG, "m1*m2: " + m1 * m2);

                        //Verify condition of perpendicularity, if true go straight else rotate
                        if (Navigation.isPerpendicular(m1, m2, focal, height, centerMean, centerTarget)) {



                            //Compute actual distance between CENTER_TARGET and MEAN point in cm
                            double newDistanceTM = Math.sqrt(Math.pow(centerTarget.x - centerMean.x, 2)
                                    + Math.pow(centerTarget.y - centerMean.y, 2));

                            Log.v("DIRECT", "newDistanceTM in pixel: " + newDistanceTM);

                            newDistanceTM = newDistanceTM / pixelToCm;
                            Log.v("DIRECT", "newDistanceTM in cm: " + newDistanceTM);

                            /*
                             * Compare actual distance with initial one, if it increase the robot is in the wrong
                             * direction and a rotation is done
                             */

                            if (distanceTM != null && newDistanceTM > distanceTM + 3) {
                                for (int i = 0; i < 6; i++){
                                    try {
                                        Thread.sleep(400);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                    Log.v("DIRECT", "getting far away turn");
                                }
                            } else {
                                for (int i = 0; i < 2; i++) {
                                    try {
                                        Thread.sleep(300);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                    Log.v("DIRECT", "Go Ahead");
                                }
                            }
                            //TODO: aggiornare commenti
                            //aggiorna la distanza
                            distanceTM = newDistanceTM;

                        /*
                         * If robot isn't perpendicular respect to target rotate it in the direction of
                         * the nearest robot marker
                         */
                        } else {

                            double distanceTR = Math.sqrt(Math.pow(centerTarget.x - centerRight.x, 2)
                                    + Math.pow(centerTarget.y - centerRight.y, 2));
                            double distanceTL = Math.sqrt(Math.pow(centerTarget.x - centerLeft.x, 2)
                                    + Math.pow(centerTarget.y - centerLeft.y, 2));

                            if (distanceTL >= distanceTR) {
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                Log.v("DIRECT", "Turn right");
                            } else if (distanceTL < distanceTR) {
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                Log.v("DIRECT", "Turn left");
                            }
                        }
                    }

                //If only the target marker is not visible the robot is arrived, set UI initial layout
                } else if (centerTarget == null) {
                    stop = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mFabStop.setVisibility(View.GONE);
                            mFabMenu.show();
                            mCalibrationInfo.setVisibility(View.GONE);

                            //Return to the preview of camera
                            mTestImage.setVisibility(View.GONE);
                            mTextureView.setVisibility(View.VISIBLE);
                            mTextureView.getTop();
                        }
                    });

                    Toast.makeText(mActivity, getResources().getString(R.string.arrived_target_bound), Toast.LENGTH_LONG).show();

                //If one of the two marker of the robot is not visible stop the navigation
                } else {

                    stop = true;
                    String message = null;
                    if (contoursLeft == null)
                        message = "LEFT is Null";
                    else if (centerRight == null)
                        message = "RIGHT is null";

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mFabStop.setVisibility(View.GONE);
                            mFabMenu.show();
                            mCalibrationInfo.setVisibility(View.GONE);

                            //Return to the preview of camera
                            mTestImage.setVisibility(View.GONE);
                            mTextureView.setVisibility(View.VISIBLE);
                            mTextureView.getTop();
                        }
                    });

                    if (message != null)
                        Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
                    Toast.makeText(mActivity, getResources().getString(R.string.null_center), Toast.LENGTH_LONG).show();
                }

                Log.v(TAG, "########################################################");

                try {
                    Thread.sleep(600);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //Continue navigation taking a new picture
                takePicture();
            }

        }
    }
}