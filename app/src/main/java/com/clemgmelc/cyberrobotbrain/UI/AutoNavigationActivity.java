package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.Computation.Calibration;
import com.clemgmelc.cyberrobotbrain.Computation.Navigation;
import com.clemgmelc.cyberrobotbrain.Data.BluetoothLeService;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;
import com.clemgmelc.cyberrobotbrain.Util.Utility;

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
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize, mMaxSize;
    private Size mImageSize;
    private ImageReader mImageReader;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private FloatingActionButton mFabStartNavigation, mFabCalibration, mFabStop;
    private Button mButtonNext, mButtonRecalibrate;
    private ImageView mTestImage;
    private Activity mActivity;
    private int k = 1, colorCounter;
    private Mat mOriginal, caseHsv, caseLeft, caseRight, caseTarget, touchedRegionHsv, touchedRegionRgba;
    private TextView mCalibrationInfo;
    private Bitmap myBitmap;
    private Calibration mDetector;
    private boolean mIsCalibrating = false;
    private org.opencv.core.Point centerLeft, centerRight, centerTarget, centerMean, upperTarget, lowerTarget;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothGattService mMovementGattService;
    private BluetoothGattCharacteristic mMovementCharacteristic;
    private NavigationThread navigationThread;
    private boolean stop = false, isYAligned = false, isFacing = false, isXAligned = false;
    private int movementType;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.auto_navigation_main);
        mTestImage = (ImageView) findViewById(R.id.imageViewTest);
        mCalibrationInfo = (TextView) findViewById(R.id.calibrationInfo);
        mFabCalibration = (FloatingActionButton) findViewById(R.id.fabCalibration);
        mButtonRecalibrate = (Button) findViewById(R.id.recalibrate);
        mFabStop = (FloatingActionButton) findViewById(R.id.fabStop);

        mDeviceAddress = getIntent().getStringExtra(ConstantApp.DEVICE_ADDRESS);

        mActivity = this;

        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            Log.d(TAG, "opencv not init");
        }
        //mButtonHide = (Button) findViewById(R.id.hide);
        //mButtonHide.setEnabled(false);
        mButtonNext = (Button) findViewById(R.id.next);
        //createImageFolder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[0]) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[1]) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, PERMISSIONS_CAMERA, REQUEST_CAMERA_PERMISSION);
            }
        }

        mTextureView = (TextureView) findViewById(R.id.textureView);
        mFabStartNavigation = (FloatingActionButton) findViewById(R.id.fabAutoNav);
        if (!Utility.isCalibrationDone(getApplicationContext()))
            mFabStartNavigation.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), R.color.googleRed)));

        mFabStartNavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "FAB CLICKED");
                mFabStartNavigation.setEnabled(false);

                //check if the calibration is done
                boolean isCalibrationDone = Utility.isCalibrationDone(getApplicationContext());
                if (!isCalibrationDone) {

                    //TODO: popup
                    mIsCalibrating = true;
                    mCalibrationInfo.setVisibility(View.VISIBLE);
                    mFabStartNavigation.setVisibility(View.GONE);
                    mFabCalibration.setVisibility(View.VISIBLE);
                    mButtonRecalibrate.setVisibility(View.GONE);
                    colorCounter = 0;

                } else {


                    AlertDialog.Builder builder = new AlertDialog.Builder(AutoNavigationActivity.this);
                    builder.setTitle(getResources().getString(R.string.movement_type_title));
                    builder.setItems(getResources().getStringArray(R.array.movement_type), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // the user clicked on colors[which]
                            if (which == 0)
                                movementType = ConstantApp.L_MOVEMENT;
                            else
                                movementType = ConstantApp.DIRECT_MOVEMENT;

                            mButtonRecalibrate.setVisibility(View.GONE);
                            mFabStartNavigation.setVisibility(View.GONE);
                            mFabStop.setVisibility(View.VISIBLE);
                            stop = false;
                            takePicture();
                            mFabStartNavigation.setEnabled(true);
                            isYAligned = false;
                            isXAligned = false;
                            isFacing = false;
                        }

                    });
                    builder.setCancelable(false);
                    builder.show();


                }
            }
        });

        mFabStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mButtonRecalibrate.setVisibility(View.VISIBLE);
                mFabStop.setVisibility(View.GONE);
                mFabStartNavigation.setVisibility(View.VISIBLE);
                mCalibrationInfo.setVisibility(View.GONE);

                //return to the "classic" camera view
                mTestImage.setVisibility(View.INVISIBLE);
                mTextureView.setVisibility(View.VISIBLE);
                mTextureView.getTop();
                mFabStartNavigation.setEnabled(true);

                stop = true;

            }
        });

        mButtonRecalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsCalibrating = true;
                mCalibrationInfo.setVisibility(View.VISIBLE);
                mFabStartNavigation.setVisibility(View.GONE);
                mFabCalibration.setVisibility(View.VISIBLE);
                colorCounter = 0;
                mButtonRecalibrate.setVisibility(View.GONE);
            }
        });

        mFabCalibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFabCalibration.setEnabled(false);
                takePicture();
            }
        });

        mButtonNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Log.v(TAG, "K: " + k);

                switch (k) {

                    case 1:
                        Log.d(TAG, "HSV MASK");
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText("HSV MASK");
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

                        /*org.opencv.core.Point center = findCentroid(caseLeft);
                        Imgproc.circle(caseLeft,center,3,new Scalar(0,255,0),3);*/
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText("CASE LEFT");

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

                       /* org.opencv.core.Point center2 = findCentroid(caseTarget);
                        Imgproc.circle(caseTarget,center2,3,new Scalar(0,255,0),3);*/
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText("CASE TARGET");

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

                       /* org.opencv.core.Point center3 = findCentroid(caseRight);
                        Imgproc.circle(caseRight,center3,3,new Scalar(0,255,0),3);*/
                        mCalibrationInfo.setVisibility(View.VISIBLE);
                        mCalibrationInfo.setText("CASE RIGHT");

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
    protected void onPause() {

        closeCamera();
        //TODO: scollegare ugo
        stopBackgroundThread();
        mTextureView.setSurfaceTextureListener(null);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if (hasFocas) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }


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

    private void setupCamera(int width, int height) {

        CameraCharacteristics cameraCharacteristics = null;

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {

                    mCameraId = cameraId;
                    break;
                }
            }

            StreamConfigurationMap mMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] mSizeList = null;

            if (mMap != null) {
                mSizeList = mMap.getOutputSizes(ImageFormat.JPEG);
            }

            int maxResIndex = Utility.maxRes(mSizeList);
            if (maxResIndex != -1) {
                mMaxSize = mSizeList[maxResIndex];
            }

            mImageReader = ImageReader.newInstance(mMaxSize.getWidth(), mMaxSize.getHeight(), ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            Point displaySize = new Point();
            this.getWindowManager().getDefaultDisplay().getSize(displaySize);

            int maxPreviewWidth = displaySize.y;
            int maxPreviewHeight = displaySize.x;

            mPreviewSize = Utility.chooseOptimalSize(mMap.getOutputSizes(SurfaceTexture.class), width, height, maxPreviewWidth, maxPreviewHeight, mMaxSize);
            //mImageSize = chooseOptimalSize(mMap.getOutputSizes(ImageFormat.JPEG), width, height, maxPreviewWidth, maxPreviewHeight, mMaxSize);

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, width, height);
            RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
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

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {

            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[0]) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[1]) == PackageManager.PERMISSION_GRANTED) {

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

    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigured: startPreview");
                            mPreviewCaptureSession = session;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (Exception e) {
                                //if the camera is closed for some reason come back to main activity
                                e.printStackTrace();
                                closeCamera();
                                //onBackPressed();
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


    private void startCapturing() {
        try {

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {


        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.v(TAG, "IMAGE AVAILABLE SAVE IT");
            if (mIsCalibrating)
                mBackgroundHandler.post(new CalibrationThread(reader.acquireLatestImage()));
            else {
                navigationThread = new NavigationThread(reader.acquireLatestImage());
                mBackgroundHandler.post(navigationThread);
            }

        }
    };


    private class NavigationThread implements Runnable {
        //scope of this class is to elaborate the image in background with openCv

        private final Image mImage;

        private NavigationThread(Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            if (!stop) {

                mTestImage.setOnTouchListener(null);

                Log.v(TAG, "Navigation Thread RUNNING##############");
                ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                //pass datas into a bitmap
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 2;
                myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);


                //TODO: if not working, puts 3.
                mOriginal = new Mat(myBitmap.getWidth(), myBitmap.getHeight(), CvType.CV_8UC4);
                Utils.bitmapToMat(myBitmap, mOriginal);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //mTestImage.setImageBitmap(myBitmap);
                        //mTextureView.setVisibility(View.INVISIBLE);
                        //mTestImage.setVisibility(View.VISIBLE);
                        //mTestImage.getTop();
                        mButtonNext.setVisibility(View.VISIBLE);

                    }
                });

                //get a reference to the shared preferences
                SharedPreferences sharedpreferences = getApplicationContext().getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

                String[] leftUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_UPPER, null).split(":");
                String[] leftLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_LOWER, null).split(":");

                String[] rightUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_UPPER, null).split(":");
                String[] rightLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_LOWER, null).split(":");

                String[] targetUpper = sharedpreferences.getString(ConstantApp.SHARED_TARGET_UPPER, null).split(":");
                String[] targetLower = sharedpreferences.getString(ConstantApp.SHARED_TARGET_LOWER, null).split(":");


                for (int i = 0; i < 3; i++) {
                    if (Double.valueOf(leftUpper[i]) >= 255)
                        leftUpper[i] = "255";
                    else if (Double.valueOf(rightUpper[i]) >= 255)
                        leftUpper[i] = "255";
                    else if (Double.valueOf(targetUpper[i]) >= 255)
                        leftUpper[i] = "255";
                }

               /* Log.v(TAG, "leftUpper: " + leftUpper[0] + " " + leftUpper[1] + " " + leftUpper[2]);
                Log.v(TAG, "leftLower: " + leftLower[0] + " " + leftLower[1] + " " + leftLower[2]);

                Log.v(TAG, "rightUpper: " + rightUpper[0] + " " + rightUpper[1] + " " + rightUpper[2]);
                Log.v(TAG, "rightLower: " + rightLower[0] + " " + rightLower[1] + " " + rightLower[2]);

                Log.v(TAG, "targetUpper: " + targetUpper[0] + " " + targetUpper[1] + " " + targetUpper[2]);
                Log.v(TAG, "targetLower: " + targetLower[0] + " " + targetLower[1] + " " + targetLower[2]);
*/
                Scalar lowLeft = new Scalar(Double.valueOf(leftLower[0]), Double.valueOf(leftLower[1]), Double.valueOf(leftLower[2]));
                Scalar upLeft = new Scalar(Double.valueOf(leftUpper[0]), Double.valueOf(leftUpper[1]), Double.valueOf(leftUpper[2]));

                Scalar lowRight = new Scalar(Double.valueOf(rightLower[0]), Double.valueOf(rightLower[1]), Double.valueOf(rightLower[2]));
                Scalar upRight = new Scalar(Double.valueOf(rightUpper[0]), Double.valueOf(rightUpper[1]), Double.valueOf(rightUpper[2]));

                Scalar lowTarget = new Scalar(Double.valueOf(targetLower[0]), Double.valueOf(targetLower[1]), Double.valueOf(targetLower[2]));
                Scalar upTarget = new Scalar(Double.valueOf(targetUpper[0]), Double.valueOf(targetUpper[1]), Double.valueOf(targetUpper[2]));

                //pass image in HSV
                caseHsv = new Mat();
                Imgproc.cvtColor(mOriginal, caseHsv, Imgproc.COLOR_RGB2HSV_FULL);

                //filter color left
                caseLeft = new Mat();
                Core.inRange(caseHsv, lowLeft, upLeft, caseLeft);
                //try {
                List<MatOfPoint> contoursLeft = Navigation.findContours(caseLeft);
                centerLeft = Navigation.findCentroid(contoursLeft);
                //Imgproc.circle(caseLeft, centerLeft, 3, new Scalar(0, 255, 0), 3);

