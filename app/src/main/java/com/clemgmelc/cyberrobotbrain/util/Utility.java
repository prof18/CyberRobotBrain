package com.clemgmelc.cyberrobotbrain.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class contains some useful static methods
 *
 */
public class Utility {

    private static final String TAG = ConstantApp.TAG + " - " + Utility.class.getSimpleName();

    /**
     * This method checks if the color calibration of the markers is done. To do that, it checks if
     * the color ranges are saved into the shared preference
     *
     * @param context   A reference of the Application Context
     * @return          The method returns true if the calibration is already done, false otherwise
     */
    public static boolean isCalibrationDone(Context context) {

        boolean isCalibrationDone = false;

        //get a reference to the shared preferences
        SharedPreferences sharedpreferences = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

        //Get the value saved on the shared preferences
        String leftUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_UPPER, null);
        String leftLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_LEFT_LOWER, null);
        String rightUpper = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_UPPER, null);
        String rightLower = sharedpreferences.getString(ConstantApp.SHARED_ROBOT_RIGHT_LOWER, null);
        String targetUpper = sharedpreferences.getString(ConstantApp.SHARED_TARGET_UPPER, null);
        String targetLower = sharedpreferences.getString(ConstantApp.SHARED_TARGET_LOWER, null);
        String focal = sharedpreferences.getString(ConstantApp.SHARED_FOCAL, null);

        if (leftUpper != null && leftLower != null && rightUpper != null && rightLower != null
                && targetUpper != null && targetLower != null && focal != null)
            isCalibrationDone = true;

        return isCalibrationDone;
    }

    /**
     * This method checks if the color calibration of the target markers is done. To do that, it checks if
     * the color ranges are saved into the shared preference
     *
     * @param context   A reference of the Application Context
     * @return          The method returns true if the calibration is already done, false otherwise
     */
    public static boolean isTargetCalibrationDone(Context context) {

        boolean isCalibrationDone = false;

        //get a reference to the shared preferences
        SharedPreferences sharedpreferences = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

        //get the value saved on the shared preferences
        String targetUpper = sharedpreferences.getString(ConstantApp.SHARED_TARGET_UPPER, null);
        String targetLower = sharedpreferences.getString(ConstantApp.SHARED_TARGET_LOWER, null);

        if ( targetUpper != null && targetLower != null)
            isCalibrationDone = true;

        return isCalibrationDone;
    }

    /**
     * This method chooses the max resolution by comparing the area.
     *
     * @param sizeList  An array of Size that contains all the resolutions
     * @return          The method returns the index of the max resolution
     */
    public static int maxRes(Size[] sizeList) {

        long surface = 0;
        int index = -1;

        for (int i = 0; i < sizeList.length; i++) {

            Size size = sizeList[i];
            //compute the area to find the best resolution
            long tempSurface = size.getHeight() * size.getWidth();

            if (tempSurface > surface) {
                surface = tempSurface;
                index = i;
            }
        }
        return index;
    }

    //TODO: controllare e pulire
    public static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight,
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
}
