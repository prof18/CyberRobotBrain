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
        double maxArea = 5000;
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


            Moments m = Imgproc.moments(contours.get(index), false);
            int x = (int) (m.get_m10() / m.get_m00());
            int y = (int) (m.get_m01() / m.get_m00());
            org.opencv.core.Point centroid = new org.opencv.core.Point(x, y);

            return centroid;
        }
        return null;
    }

}
