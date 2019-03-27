package com.example.promar.imageprocessinglib;

import android.graphics.Bitmap;

import com.example.promar.imageprocessinglib.feature.FeatureDetector;
import com.example.promar.imageprocessinglib.feature.FeatureMatcher;
import com.example.promar.imageprocessinglib.model.DescriptorType;
import com.example.promar.imageprocessinglib.model.ImageFeature;
import com.example.promar.imageprocessinglib.util.ImageUtil;
import org.opencv.android.Utils;
import org.opencv.core.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageProcessor {

    static public List<Mat> rotatedImage(Mat image, float stepAngle, int num) {
        List<Mat> r = new ArrayList<>();
        for (int i = 1; i <= num; i++) {
            r.add(ImageUtil.rotateImage(image, (1 + i * stepAngle)));
        }
        return r;
    }

    static public List<Mat> scaleImage(Mat image, float stepScale, int num) {
        List<Mat> r = new ArrayList<>();
        for (int i = 1; i <= num; i++) {
            r.add(ImageUtil.scaleImage(image, (1 + i * stepScale)));
        }
        return r;
    }

    static public List<Mat> lightImage(Mat image, float stepLight, int num) {
        List<Mat> r = new ArrayList<>();
        for (int i = 1; i <= num; i++) {
            r.add(ImageUtil.lightImage(image, 1 + stepLight * i, 0));
        }
        return r;
    }

    //WARNING: stepPer * num should be less than Min(image.width, image.height) and larger than 0
    static public List<Mat> changeToRightPerspective(Mat image, float stepPer, int num) {
        List<Mat> r = new ArrayList<>();

        List<Point> originals = new ArrayList<>();
        originals.add(new Point(0, 0));
        originals.add(new Point(image.cols(), 0));
        originals.add(new Point(image.cols(), image.rows()));
        originals.add(new Point(0, image.rows()));
        int pixelStep = Math.min(image.rows(), image.cols());
        for (int i = 1; i <= num; i++) {
            List<Point> corners = new ArrayList<>();
            corners.add(new Point(stepPer*i, stepPer*i));
            corners.add(new Point(image.cols(), 0));
            corners.add(new Point(image.cols(), image.rows()));
            corners.add(new Point(stepPer*i, image.rows()-stepPer*i));

            r.add(ImageUtil.changeImagePerspective(image, originals, corners));
        }
        return r;
    }

    //WARNING: stepPer * num should be less than Min(image.width, image.height) and larger than 0
    static public List<Mat> changeToLeftPerspective(Mat image, float stepPer, int num) {
        List<Mat> r = new ArrayList<>();

        List<Point> originals = new ArrayList<>();
        originals.add(new Point(0, 0));
        originals.add(new Point(image.cols(), 0));
        originals.add(new Point(image.cols(), image.rows()));
        originals.add(new Point(0, image.rows()));
        for (int i = 1; i <= num; i++) {
            List<Point> corners = new ArrayList<>();
            corners.add(new Point(0, 0));
            corners.add(new Point(image.cols()-i*stepPer, i*stepPer));
            corners.add(new Point(image.cols()-i*stepPer, image.rows()-i*stepPer));
            corners.add(new Point(0, image.rows()));

            r.add(ImageUtil.changeImagePerspective(image, originals, corners));
        }
        return r;
    }

    //WARNING: stepPer * num should be less than Min(image.width, image.height) and larger than 0
    static public List<Mat> changeToTopPerspective(Mat image, float stepPer, int num) {
        List<Mat> r = new ArrayList<>();

        List<Point> originals = new ArrayList<>();
        originals.add(new Point(0, 0));
        originals.add(new Point(image.cols(), 0));
        originals.add(new Point(image.cols(), image.rows()));
        originals.add(new Point(0, image.rows()));
        for (int i = 1; i <= num; i++) {
            List<Point> corners = new ArrayList<>();
            corners.add(new Point(0, 0));
            corners.add(new Point(image.cols(), 0));
            corners.add(new Point(image.cols()-stepPer*i, image.rows()-stepPer*i));
            corners.add(new Point(stepPer*i, image.rows()-stepPer*i));

            r.add(ImageUtil.changeImagePerspective(image, originals, corners));
        }
        return r;
    }

    //WARNING: stepPer * num should be less than Min(image.width, image.height) and larger than 0
    static public List<Mat> changeToBottomPerspective(Mat image, float stepPer, int num) {
        List<Mat> r = new ArrayList<>();

        List<Point> originals = new ArrayList<>();
        originals.add(new Point(0, 0));
        originals.add(new Point(image.cols(), 0));
        originals.add(new Point(image.cols(), image.rows()));
        originals.add(new Point(0, image.rows()));
        for (int i = 1; i <= num; i++) {
            List<Point> corners = new ArrayList<>();
            corners.add(new Point(stepPer*i, stepPer*i));
            corners.add(new Point(image.cols()-i*stepPer, stepPer*i));
            corners.add(new Point(image.cols(), image.rows()));
            corners.add(new Point(0, image.rows()));

            r.add(ImageUtil.changeImagePerspective(image, originals, corners));
        }
        return r;
    }

    static public ImageFeature extractRobustFeatures(Mat img, int num, DescriptorType type) {
        MatOfKeyPoint kps = new MatOfKeyPoint();
        Mat des = new Mat();
        FeatureDetector.getInstance().extractFeatures(img, kps, des, type);
        return extractRobustFeatures(img, FeatureDetector.getInstance().distortImage(img), num, 300, type);
    }

    static public ImageFeature extractRobustFeatures(Mat img, List<Mat> distortedImg, int num, int disThd, DescriptorType type) {
        return extractRobustFeatures(img, distortedImg, num, disThd, type, null);
    }
    //Extract robust image feature points with customized setting
    static public ImageFeature extractRobustFeatures(Mat img, List<Mat> distortedImg, int num, int disThd, DescriptorType type, List<Integer> minTracker) {
        ImageFeature imageFeature = null;
        switch (type) {
            case SURF: imageFeature = extractSURFFeatures(img); break;
            default:
            case ORB: imageFeature = extractORBFeatures(img); break;
        }
        return extractRobustFeatures(imageFeature, distortedImg, num, disThd, type, minTracker);
    }

    //Extract robust image feature points with customized setting
    static public ImageFeature extractRobustFeatures(ImageFeature tIF, List<Mat> distortedImg, int num, int disThd, DescriptorType type, List<Integer> minTracker) {
        MatOfKeyPoint kps = new MatOfKeyPoint();
        kps.fromList(tIF.getObjectKeypoints().toList());
        Mat des = new Mat();
        tIF.getDescriptors().copyTo(des);
        FeatureDetector.getInstance().extractRobustFeatures(distortedImg, kps, des, type, num, disThd, minTracker);
        return new ImageFeature(kps, des, type);
    }

    /*
    Extract image feature points
     */
    static public ImageFeature extractORBFeatures(Mat img) {
        MatOfKeyPoint kps = new MatOfKeyPoint();
        Mat des = new Mat();
        FeatureDetector.getInstance().extractORBFeatures(img, kps, des);
        return new ImageFeature(kps, des, DescriptorType.ORB);
    }

    /*
    Extract image feature points with ORB detector, the bound of the number of feature points is num
     */
    static public ImageFeature extractORBFeatures(Mat img, int num) {
        MatOfKeyPoint kps = new MatOfKeyPoint();
        Mat des = new Mat();
        FeatureDetector fd = new FeatureDetector(num);
        fd.extractORBFeatures(img, kps, des);
        return new ImageFeature(kps, des, DescriptorType.ORB);
    }

    /*
    Extract image feature points
     */
    static public ImageFeature extractFeatures(Mat img) {
        return extractORBFeatures(img);
    }

    /*
    Extract image feature points
     */
    static public ImageFeature extractSURFFeatures(Mat img) {
        MatOfKeyPoint kps = new MatOfKeyPoint();
        Mat des = new Mat();
        FeatureDetector.getInstance().extractSurfFeatures(img, kps, des);
        return new ImageFeature(kps, des, DescriptorType.SURF);
    }

    /*
    Match two images
     */
    static public MatOfDMatch matchImages(ImageFeature qIF, ImageFeature tIF) {
        if (qIF.getDescriptorType() != tIF.getDescriptorType()) {
            System.out.print("Can't match different feature descriptor types");
            return null;
        }
        return FeatureMatcher.getInstance().matchFeature(qIF.getDescriptors(), tIF.getDescriptors(),
                qIF.getObjectKeypoints(), tIF.getObjectKeypoints(), qIF.getDescriptorType());
//        return FeatureMatcher.getInstance().BFMatchFeature(qIF.getDescriptors(), tIF.getDescriptors());
    }

    static public MatOfDMatch matchImages(Mat queryImg, Mat temImg) {
        ImageFeature qIF = extractFeatures(queryImg);
        ImageFeature tIF = extractFeatures(temImg);
        return matchImages(qIF, tIF);
    }

    /*
    Match two images
     */
    static public MatOfDMatch BFMatchImages(ImageFeature qIF, ImageFeature tIF) {
        if (qIF.getDescriptorType() != tIF.getDescriptorType()) {
            System.out.print("Can't match different feature descriptor types");
            return null;
        }
        return FeatureMatcher.getInstance().BFMatchFeature(qIF.getDescriptors(), tIF.getDescriptors(), qIF.getDescriptorType());
    }

    static public KeyPoint findKeyPoint(ImageFeature templateF, int idx){
        return findKeyPoint(templateF.getObjectKeypoints(),idx);
    }

    static public KeyPoint findKeyPoint(MatOfKeyPoint mkp, int idx){
        KeyPoint[] kps=mkp.toArray();
        return kps[idx];
    }

    static public MatOfDMatch matchWithRegression(ImageFeature qIF, ImageFeature tIF, int knnNum, float matchDisThd, int posThd) {
        if (qIF.getDescriptorType() != tIF.getDescriptorType()) {
            System.out.print("Can't match different feature descriptor types");
            return null;
        }
        return FeatureMatcher.getInstance().matchWithRegression(qIF.getDescriptors(), tIF.getDescriptors(),
                qIF.getObjectKeypoints(), tIF.getObjectKeypoints(), qIF.getDescriptorType(), knnNum, matchDisThd, posThd);
    }

    static public MatOfDMatch matchWithRegression(ImageFeature qIF, ImageFeature tIF) {
        return matchWithRegression(qIF, tIF, 5, 300, 20);
    }

    static MatOfDMatch matchWithDistanceThreshold(ImageFeature qIF, ImageFeature tIF, int disThd) {
        MatOfDMatch m = ImageProcessor.BFMatchImages(qIF, tIF);
//                MatOfDMatch m = ImageProcessor.BFMatchImages(qIF, tIF);
//                MatOfDMatch m = ImageProcessor.matchImages(qIF, tIF);
//                MatOfDMatch m = ImageProcessor.matchWithRegression(qIF, tIF);
        List<DMatch> mL = new ArrayList<>();
//                List<DMatch> mL = m.toList();
        Map<Integer, List<DMatch>> recorder = new HashMap<>();

        for (DMatch match : m.toList()) {
            //filter out those unqualified matches
            if (match.distance < disThd) {
                if (recorder.get(match.trainIdx) == null) {
                    recorder.put(match.trainIdx, new ArrayList<>());
                }
                recorder.get(match.trainIdx).add(match);
//                        mL.add(match);
            }
        }
        //if multiple query points are matched to the same template point, keep the match with minimum distance
        for (Integer i : recorder.keySet()) {
            DMatch minDisMatch = null;
            float minDis = Float.MAX_VALUE;
            for (DMatch dMatch : recorder.get(i)) {
                if (dMatch.distance < minDis) {
                    minDisMatch = dMatch;
                    minDis = dMatch.distance;
                }
            }
            if (minDisMatch != null)
                mL.add(minDisMatch);
        }

//        //display matches
//        mL.sort((o1, o2) -> {
//            return (int) (o1.trainIdx - o2.trainIdx);
//        });
        MatOfDMatch ret = new MatOfDMatch();
        ret.fromList(mL);
        return ret;
    }
}

