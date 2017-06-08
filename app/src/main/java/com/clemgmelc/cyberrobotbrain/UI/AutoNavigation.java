package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
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

import com.clemgmelc.cyberrobotbrain.Data.ColorBlobDetector;
import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AutoNavigation extends AppCompatActivity {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigation.class.getSimpleName();
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
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
    private Size mMaxSize;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private FloatingActionButton fab;
    private File mImageFolder;
    private String mImageFileName;
    private Button mButtonHide;
    private Button mButtonNext;
    private ImageView mTestImage;
    private Activity mActivity;
    private int k = 1;
    private Mat mOriginal, caseHsv, caseRed, caseBlue, caseGreen;
    private TextView mCalibrationInfo;
    private FloatingActionButton mFabCalibration;

    private Bitmap myBitmap;
    private ColorBlobDetector mDetector;
    private int colorCounter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.auto_navigation_main);
        mTestImage = (ImageView) findViewById(R.id.imageViewTest);
        mCalibrationInfo = (TextView) findViewById(R.id.calibrationInfo);
        mFabCalibration = (FloatingActionButton) findViewById(R.id.fabCalibration);

        mActivity = this;

        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
            Log.d(TAG, "opencv not init");
        }
        mButtonHide = (Button) findViewById(R.id.hide);
        //mButtonHide.setEnabled(false);
        mButtonNext = (Button) findViewById(R.id.next);
        //mButtonNext.setEnabled(false);
        createImageFolder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[0]) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, PERMISSIONS_CAMERA[1]) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, PERMISSIONS_CAMERA, REQUEST_CAMERA_PERMISSION);
            }
        }

        mTextureView = (TextureView) findViewById(R.id.textureView);
        fab = (FloatingActionButton) findViewById(R.id.fabAutoNav);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "FAB CLICKED");
                fab.setEnabled(false);

                //check if the calibration is done
                boolean isCalibrationDone = isCalibrationDone(getApplicationContext());
                if (!isCalibrationDone) {

                    //TODO: popup
                    mCalibrationInfo.setVisibility(View.VISIBLE);
                    fab.setVisibility(View.GONE);
                    mFabCalibration.setVisibility(View.VISIBLE);
                    colorCounter = 0;

                } else
                    //takePicture();
                    Toast.makeText(mActivity, "Calibration Done", Toast.LENGTH_SHORT).show();
            }
        });

        mFabCalibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

