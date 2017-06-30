package com.clemgmelc.cyberrobotbrain.Computation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.UI.AutoNavigationActivity;
import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;

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
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(15, 50, 50, 0);
    private static final String TAG = ConstantApp.TAG + " - " + AutoNavigationActivity.class.getSimpleName();


  /*  // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.1;


    private Mat mSpectrum = new Mat();
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    // Cache
    Mat mPyrDownMat = new Mat();
    Mat mHsvMat = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();
    Mat mHierarchy = new Mat();

    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }*/

    public void setHsvRange(Scalar hsvColor) {




        //double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0] - mColorRadius.val[0] : 0;
        //double maxH = (hsvColor.val[0] + mColorRadius.val[0] <= 255) ? hsvColor.val[0] + mColorRadius.val[0] : 255;
        double minH2 = -1;
        double maxH2 = -1;

        //TODO: due range
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

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = minH2;
        mUpperBound.val[3] = maxH2;

        /*Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);*/
    }

    public String getLowerBound() {
        return mLowerBound.val[0] + ":" + mLowerBound.val[1] + ":" + mLowerBound.val[2] + ":" + mLowerBound.val[3];
    }

    public String getUpperBound() {
        return mUpperBound.val[0] + ":" + mUpperBound.val[1] + ":" + mUpperBound.val[2] + ":" + mUpperBound.val[3];
    }

    public static Scalar hsvToRGBA(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

   /* public Mat getSpectrum() {
        return mSpectrum;
    }*/

/*    public void setMinContourArea(double area) {
        mMinContourArea = area;
    }

    public void process(Mat rgbaImage) {
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find max contour area
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        // Filter contours by area and resize to fit the original image size
        mContours.clear();
        each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) > mMinContourArea*maxArea) {
                Core.multiply(contour, new Scalar(4,4), contour);
                mContours.add(contour);
            }
        }
    }

    public List<MatOfPoint> getContours() {
        return mContours;
    }*/

    public static void computeFocal(Mat original, Context context) {

        SharedPreferences shared = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);

        String[] targetUpper = shared.getString(ConstantApp.SHARED_TARGET_UPPER, null).split(":");
        String[] targetLower = shared.getString(ConstantApp.SHARED_TARGET_LOWER, null).split(":");
        Scalar lowTarget = new Scalar(Double.valueOf(targetLower[0]), Double.valueOf(targetLower[1]), Double.valueOf(targetLower[2]));
        Scalar upTarget = new Scalar(Double.valueOf(targetUpper[0]), Double.valueOf(targetUpper[1]), Double.valueOf(targetUpper[2]));

        Mat caseHsv = new Mat();
        Imgproc.cvtColor(original, caseHsv, Imgproc.COLOR_RGB2HSV);
        Mat target = new Mat();
        Core.inRange(caseHsv, lowTarget, upTarget, target);

        Mat dest = new Mat();
        Mat gray = new Mat();

        //apply the mask of target on the original image in order to apply imagproc only in the desired area of image (reduce computation)
        original.copyTo(dest, target);

        //pass image into a grey scale
        Imgproc.cvtColor(dest, gray, Imgproc.COLOR_RGB2GRAY);

        //blur image to reduce noise and details in order to reduce number of detected edges
        //incrementing the  size of the kernel takes computational time not feasable for the application
        //and also not useful in our simplified scenario
        org.opencv.core.Size s = new org.opencv.core.Size(3, 3);
        //Imgproc.blur(gray,gray,s);
        Imgproc.GaussianBlur(gray, gray, s, 0, 0);

        //use the Canny edge detector to find edges on the blured image
        Imgproc.Canny(gray, gray, 50, 200);

        //dilatate the borders in order to better find them
        Imgproc.dilate(gray, gray, new Mat());

        //use gray image
        List<MatOfPoint> contoursTarget = new ArrayList<>( );
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contoursTarget, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);


        double maxArea = 0;
        MatOfPoint finalcontour = new MatOfPoint();
        List<MatOfPoint> finacontourlist = new ArrayList<>( );
        Iterator<MatOfPoint> each = contoursTarget.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea) {
                finalcontour = wrapper;
                finacontourlist.add(0, wrapper);
                maxArea = area;
            }
        }

        SharedPreferences sharedpreferences = context.getSharedPreferences(ConstantApp.SHARED_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();

        //TODO: look what arrives
        if (finalcontour != null) {

            Rect rect = Imgproc.boundingRect(finalcontour);
            org.opencv.core.Size rectsize = rect.size();
            Imgproc.rectangle(original, rect.tl(), rect.br(), CONTOUR_COLOR, 5);
            Imgproc.drawContours(original, finacontourlist, -1, CONTOUR_COLOR, 5);

            double pixelWidth = rectsize.width;
            double focal = (pixelWidth * ConstantApp.KNOWN_DISTANCE) / ConstantApp.KNOWN_WIDTH;
            editor.putString(ConstantApp.SHARED_FOCAL, String.valueOf(focal));
            editor.apply();

            Log.v(TAG, "Focal: " +  focal);
            Log.v(TAG, "Standard Width, " + pixelWidth);

        }




    }
}