/*                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    mImage.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                            Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_LONG).show();
                        }
                    });
                }*/

                //filter color right
                caseRight = new Mat();
                Core.inRange(caseHsv, lowRight, upRight, caseRight);
                // try {
                List<MatOfPoint> contoursRight = Navigation.findContours(caseRight);
                centerRight = Navigation.findCentroid(contoursRight);
                //Imgproc.circle(caseRight, centerRight, 3, new Scalar(0, 255, 0), 3);

                /*} catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onBackPressed();
                            Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_LONG).show();
                        }
                    });
                }*/


                //filter color target
                caseTarget = new Mat();
                Core.inRange(caseHsv, lowTarget, upTarget, caseTarget);
                // try {

                List<MatOfPoint> contoursTarget = Navigation.findContours(caseTarget);
                centerTarget = Navigation.findCentroid(contoursTarget);

                //one movement is about 100 pixel, so we take a bound of 200 pixels
                //TODO: controllare se con i negativi va bene
                if (centerTarget != null) {
                    upperTarget = new org.opencv.core.Point(centerTarget.x, centerTarget.y + 75);
                    lowerTarget = new org.opencv.core.Point(centerTarget.x, centerTarget.y - 75);
                }


                //Imgproc.circle(caseTarget, centerTarget, 3, new Scalar(0, 255, 0), 3);

                /*} catch (Exception e) {
                    e.printStackTrace();
                    mImage.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                            Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_LONG).show();
                        }
                    });
                }*/

                mImage.close();


                //begin the movement stuff
                if (centerTarget != null && centerLeft != null && centerRight != null) {

                    double meanX = (centerLeft.x + centerRight.x) / 2;
                    double meanY = (centerLeft.y + centerRight.y) / 2;
                    centerMean = new org.opencv.core.Point(meanX, meanY);
                    Log.v(TAG, "CenterLeft: " + centerLeft.x + "," + centerLeft.y);
                    Log.v(TAG, "CenterRight: " + centerRight.x + "," + centerRight.y);
                    Log.v(TAG, "The mean point is: " + meanX + "," + meanY);

                    Log.v(TAG, "centerTarget: " + centerTarget.x + "," + centerTarget.y);

                    //L Movement
                    if (movementType == ConstantApp.L_MOVEMENT) {


                        if (Navigation.isInBound(true, centerMean, centerTarget, 200)) {

                            int action = Navigation.turnDirection(centerTarget, centerLeft, centerRight, true);
                            switch (action) {

                                case 0:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                    Log.v(TAG, "Moving left on y");
                                    break;

                                case 1:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                    Log.v(TAG, "Moving right on y");
                                    break;

                                case 2:
                                    if (Navigation.isWrongSide(centerTarget, centerRight, centerMean, true)) {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                        Log.v(TAG, "Turn right on y");

                                    } else {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                        Log.v(TAG, "Moving forward on y");
                                    }
                                    break;

                                default:
                                    break;
                            }

                        } else {
                            org.opencv.core.Point tempTarget = new org.opencv.core.Point(meanX, centerTarget.y);

                            int action = Navigation.turnDirection(tempTarget, centerLeft, centerRight, false);
                            switch (action) {

                                case 0:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                    Log.v(TAG, "Moving left on x");
                                    break;

                                case 1:
                                    mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                    Log.v(TAG, "Moving right on x");
                                    break;

                                case 2:
                                    if (Navigation.isWrongSide(tempTarget, centerRight, centerMean, false)) {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                        Log.v(TAG, "Turn right on x");

                                    } else {
                                        mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                                        Log.v(TAG, "Moving forward on x");
                                    }

                                    break;

                                default:
                                    break;
                            }
                        }

                        //direct movement
                    } else {

                        double m1;
                        if (centerRight.x > centerLeft.x) {
                            m1 = (centerRight.y - centerLeft.y) / (centerRight.x - centerLeft.x);
                        } else if (centerRight.x < centerLeft.x) {
                            m1 = (centerLeft.y - centerRight.y) / (centerLeft.x - centerRight.x);
                        } else {
                            m1 = Double.MAX_VALUE;
                        }

                        double m2;
                        if (centerMean.x > centerTarget.x) {
                            m2 = (centerMean.y - centerTarget.y) / (centerMean.x - centerTarget.x);
                        } else if (centerMean.x < centerTarget.x) {
                            m2 = (centerTarget.y - centerMean.y) / (centerTarget.x - centerMean.x);
                        } else {
                            m2 = Double.MAX_VALUE;
                        }

                        Log.v(TAG, "m1*m2: " + m1*m2);

                        if (m1 != Double.MAX_VALUE && m2 != Double.MAX_VALUE && m1*m2 <= -0.5 && m1*m2 >= -1.5) {
                            mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.forward);
                            Log.v(TAG, "Go Ahead");
                        } else {
                            double distanceTR = Math.sqrt(Math.pow(centerTarget.x - centerRight.x, 2)
                                    + Math.pow(centerTarget.y - centerRight.y, 2));
                            double distanceTL = Math.sqrt(Math.pow(centerTarget.x - centerLeft.x, 2)
                                    + Math.pow(centerTarget.y - centerLeft.y, 2));

                            if (distanceTL >= distanceTR) {
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.right);
                                Log.v(TAG, "Turn righ");
                            } else if (distanceTL < distanceTR) {
                                mBluetoothLeService.writeCharacteristic(mMovementCharacteristic, ConstantApp.left);
                                Log.v(TAG, "Turn left");
                            }
                        }


                    }

                    try {
                        Thread.sleep(400);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    takePicture();


                } /*else if ()  {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mButtonRecalibrate.setVisibility(View.VISIBLE);
                            mFabStop.setVisibility(View.GONE);
                            mFabStartNavigation.setVisibility(View.VISIBLE);
                            mCalibrationInfo.setVisibility(View.GONE);

                            //return to the "classic" camera view
                            mTestImage.setVisibility(View.INVISIBLE);
                            mTextureView.setVisibility(View.VISIBLE);
                            mTextureView.getTop();
                            mFabStartNavigation.setEnabled(true);
                            myBitmap = null;
                        }
                    });

                    Toast.makeText(mActivity, getResources().getString(R.string.arrived_target_bound), Toast.LENGTH_LONG).show();
                }  */ else if (centerTarget == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mButtonRecalibrate.setVisibility(View.VISIBLE);
                            mFabStop.setVisibility(View.GONE);
                            mFabStartNavigation.setVisibility(View.VISIBLE);
                            mCalibrationInfo.setVisibility(View.GONE);

                            //return to the "classic" camera view
                            mTestImage.setVisibility(View.GONE);
                            mTextureView.setVisibility(View.VISIBLE);
                            mTextureView.getTop();
                            mFabStartNavigation.setEnabled(true);
                            //myBitmap = null;
                        }
                    });

                    Toast.makeText(mActivity, getResources().getString(R.string.target_null_arrived), Toast.LENGTH_LONG).show();
                } else {
                    String message = null;
                    if (contoursLeft == null)
                        message = "LEFT is Null";
                    else if (centerRight == null)
                        message = "RIGHT is null";

                    //centerTarget != null && centerLeft != null && centerRight != null
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mButtonRecalibrate.setVisibility(View.VISIBLE);
                            mFabStop.setVisibility(View.GONE);
                            mFabStartNavigation.setVisibility(View.VISIBLE);
                            mCalibrationInfo.setVisibility(View.GONE);

                            //return to the "classic" camera view
                            mTestImage.setVisibility(View.GONE);
                            mTextureView.setVisibility(View.VISIBLE);
                            mTextureView.getTop();
                            mFabStartNavigation.setEnabled(true);
                            //myBitmap = null;
                        }
                    });
                    Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
                    Toast.makeText(mActivity, getResources().getString(R.string.null_center), Toast.LENGTH_LONG).show();
                }
            }

        }
    }


    private class CalibrationThread implements Runnable {
        //scope of this class is to elaborate the image in background with openCv

        private final Image mImage;

        private CalibrationThread(Image image) {
            mImage = image;
        }

        @Override
        public void run() {


            Log.v(TAG, "Calibration Thread RUNNING##############");
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            //pass datas into a bitmap
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 2;
            myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);

            //TODO: if not working, puts 3.
            mOriginal = new Mat(myBitmap.getWidth(), myBitmap.getHeight(), CvType.CV_8UC4);
            Utils.bitmapToMat(myBitmap, mOriginal);

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

            mTestImage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    if (colorCounter > 2) {
                        colorCounter = 0;
                        touchedRegionRgba.release();
                        touchedRegionHsv.release();
                        //return to the "classic" camera view
                        mTestImage.setVisibility(View.INVISIBLE);
                        mTextureView.setVisibility(View.VISIBLE);
                        mTextureView.getTop();
                        mFabCalibration.setVisibility(View.GONE);
                        mFabStartNavigation.setVisibility(View.VISIBLE);
                        mButtonRecalibrate.setVisibility(View.VISIBLE);
                        mFabStartNavigation.setEnabled(true);
                        return false;
                    }

                    int cols = mOriginal.cols();
                    int rows = mOriginal.rows();

                    int xOffset = (mTextureView.getWidth() - cols) / 2;
                    int yOffset = (mTextureView.getHeight() - rows) / 2;

                    int x = (int) event.getX() - xOffset;
                    int y = (int) event.getY() - yOffset;

                    Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

                    if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

                    Rect touchedRect = new Rect();

                    touchedRect.x = (x > 4) ? x - 4 : 0;
                    touchedRect.y = (y > 4) ? y - 4 : 0;

                    touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
                    touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

                    touchedRegionRgba = mOriginal.submat(touchedRect);

                    touchedRegionHsv = new Mat();
                    Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

                    mDetector = new Calibration();
                    Mat mSpectrum = new Mat();
                    Scalar mBlobColorRgba = new Scalar(255);
                    Scalar mBlobColorHsv = new Scalar(255);
                    org.opencv.core.Size SPECTRUM_SIZE = new org.opencv.core.Size(200, 64);
                    final Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);

                    // Calculate average color of touched region
                    mBlobColorHsv = Core.sumElems(touchedRegionHsv);
                    int pointCount = touchedRect.width * touchedRect.height;
                    for (int i = 0; i < mBlobColorHsv.val.length; i++)
                        mBlobColorHsv.val[i] /= pointCount;

                    mBlobColorRgba = Calibration.hsvToRGBA(mBlobColorHsv);
                    int red = (int) mBlobColorRgba.val[0];
                    int green = (int) mBlobColorRgba.val[1];
                    int blue = (int) mBlobColorRgba.val[2];
                    int alpha = (int) (int) mBlobColorRgba.val[3];

                    String hex = String.format("#%02x%02x%02x%02x", red, green, blue, alpha);

                    Log.v(TAG, "COLOR CLICKED: " + hex);

                    Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                            ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

                    mDetector.setHsvRange(mBlobColorHsv);

                    //Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

                    final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AutoNavigationActivity.this);
                    dialogBuilder.setTitle("Selected Color");
                    dialogBuilder.setMessage("The selected color is " + hex);
                    dialogBuilder.setCancelable(false);
                    dialogBuilder.setNegativeButton(android.R.string.ok,
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
                                            editor.commit();
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
                                            editor.commit();
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
                                            editor.commit();
                                            //calibration is done
                                            mCalibrationInfo.setVisibility(View.GONE);
                                            colorCounter = 0;
                                            touchedRegionRgba.release();
                                            touchedRegionHsv.release();
                                            //return to the "classic" camera view
                                            mTestImage.setVisibility(View.INVISIBLE);
                                            mTextureView.setVisibility(View.VISIBLE);
                                            mTextureView.getTop();
                                            mFabCalibration.setVisibility(View.GONE);
                                            mFabStartNavigation.setVisibility(View.VISIBLE);
                                            mButtonRecalibrate.setVisibility(View.VISIBLE);
                                            mFabStartNavigation.setEnabled(true);
                                            mFabStartNavigation.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), R.color.googleGreen)));
                                            mIsCalibrating = false;
                                            mFabCalibration.setEnabled(true);
                                            break;

                                        default:
                                            break;


                                    }
                                }
                            });
                    dialogBuilder.show();
                    //false because we need only a single touch event
                    return false;
                }
            });

            mImage.close();


        }
    }


    private void takePicture() {

        mFabCalibration.setVisibility(View.GONE);


        mCaptureState = STATE_WAIT_LOCK;

        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, null);
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
            finish();
            Toast.makeText(mActivity, getResources().getString(R.string.error_occured_camera), Toast.LENGTH_SHORT).show();
        }
    }

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult captureResult) {
            switch (mCaptureState) {
                case STATE_PREVIEW:
                    Log.v(TAG, "STATE_PREVIEW---> stop the process");
                    // Do nothing
                    break;
                case STATE_WAIT_LOCK:
                    Log.v(TAG, "STATE_WAIT_LOCK--->continue with process");
                    mCaptureState = STATE_PREVIEW;
                    Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Toast.makeText(getApplicationContext(), "AF Locked!", Toast.LENGTH_SHORT).show();
                        startCapturing();
                    }
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };


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


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {

            case (REQUEST_CAMERA_PERMISSION):
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {

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

    //show a dialog of error that will close the scan activity after ok is pressed
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
}