/*        mButtonNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (k) {
                    case 1:
                        Log.d(TAG, "case = 1");
                        Utils.matToBitmap(mOriginal, myBitmap);
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
                        Log.d(TAG, "case = 2");
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

                    case 3:
                        Log.d(TAG, "last case");
                        Utils.matToBitmap(caseRed, myBitmap);
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
                        Log.d(TAG, "last case");
                        Utils.matToBitmap(caseGreen, myBitmap);
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

                    case 5:
                        Log.d(TAG, "last case");
                        Utils.matToBitmap(caseBlue, myBitmap);
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
        });*/
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
    }


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

    private boolean isCalibrationDone(Context context) {

        boolean isCalibrationDone = false;

        //get a reference to the shared preferences
        SharedPreferences sharedpreferences = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

        String left_upper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_UPPER, null);
        String left_lower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_LOWER, null);
        String right_upper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_UPPER, null);
        String right_lower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_LOWER, null);
        String target_upper = sharedpreferences.getString(ConstantApp.SHARED_TARGET_UPPER, null);
        String target_lower = sharedpreferences.getString(ConstantApp.SHARED_TARGET_LOWER, null);

        if (left_upper != null && left_lower != null && right_upper != null && right_lower != null
                && target_upper != null && target_lower != null)
            isCalibrationDone = true;

        return isCalibrationDone;
        /*SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString("user_type", personalInfo.getType());
        if (personalInfo.getType().equals(ConstantApp.TYPE_S))
            editor.putBoolean("user_cc", personalInfo.getHasCreditCard());
        Log.v(ConstantApp.TAGLOG, "put in shared: " + personalInfo.getType());
        editor.commit();*/


        /*
             SharedPreferences sharedpreferences = context.getSharedPreferences("tt_shared", Context.MODE_PRIVATE);

        return sharedpreferences.getBoolean("user_cc", true);

         */
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

            int maxResIndex = maxRes(mSizeList);
            if (maxResIndex != -1) {
                mMaxSize = mSizeList[maxResIndex];
            }

            mImageReader = ImageReader.newInstance(mMaxSize.getWidth(), mMaxSize.getHeight(), ImageFormat.JPEG, 3);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            Point displaySize = new Point();
            this.getWindowManager().getDefaultDisplay().getSize(displaySize);

            int maxPreviewWidth = displaySize.y;
            int maxPreviewHeight = displaySize.x;

            mPreviewSize = chooseOptimalSize(mMap.getOutputSizes(SurfaceTexture.class), width, height, maxPreviewWidth, maxPreviewHeight, mMaxSize);
            mImageSize = chooseOptimalSize(mMap.getOutputSizes(ImageFormat.JPEG), width, height, maxPreviewWidth, maxPreviewHeight, mMaxSize);

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


            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            try {
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };

            //mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {


                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.v(TAG, "IMAGE AVAILABLE SAVE IT");
                    mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));

                }
            };

    private class ImageSaver implements Runnable {
        //scope of this class is to elaborate the image in background with openCv

        private final Image mImage;

        private ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {



            Log.v(TAG, "ImageSaver RUNNING##############");
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
                    mCalibrationInfo.setText("Schicia el sercion in tel target");



                }
            });

            mTestImage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    if (colorCounter > 2)
                        //TODO: uscire dalla calibration e tornare in setting
                        return false;
                    int cols = mOriginal.cols();
                    int rows = mOriginal.rows();

                    int xOffset = (mTextureView.getWidth() - cols) / 2;
                    int yOffset = (mTextureView.getHeight() - rows) / 2;

                    int x = (int)event.getX() - xOffset;
                    int y = (int)event.getY() - yOffset;

                    Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

                    if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

                    Rect touchedRect = new Rect();

                    touchedRect.x = (x>4) ? x-4 : 0;
                    touchedRect.y = (y>4) ? y-4 : 0;

                    touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
                    touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

                    Mat touchedRegionRgba = mOriginal.submat(touchedRect);

                    Mat touchedRegionHsv = new Mat();
                    Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

                    mDetector = new ColorBlobDetector();
                    Mat mSpectrum = new Mat();
                    Scalar mBlobColorRgba = new Scalar(255);
                    Scalar mBlobColorHsv = new Scalar(255);
                    org.opencv.core.Size SPECTRUM_SIZE = new org.opencv.core.Size(200, 64);
                    Scalar CONTOUR_COLOR = new Scalar(255,0,0,255);

                    // Calculate average color of touched region
                    mBlobColorHsv = Core.sumElems(touchedRegionHsv);
                    int pointCount = touchedRect.width*touchedRect.height;
                    for (int i = 0; i < mBlobColorHsv.val.length; i++)
                        mBlobColorHsv.val[i] /= pointCount;

                    mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);
                    int red = (int) mBlobColorRgba.val[0];
                    int green = (int) mBlobColorRgba.val[1];
                    int blue = (int) mBlobColorRgba.val[2];
                    int alpha = (int) (int) mBlobColorRgba.val[3];

                    String hex = String.format("#%02x%02x%02x%02x", red,green,blue,alpha);

                    Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                            ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

                    final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AutoNavigation.this);
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
                                            editor.putString(ConstantApp.SHARED_TARGET_LOWER, mDetector.getmLowerBound());
                                            editor.putString(ConstantApp.SHARED_TARGET_UPPER, mDetector.getmUpperBound());
                                            editor.commit();
                                            colorCounter++;
                                            break;

                                        //right
                                        case 1:
                                            break;

                                        //left
                                        case 2:

                                            //calibration is done
                                            break;


                                    }
                                }
                            });
                    dialogBuilder.show();
                    /*SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString("user_type", personalInfo.getType());
        if (personalInfo.getType().equals(ConstantApp.TYPE_S))
            editor.putBoolean("user_cc", personalInfo.getHasCreditCard());
        Log.v(ConstantApp.TAGLOG, "put in shared: " + personalInfo.getType());
        editor.commit();*/


                    //editor.putString(ConstantApp.SHARED_TARGET, )


                    mDetector.setHsvColor(mBlobColorHsv);

                    Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

                    //mIsColorSelected = true;

                    touchedRegionRgba.release();
                    touchedRegionHsv.release();

                    return false; // don't need subsequent touch events
                }
            });

/*            Scalar lowRed = new Scalar(228, 100, 100);
            Scalar upRed = new Scalar(255, 255, 255);

            Scalar lowBlue = new Scalar(120, 100, 100);
            Scalar upBlue = new Scalar(179, 255, 255);

            Scalar lowGreen = new Scalar(50, 0, 50);
            Scalar upGreen = new Scalar(255, 128, 250);

            //pass image in HSV
            caseHsv = new Mat();
            Imgproc.cvtColor(mOriginal, caseHsv, Imgproc.COLOR_RGB2HSV);

            //filter color red
            caseRed = new Mat();
            Core.inRange(caseHsv, lowRed, upRed, caseRed);

            //filter color blue
            caseBlue = new Mat();
            Core.inRange(caseHsv, lowBlue, upBlue, caseBlue);

            //filter color green
            caseGreen = new Mat();
            Core.inRange(caseHsv, lowGreen, upGreen, caseGreen);*/


                /*FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(mImageFileName);
                    fileOutputStream.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mImage.close();

                    Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mImageFileName)));
                    sendBroadcast(mediaStoreUpdateIntent);

                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.v(TAG, "************skipped image");
            }*/

        }
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }


    private void takePicture() {

        //re-enable button
/*        mButtonHide.setVisibility(View.VISIBLE);
        mButtonNext.setVisibility(View.VISIBLE);*/

        mFabCalibration.setVisibility(View.GONE);


        mCaptureState = STATE_WAIT_LOCK;

        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
        mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, null);

        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
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

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            try {
                createImageFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight,
                                          int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private int maxRes(Size[] sizeList) {
        long surface = 0;
        int index = -1;

        for (int i = 0; i < sizeList.length; i++) {

            Size size = sizeList[i];

            long tempSurface = size.getHeight() * size.getWidth();

            if (tempSurface > surface) {

                surface = tempSurface;
                index = i;
            }
        }

        return index;
    }

    private void createImageFolder() {
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFile, "clemlecCamera");
        if (!mImageFolder.exists()) {
            mImageFolder.mkdirs();
        }
    }


    private File createImageFileName() throws IOException {

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "IMG_" + timestamp + "_";
        File imageFile = File.createTempFile(prepend, ".jpg", mImageFolder);
        mImageFileName = imageFile.getAbsolutePath();

        return imageFile;
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