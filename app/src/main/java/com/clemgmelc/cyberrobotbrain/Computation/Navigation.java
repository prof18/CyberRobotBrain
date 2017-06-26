package com.clemgmelc.cyberrobotbrain.Computation;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by marco on 6/12/17.
 */

public class Navigation {

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
    public static int turnDirection(Point target, Point left, Point right, boolean type) {

        //0 left, 1 right, 2 facing
        int action = -1;

        if (type) {
            double distanceTL = Math.abs(left.x - target.x);
            double distanceTR = Math.abs(right.x - target.x);
            double distanceLR = Math.abs(right.x - left.x);

            if (distanceLR <= 65)
                action = 2;
            else if (distanceTL > distanceTR)
                action = 1;
            else if (distanceTL < distanceTR)
                action = 0;
        } else {
            double distanceTL = Math.abs(left.y - target.y);
            double distanceTR = Math.abs(right.y - target.y);
            double distanceLR = Math.abs(right.y - left.y);

            if (distanceLR <= 50)
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
    public static boolean isInBound(boolean type, Point start, Point end, int offset) {

        boolean isInBound = false;

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

}
