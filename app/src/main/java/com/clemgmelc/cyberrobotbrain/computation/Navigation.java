package com.clemgmelc.cyberrobotbrain.computation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.clemgmelc.cyberrobotbrain.util.ConstantApp;
import com.clemgmelc.cyberrobotbrain.util.Utility;

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


public class Navigation {

    private static final String TAG = ConstantApp.TAG + " - " + Utility.class.getSimpleName();

    /**
     * This method evaluates contours
     *
     * @param matImage is a Mat containing a mask (black & white)
     * @return List<MatOfPoint> containing the contour
     */
    public static List<MatOfPoint> findContours(Mat matImage) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(matImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        return contours;
    }

    /**
     * This method evaluates the centroid of a specific marker
     *
     * @param contours is the contour of the marker
     * @return
     */
    public static Point findCentroid(List<MatOfPoint> contours) {

        //In order to eliminate small source of noise the area need to be greater than 100 pixel
        double maxArea = 100;
        int index = -1;

        if (!contours.isEmpty()) {

            //take max contour
            for (MatOfPoint mop : contours) {

                double area = Imgproc.contourArea(mop);
                if (area > maxArea) {
                    maxArea = area;
                    index = contours.indexOf(mop);
                }
            }

            if (index == -1)
                return null;

            //Imgproc.moments computes the centroid
            Moments m = Imgproc.moments(contours.get(index), false);
            int x = (int) (m.get_m10() / m.get_m00());
            int y = (int) (m.get_m01() / m.get_m00());
            org.opencv.core.Point centroid = new org.opencv.core.Point(x, y);

            return centroid;
        }
        return null;
    }

