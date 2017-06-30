package com.clemgmelc.cyberrobotbrain.Computation;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.lang.UProperty;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.Util.ConstantApp;
import com.clemgmelc.cyberrobotbrain.Util.Utility;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by marco on 6/12/17.
 */

public class Navigation {

    private static final String TAG = ConstantApp.TAG + " - " + Utility.class.getSimpleName();


    public static List<MatOfPoint> findContours(Mat matImage) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(matImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        return contours;
    }

    public static Point findCentroid(List<MatOfPoint> contours) {

        //this value is taken from the experience
        double maxArea = 100;
        int index = -1;

        if (!contours.isEmpty()) {

            for (MatOfPoint mop : contours) {

                double area = Imgproc.contourArea(mop);
                if (area > maxArea) {
                    maxArea = area;
                    index = contours.indexOf(mop);
                }
            }


            //List<Moments> mu = new ArrayList<>(contours.size());

            if (index == -1)
                return null;
            Moments m = Imgproc.moments(contours.get(index), false);
            int x = (int) (m.get_m10() / m.get_m00());
            int y = (int) (m.get_m01() / m.get_m00());
            org.opencv.core.Point centroid = new org.opencv.core.Point(x, y);

            return centroid;
        }
        return null;
    }

    /**
     * @param target
     * @param left
     * @param right
     * @param type   false for x, true for y
     * @return
     */
    public static int turnDirection(Point target, Point left, Point right, boolean type, double focal, double height) {


        double tmp = (ConstantApp.KNOWN_WIDTH * focal) / height;

        double pixelToCm = tmp / 3;



        //0 left, 1 right, 2 facing
        int action = -1;

        if (type) {
            double distanceTL = (Math.abs(left.x - target.x))/pixelToCm;
            double distanceTR = (Math.abs(right.x - target.x))/pixelToCm;
            double distanceLR = (Math.abs(right.x - left.x))/pixelToCm;

            if (distanceLR <= 2.5)
                action = 2;
            else if (distanceTL > distanceTR)
                action = 1;
            else if (distanceTL < distanceTR)
                action = 0;
        } else {
            double distanceTL = (Math.abs(left.y - target.y))/pixelToCm;
            double distanceTR = (Math.abs(right.y - target.y))/pixelToCm;
            double distanceLR = (Math.abs(right.y - left.y))/pixelToCm;

            if (distanceLR <= 2.5)
                action = 2;
            else if (distanceTL > distanceTR)
                action = 1;
            else if (distanceTL < distanceTR)
                action = 0;
        }

        return action;
    }

    /**
     * @param type  true for y, false for x
     * @param start
     * @param end
     * @return
     */
    public static boolean isInBound(boolean type, Point start, Point end, double boundCm, double height, double focal) {

        boolean isInBound = false;

        double offset = (ConstantApp.KNOWN_WIDTH * focal) / height;
        Log.v(TAG, "offset: " + offset);

        if (ConstantApp.KNOWN_WIDTH != boundCm) {

            double pixelToCm = offset / 3;

            offset = boundCm * pixelToCm;
            Log.v(TAG, "offset: " + offset);
        }

        if (type) {
            if (start.y >= end.y - offset && start.y <= end.y + offset)
                isInBound = true;

        } else {
            if (start.x >= end.x - offset && start.x <= end.x + offset)
                isInBound = true;
        }
        return isInBound;
    }

    /**
     * @param target
     * @param right
     * @param mean
     * @param type   false for x, true for y
     * @return
     */
    public static boolean isWrongSide(Point target, Point right, Point mean, boolean type) {

        boolean isWrongSide = false;

        if (type) {
            //case 3
            if (target.x <= mean.x && right.y >= mean.y)
                isWrongSide = true;
                //case 4
            else if (target.x >= mean.x && right.y <= mean.y)
                isWrongSide = true;

        } else {
            //case 1
            if (target.y <= mean.y && right.x <= mean.x)
                isWrongSide = true;
                //case 2
            else if (target.y >= mean.y && right.y >= mean.y)
                isWrongSide = true;
        }

        return isWrongSide;

    }

    public static double computeDistance(Mat original, Context context) {

        double distance = -1;

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
        List<MatOfPoint> contoursTarget = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contoursTarget, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);


        double maxArea = 0;
        MatOfPoint finalcontour = new MatOfPoint();
        List<MatOfPoint> finacontourlist = new ArrayList<>();
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

        if (finalcontour != null) {

            Rect rect = Imgproc.boundingRect(finalcontour);
            org.opencv.core.Size rectsize = rect.size();
            Imgproc.rectangle(original, rect.tl(), rect.br(), CONTOUR_COLOR, 5);
            Imgproc.drawContours(original, finacontourlist, -1, CONTOUR_COLOR, 5);

            String distanceS = shared.getString(ConstantApp.SHARED_FOCAL, null);

            double pixelWidth = rectsize.width;
            double focal;

            if (distanceS != null) {
                focal = Double.valueOf(distanceS);

                Log.v(TAG, "Pixel Width: " + pixelWidth);

                distance = (ConstantApp.KNOWN_WIDTH * focal) / pixelWidth;

            }

        }

        return distance;

    }

    public static boolean isPerpendicular(double m1, double m2, double focal, double height, Point start, Point target) {

        boolean isPerpendicular = false;

        //  m1 * m2 <= -0.5 && m1 * m2 >= -1.5

        double lowerBound;
        double upperBound;

        Log.v(TAG, "m1: " + m1);
        Log.v(TAG, "m2: " + m2);

        double tmp = (ConstantApp.KNOWN_WIDTH * focal) / height;

        double pixelToCm = tmp / 3;

        double distance = Math.sqrt(Math.pow(target.x - start.x, 2) + Math.pow(target.y - start.y, 2));
        double distanceCm = distance/pixelToCm;

        Log.v(TAG, "Distance in cm: " + distanceCm);

    /*    if (distanceCm >= 21) {

            lowerBound = -1.8;
            upperBound = -0.2;

        }  else  {
            lowerBound = -3.0;
            upperBound = 0.0;
        }*/

        if (distanceCm <= 8) {

            upperBound = -0.000000001;
            lowerBound = -1.999999999;

        } else {

            upperBound = -0.1;
            lowerBound = -1.9;
        }

        Log.v(TAG, "Lower Bound: " + lowerBound + " - Upper Bound: "+ upperBound);

        //tmp = boundCm * pixelToCm;

        //  m1 * m2 <= -0.5 && m1 * m2 >= -1.5

        if ( m1 * m2 >= lowerBound && m1 * m2 <= upperBound)
            isPerpendicular = true;

        return isPerpendicular;


    }

}
