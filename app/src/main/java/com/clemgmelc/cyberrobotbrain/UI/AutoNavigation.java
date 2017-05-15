package com.clemgmelc.cyberrobotbrain.UI;

import android.app.Activity;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.clemgmelc.cyberrobotbrain.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by giulio on 15/05/17.
 */

public class AutoNavigation extends Activity {

    private final static String TAG = "SimpleCamera";
    private TextureView mTextureView;
    private Size mPreviewSize;
    private Size[] mSizeList;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    protected CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private FloatingActionButton mTakePictureButton;
    private TextureView.SurfaceTextureListener mSurfaceTextureListner;
    private CameraCaptureSession.StateCallback mPreviewStateCallback;
    private int mTextureHeight, mTextureWidth;
    private CameraDevice.StateCallback mStateCallback;
    private StreamConfigurationMap mMap;
    private CameraManager mCameraManager;
    private String mCameraId;
    private Size mMaxSize;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        //turning off the title at the top
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //full screen hid superior bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.auto_navigation_main);
        mTextureView = (TextureView) findViewById(R.id.texture);

        mSurfaceTextureListner = getSurfaceTextureListener();

        mPreviewStateCallback = setStateCallbackCameraCaptureSession();

        mStateCallback = setStateCallbackCameraDevice();

        mTextureView.setSurfaceTextureListener(mSurfaceTextureListner);



        mTakePictureButton = (FloatingActionButton) findViewById(R.id.floatingActionButton);
        mTakePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(),"cheeeese",Toast.LENGTH_SHORT).show();
                takePicture();
            }
        });


    }
    private TextureView.SurfaceTextureListener getSurfaceTextureListener() {
        TextureView.SurfaceTextureListener surfaceTextureListner = new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                                                    int height) {
                // TODO Auto-generated method stub
                Log.i(TAG, "onSurfaceTextureSizeChanged()");
                mTextureWidth = width;
                mTextureHeight = height;

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                // TODO Auto-generated method stub
                Log.i(TAG, "onSurfaceTextureDestroyed()");
                return false;
            }

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // TODO Auto-generated method stub
                Log.i(TAG, "onSurfaceTextureAvailable()");
                mTextureWidth = width;
                mTextureHeight = height;
                //when surface texture is available setup the camera
                setupCamera();


                try {


                    for (String cameraId : mCameraManager.getCameraIdList()) {
                        CameraCharacteristics characteristics
                                = mCameraManager.getCameraCharacteristics(cameraId);


                        Log.d("Img", "INFO_SUPPORTED_HARDWARE_LEVEL " + characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
                    }

                    mCameraManager.openCamera(mCameraId, mStateCallback, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        };
        return surfaceTextureListner;
    }




    protected void createCameraPreview() {

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (texture == null) {
            Log.e(TAG, "texture is null");
            return;
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mPreviewBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface),new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (mCameraDevice == null) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(AutoNavigation.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }






    private CameraDevice.StateCallback setStateCallbackCameraDevice() {
        CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                // TODO Auto-generated method stub
                Log.i(TAG, "onOpened");
                mCameraDevice = camera;
                createCameraPreview();


            }

            @Override
            public void onError(CameraDevice camera, int error) {
                // TODO Auto-generated method stub
                if (mCameraDevice!= null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                Log.e(TAG, "onError " + error);

            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                // TODO Auto-generated method stub
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                /*if (mImageReader != null) {
                    mImageReader.close();
                    mImageReader = null;
                }*/

                Log.e(TAG, "onDisconnected");

            }

        };
        return stateCallback;
    }

    private CameraCaptureSession.StateCallback setStateCallbackCameraCaptureSession() {
        CameraCaptureSession.StateCallback previewStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                // TODO Auto-generated method stub
                Log.d(TAG, "onConfigured");
                if (mCameraDevice == null) {
                    return;
                }
                mPreviewSession = session;
                updatePreview();
/*
				mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

				HandlerThread backgroundThread1 = new HandlerThread("CameraPreview1");
				backgroundThread1.start();
				Handler backgroundHandler = new Handler(backgroundThread1.getLooper());


				try {
					mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
				} catch (CameraAccessException e) {
					e.printStackTrace();
				}*/


            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                // TODO Auto-generated method stub
                Log.e(TAG, "CameraCaptureSession Configure failed");
            }
        };
        return previewStateCallback;
    }

    private void setupCamera() {
        CameraCharacteristics characteristics;
        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            //get frontal camera
            for (String cameraId : mCameraManager.getCameraIdList()) {

                characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                //We need only back camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {

                    mCameraId = cameraId;
                }
            }
            //override characteristic of that camera
            characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            mMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (mMap != null) {
                mSizeList = mMap.getOutputSizes(ImageFormat.JPEG);
            }

            //need to get the max available size saved in mMaxSize
            int maxResIndex = maxRes(mSizeList);
            if (maxResIndex != -1) {
                mMaxSize = mSizeList[maxResIndex];
            }
            mImageReader = ImageReader.newInstance( mMaxSize.getWidth(),mMaxSize.getHeight(), ImageFormat.JPEG,2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
            Point displaySize = new Point();
            this.getWindowManager().getDefaultDisplay().getSize(displaySize);

            int maxPreviewWidth = displaySize.y;
            int maxPreviewHeight = displaySize.x;

            mPreviewSize = chooseOptimalSize(mMap.getOutputSizes(SurfaceTexture.class),
                    mTextureWidth, mTextureHeight, maxPreviewWidth, maxPreviewHeight, mMaxSize);

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, mTextureWidth, mTextureHeight);
            RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            //matrix.postRotate(270, centerX, centerY);

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) mTextureHeight / mPreviewSize.getHeight(),
                    (float) mTextureWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(270, centerX, centerY);

            mTextureView.setTransform(matrix);
            //CAMERA SETUP FINISHED



        } catch (CameraAccessException e) {
            e.printStackTrace();
        }




    }

    protected void updatePreview() {
        if (mCameraDevice == null) {
            Log.e(TAG, "updatePreview error, return");
            return;
        }
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
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
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    //a new image is ready to be saved
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //TODO: fai cose per catturare l'immagine
            Log.e(TAG, "onImageAvailable");
            //mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

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




    protected void takePicture() {
        if (mCameraDevice == null) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        try {

            List<Surface> outputSurfaces = new ArrayList<>();

            outputSurfaces.add(mImageReader.getSurface());

            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            final File file = new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis() + ".jpg");
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

            mImageReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(AutoNavigation.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    Log.d(TAG,"FOTO SALVATA");
                    createCameraPreview();

                }
            };

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
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

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        Log.d(TAG, "onPause()");
        super.onPause();

        stopBackgroundThread();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            setupCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListner);
        }

    }
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


}