    /**
     * This method is used to obtain the next rotation action that robot will execute if needed
     * in the movement of L shape
     *
     * @param target The target marker Centroid (for the movement on Y axis pass a temporary target)
     * @param left   The left marker Centroid
     * @param right  The right marker Centroid
     * @param type   Type is used to select the axis on which the robot will move. FALSE if it moves on x axis,
     *               TRUE for movement on y axis
     * @return An integer that identify the action. {0 = TURN_LEFT, 1 = TURN_RIGHT, 2 = FACING (rotation not needed)}
     */
    public static int turnDirection(Point target, Point left, Point right, boolean type, double focal, double height) {

        double tmp = (ConstantApp.KNOWN_WIDTH * focal) / height;
        double pixelToCm = tmp / 3;
        int action = -1;

        if (type) {
            //Movement on Y axis
            /*
             * Compute distances (in cm) from TARGET_CENTROID to LEFT_CENTROID and to RIGHT_CENTROID
             * and from LEFT_CENTROID to RIGHT_CENTROID. These are computed using x coordinates
             */
            double distanceTL = (Math.abs(left.x - target.x)) / pixelToCm;
            double distanceTR = (Math.abs(right.x - target.x)) / pixelToCm;
            double distanceLR = (Math.abs(right.x - left.x)) / pixelToCm;

            /*
             * Using distances on x coordinates establish if robot is correctly oriented for the movement
             * over Y axis. If distanceLR is small enough it means robot is facing the target, otherwise
             * it turn to the side of the nearest marker to the temporary target
             */
            if (distanceLR <= 2.5)
                action = 2;
            else if (distanceTL > distanceTR)
                action = 1;
            else if (distanceTL < distanceTR)
                action = 0;

        } else {
            //Movement on X axis
            /*
             * Compute distances in cm from TARGET_CENTROID to LEFT_CENTROID, RIGHT_CENTROID
             * and from LEFT_CENTROID to RIGHT_CENTROID
             */
            double distanceTL = (Math.abs(left.y - target.y)) / pixelToCm;
            double distanceTR = (Math.abs(right.y - target.y)) / pixelToCm;
            double distanceLR = (Math.abs(right.y - left.y)) / pixelToCm;

            /*
             * Using distances on y coordinates establish if robot is correctly oriented for the movement
             * over X axis. If distanceLR is small enough it means robot is facing the target, otherwise
             * it turn to the side of the nearest marker to the target
             */
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
     * This method computes a bound for the path, for a specific axis. This bound is used to estimate if the robot
     * is deviating too much from the straight route. (for the movement of L shape)
     *
     * @param type    Type is used to select the axis on which the robot will move. FALSE if it moves on x axis,
     *                TRUE for movement on y axis
     * @param start   is the mean point among LEFT_CENTROID and RIGHT_CENTROID
     * @param end     is the TARGET_CENTROID
     * @param boundCm is half of the bound in cm that will be fixed
     * @param height  is the current height from the device to the target marker
     * @param focal   is the factor of scalability from real dimension to pixel dimension
     *                (x pixel = 3 cm. Value 3 is the diameter of the TARGET_MARKER)
     * @return TRUE if robot is in the bound, FALSE otherwise
     */
    public static boolean isInBound(boolean type, Point start, Point end, double boundCm, double height, double focal) {

        boolean isInBound = false;
        double offset = (ConstantApp.KNOWN_WIDTH * focal) / height;
        Log.v(TAG, "offset: " + offset);

        //compute offset
        if (ConstantApp.KNOWN_WIDTH != boundCm) {

            //pixelToCm is the scale factor (x pixel = 1 cm)
            double pixelToCm = offset / 3;
            offset = boundCm * pixelToCm;
            Log.v(TAG, "offset: " + offset);
        }

        //evaluate inBound condition
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
     * This method evaluate if the robot is turned away to target (is oriented in the wrong direction).
     * (for the movement of L shape)
     *
     * @param target is the TARGET_CENTROID
     * @param right  is the RIGHT_MARKER
     * @param mean   is the mean point among LEFT_CENTROID and RIGHT_CENTROID
     * @param type   Type is used to select the axis on which the robot will move. FALSE if it moves on x axis,
     *               TRUE for movement on y axis
     * @return TRUE if robot is oriented in wrong direction, FALSE otherwise
     */
    public static boolean isWrongSide(Point target, Point right, Point mean, boolean type) {

        boolean isWrongSide = false;
        /*
         * Depending on the disposition of robot and target in the field four condition can occur
         * For each of them comparing coordinates it evaluate the WrongSide condition
         */
        if (type) {
            if (target.x <= mean.x && right.y >= mean.y)
                isWrongSide = true;
            else if (target.x >= mean.x && right.y <= mean.y)
                isWrongSide = true;

        } else {
            if (target.y <= mean.y && right.x <= mean.x)
                isWrongSide = true;
            else if (target.y >= mean.y && right.y >= mean.y)
                isWrongSide = true;
        }

        return isWrongSide;

    }

    /**
     * This method compute height in cm from the camera to the TARGET_MARKER using this formula
     * ACTUAL_HEIGHT = (TARGET_WIDTH * FOCAL) / ACTUAL_PIXEL_WIDTH
     *
     * @param original Mat in RGB format, containing the image framed
     * @param context
     * @return the current height
     */

    public static double computeHeight(Mat original, Context context) {

        double height = -1;
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
        List<MatOfPoint> contoursTarget = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contoursTarget, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        //Find the contour wrapping the max area
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

        //TODO: look what arrives if null is OK
        if (finalcontour != null) {

            //Find the min rect that contains contour with max area
            Rect rect = Imgproc.boundingRect(finalcontour);
            org.opencv.core.Size rectsize = rect.size();

            //Use these lines to visualize the results
            //Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
            //Imgproc.rectangle(original, rect.tl(), rect.br(), CONTOUR_COLOR, 5);
            //Imgproc.drawContours(original, finacontourlist, -1, CONTOUR_COLOR, 5);

            //Retrieve focal from sahered
            String focalS = shared.getString(ConstantApp.SHARED_FOCAL, null);

            //Take width of the rect
            double pixelWidth = rectsize.width;
            double focal;
            if (focalS != null) {

                focal = Double.valueOf(focalS);
                Log.v(TAG, "Pixel Width: " + pixelWidth);
                //Compute height
                height = (ConstantApp.KNOWN_WIDTH * focal) / pixelWidth;
            }

        }

        return height;

    }

    /**
     * @param m1     slope of the first line passing from LEFT_MARKER and RIGHT_MARKER
     * @param m2     slope of the second line passing from the target and the mean point between LEFT_MARKER and RIGHT_MARKER
     * @param focal  is the factor of scalability from real dimension to pixel dimension
     *               (x pixel = 3 cm. Value 3 is the diameter of the TARGET_MARKER)
     * @param height is the current height from the device to the target marker
     * @param start  is the mean point among LEFT_CENTROID and RIGHT_CENTROID
     * @param target is the TARGET_CENTROID
     * @return TRUE if m1 and m2 are perpendicular, FALSE otherwise
     */
    public static boolean isPerpendicular(double m1, double m2, double focal, double height, Point start, Point target) {

        boolean isPerpendicular = false;
        double lowerBound;
        double upperBound;
        double tmp = (ConstantApp.KNOWN_WIDTH * focal) / height;
        double pixelToCm = tmp / 3;
        double distance = Math.sqrt(Math.pow(target.x - start.x, 2) + Math.pow(target.y - start.y, 2));
        double distanceCm = distance / pixelToCm;

        Log.v(TAG, "m1: " + m1);
        Log.v(TAG, "m2: " + m2);
        Log.v(TAG, "Distance in cm: " + distanceCm);

        //Based on distance take different condition of perpendicularity
        if (distanceCm <= 8) {
            upperBound = -0.000000001;
            lowerBound = -1.999999999;
        } else {
            upperBound = -0.1;
            lowerBound = -1.9;
        }

        Log.v(TAG, "Lower Bound: " + lowerBound + " - Upper Bound: " + upperBound);

        if (m1 * m2 >= lowerBound && m1 * m2 <= upperBound)
            isPerpendicular = true;

        return isPerpendicular;
    }
}
