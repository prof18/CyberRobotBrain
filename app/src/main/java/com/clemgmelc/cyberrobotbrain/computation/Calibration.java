package com.clemgmelc.cyberrobotbrain.computation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.ui.AutoNavigationActivity;
import com.clemgmelc.cyberrobotbrain.util.ConstantApp;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Calibration {

    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(15, 50, 50, 0);

    /**
     *
     * This method saves HSV upper bound and lower bound for the HSV color passed, using the mColorRadius.
     * @param hsvColor is the color that we want to evaluate bounds
     */
    public void setHsvRange(Scalar hsvColor) {

        double minH2 = -1;
        double maxH2 = -1;

        /*
         * H value has a range [0 ; 255] ( mod(255) ).
         * If H is close to 0 or 255 we extend the bound as a circular interval
         * and store the second part of it in .val[3]
         */
        double minH =  hsvColor.val[0] - mColorRadius.val[0];
        if (minH < 0) {
            minH2 = 255 + hsvColor.val[0] - mColorRadius.val[0];
            minH = 0;
        }

        double maxH = hsvColor.val[0] + mColorRadius.val[0];
        if (maxH > 255) {
            maxH2 = hsvColor.val[0] + mColorRadius.val[0] - 255;
            maxH = 0;
        }

        //first H bounds
        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;
        //S bounds
        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];
        //V bounds
        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];
        //second H bounds
        mLowerBound.val[3] = minH2;
        mUpperBound.val[3] = maxH2;

    }

    /**
     *
     * @return Return lower bound
     */
    public String getLowerBound() {
        return mLowerBound.val[0] + ":" + mLowerBound.val[1] + ":" + mLowerBound.val[2] + ":" + mLowerBound.val[3];
    }

    /**
     *
     * @return Return upper bound
     */
    public String getUpperBound() {
        return mUpperBound.val[0] + ":" + mUpperBound.val[1] + ":" + mUpperBound.val[2] + ":" + mUpperBound.val[3];
    }

    /**
     *
     * This method computes RGB values from HSV
     * @param hsvColor Scalar containing HSV values
     * @return Scalar containing RGB values
     */
    public static Scalar hsvToRGBA(Scalar hsvColor) {

        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    /**
     *
     * This method computes the focal of the camera. Focal is computed using the triangular similarity between the real target dimension,
     * the real distance from the target and the dimension of the target in pixel. The focal is the factor of scalability from real dimension
     * to pixel dimension.
     * FOCAL = (PIXEL_DIMENSION * REAL_DISTANCE) /TARGET_DIMENSION
     *
     * @param original Mat containing the picture in RGB format
     * @param context Context
     */
    public static void computeFocal(Mat original, Context context) {

        //Retrieve of bounds from shared preferences
        SharedPreferences shared = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);
        String[] targetUpper = shared.getString(ConstantApp.SHARED_TARGET_UPPER, null).split(":");
        String[] targetLower = shared.getString(ConstantApp.SHARED_TARGET_LOWER, null).split(":");
        Scalar lowTarget = new Scalar(Double.valueOf(targetLower[0]), Double.valueOf(targetLower[1]), Double.valueOf(targetLower[2]));
        Scalar upTarget = new Scalar(Double.valueOf(targetUpper[0]), Double.valueOf(targetUpper[1]), Double.valueOf(targetUpper[2]));

        //Convert Mat from RGB to HSV and store it in caseHsv
        Mat caseHsv = new Mat();
        Imgproc.cvtColor(original, caseHsv, Imgproc.COLOR_RGB2HSV);
        //Compute the mask of target marker and store in target
        Mat target = new Mat();
        Core.inRange(caseHsv, lowTarget, upTarget, target);

        Mat dest = new Mat();
        Mat gray = new Mat();

        //Apply the mask of target on the original image in order to use Imagproc methods only on the desired area of image (reduce computation)
        original.copyTo(dest, target);

        //Pass image into a grey scale
        Imgproc.cvtColor(dest, gray, Imgproc.COLOR_RGB2GRAY);

        /*
         * Blur image to reduce noise and details in order to reduce number of detected edges
         * incrementing the size of the kernel takes computational time not feasible for the application
         * and also not useful in our simplified scenario
        */
        org.opencv.core.Size s = new org.opencv.core.Size(3, 3);
        //Imgproc.blur(gray,gray,s);
        Imgproc.GaussianBlur(gray, gray, s, 0, 0);

        //Use the Canny edge detector to find edges on the blured image
        Imgproc.Canny(gray, gray, 50, 200);

        //Expand the borders in order to better find them
        Imgproc.dilate(gray, gray, new Mat());

        //Save contours
        List<MatOfPoint> contoursTarget = new ArrayList<>( );
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contoursTarget, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        //Find the contour wrapping the max area
        double maxArea = 0;
        MatOfPoint finalContours = new MatOfPoint();
        List<MatOfPoint> finacontourlist = new ArrayList<>( );
        Iterator<MatOfPoint> each = contoursTarget.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea) {
                finalContours = wrapper;
                finacontourlist.add(0, wrapper);
                maxArea = area;
            }
        }

        if (finalContours != null) {

            SharedPreferences sharedpreferences = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();

            //Find the min rect that contains contour with max area
            Rect rect = Imgproc.boundingRect(finalContours);
            org.opencv.core.Size rectsize = rect.size();

            //Use these lines to visualize the results
            //Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
            //Imgproc.rectangle(original, rect.tl(), rect.br(), CONTOUR_COLOR, 5);
            //Imgproc.drawContours(original, finacontourlist, -1, CONTOUR_COLOR, 5);

            //Save in shared preferences the PIXEL_DIMENSION
            double pixelWidth = rectsize.width;
            double focal = (pixelWidth * ConstantApp.KNOWN_DISTANCE) / ConstantApp.KNOWN_WIDTH;
            editor.putString(ConstantApp.SHARED_FOCAL, String.valueOf(focal));
            editor.apply();

            Log.v(TAG, "Focal: " +  focal);
            Log.v(TAG, "Standard Width, " + pixelWidth);
        }
    }
}

