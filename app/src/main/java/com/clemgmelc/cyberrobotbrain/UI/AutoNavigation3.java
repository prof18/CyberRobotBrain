package com.clemgmelc.cyberrobotbrain.UI;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.R;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by marco on 5/4/17.
 */

public class AutoNavigation3 extends AppCompatActivity {


    private static final String TAG = ConstantApp.TAG;
    private ImageReader reader;
    private TextureView textureView;
    private List<Surface> outputSurfaces;
    private CameraCharacteristics mCharacteristics;
    private CameraManager manager;
    private CaptureRequest.Builder captureBuilder;
    private static final int ALERT_CAMERA = 1;
    private static final int ALERT_FILE = 2;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();


    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String mCameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private FloatingActionButton mFab;
    private int mTextureHeight, mTextureWidth;
    private ImageReader mImageReader;
    private Size maxSize;
    private Integer mCameraOrientation;

    // Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_WIDTH = 1920;
    //Max preview height that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private AutoNavigation3 activity;
    private StreamConfigurationMap map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auto_navigation_main);
        activity = this;

        textureView = (TextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);
        mFab = (FloatingActionButton) findViewById(R.id.floatingActionButton);


        //TODO: REQUEST PERMISSIONS

/*        //full screen and transparent status and navigation bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        final View decorView = getWindow().getDecorView();
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {

                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            decorView.setSystemUiVisibility(uiOptions);
                        }
                    }, 1500);

                }
            }
        });*/

        Log.v(TAG, "sihg");

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(AutoNavigation3.this, "View Clicked", Toast.LENGTH_SHORT).show();
                takePicture();
            }
        });

    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            mTextureWidth = width;
            mTextureHeight = height;
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
            mTextureWidth = width;
            mTextureHeight = height;
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    //camera state callback
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.v(TAG, "Camera opened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
            closeCamera();
            Log.v(TAG, "Camera closed");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
            Log.v(TAG, "Camera error");
        }
    };

    // callback called when the capture is completed
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(AutoNavigation3.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.v(TAG, "Camera Background Thread Started");
    }

    private void stopBackgroundThread() {

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.v(TAG, "Camera Background Thread Stopped");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    protected void createCameraPreview() {

        try {

            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture != null) {
                texture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                Surface surface = new Surface(texture);
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(surface);
                cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        //The camera is already closed
                        if (null == cameraDevice) {
                            return;
                        }
                        // When the session is ready, we start displaying the preview.
                        cameraCaptureSessions = cameraCaptureSession;
                        updatePreview();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(AutoNavigation3.this, "Configuration change", Toast.LENGTH_SHORT).show();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupCamera() {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {

            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                //We need only back camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {

                    mCameraId = cameraId;

                    mCharacteristics = manager.getCameraCharacteristics(mCameraId);
                    map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    if (map != null) {
                        Size[] sizeList = map.getOutputSizes(ImageFormat.JPEG);
                        int maxResIndex = maxRes(sizeList);
                        if (maxResIndex != -1) {
                            maxSize = sizeList[maxResIndex];
                            mImageReader = ImageReader.newInstance(maxSize.getWidth(), maxSize.getHeight(), ImageFormat.JPEG, 1);
                            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                            //get device orientation
                            int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();

                            //get camera orientation
                            mCameraOrientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);


                            Point displaySize = new Point();
                            getWindowManager().getDefaultDisplay().getSize(displaySize);
                            //portrait
/*                            int maxPreviewWidth = displaySize.x;
                            int maxPreviewHeight = displaySize.y;*/


                            int maxPreviewWidth = displaySize.y;
                            int maxPreviewHeight = displaySize.x;


                            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                                maxPreviewWidth = MAX_PREVIEW_WIDTH;
                            }

                            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                            }

                            Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                                    mTextureWidth, mTextureHeight, maxPreviewWidth, maxPreviewHeight, maxSize);


                            Matrix matrix = new Matrix();
                            RectF viewRect = new RectF(0, 0, mTextureWidth, mTextureHeight);
                            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());

                            float centerX = viewRect.centerX();
                            float centerY = viewRect.centerY();

                            //matrix.postRotate(270, centerX, centerY);

                            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                            float scale = Math.max(
                                    (float) mTextureHeight / previewSize.getHeight(),
                                    (float) mTextureWidth / previewSize.getWidth());
                            matrix.postScale(scale, scale, centerX, centerY);
                            matrix.postRotate(270, centerX, centerY);

                            textureView.setTransform(matrix);

                        }
                    }

                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.v(TAG, "Exception from setupCamera");
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
            return Collections.min(bigEnough, new AutoNavigation2.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new AutoNavigation2.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    //a new image is ready to be saved
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //TODO: fai cose per catturare l'immagine
            //mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };


    private int maxRes(Size[] list) {


        long surface = 0;
        int index = -1;

        for (int i = 0; i < list.length; i++) {

            Size size = list[i];

            long tempSurface = size.getHeight() * size.getWidth();

            if (tempSurface > surface) {

                surface = tempSurface;
                index = i;
            }
        }

        return index;
    }

    private void openCamera() {

        try {

            setupCamera();
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            int cameraPerm = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_CAMERA[0]);
            int storagePerm = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_CAMERA[1]);

            if (cameraPerm != PackageManager.PERMISSION_GRANTED || storagePerm != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS_CAMERA, REQUEST_CAMERA_PERMISSION);
            } else {
                manager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        int cameraPerm = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_CAMERA[0]);
        int storagePerm = ActivityCompat.checkSelfPermission(activity, PERMISSIONS_CAMERA[1]);
        switch (requestCode) {

            //TODO: fa casini, tanti casini con i dialog

            case (REQUEST_CAMERA_PERMISSION):
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Permission Granted");

                    if (cameraPerm == PackageManager.PERMISSION_GRANTED && storagePerm == PackageManager.PERMISSION_GRANTED) {

                        try {
                            manager.openCamera(mCameraId, stateCallback, null);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
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

    protected void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        try {

           Size[] jpegSizes = null;
            if (mCharacteristics != null) {
                jpegSizes = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }


            ImageReader reader = ImageReader.newInstance(width,height , ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>();

            //preview
            outputSurfaces.add(mImageReader.getSurface());
            outputSurfaces.add(reader.getSurface());


            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            //final File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".jpg");
            final File file = new File(getApplicationContext().getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");


            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(AutoNavigation3.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

}
