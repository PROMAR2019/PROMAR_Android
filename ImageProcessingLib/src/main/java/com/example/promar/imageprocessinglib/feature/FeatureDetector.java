package com.example.promar.imageprocessinglib.feature;

import com.example.promar.imageprocessinglib.model.DescriptorType;
import com.example.promar.imageprocessinglib.util.ImageUtil;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.ORB;
import org.opencv.xfeatures2d.SURF;

import java.util.*;
import java.util.stream.Collectors;


public class FeatureDetector {
    private static final int        kMaxFeatures = 500;

    //    private FastFeatureDetector     FAST;
    private SURF surf;
    private ORB orb;

    private static final FeatureDetector ourInstance = new FeatureDetector();

    public static FeatureDetector getInstance() {
        return ourInstance;
    }

    //init ORB detector with specific limit on feature number,
    // this constructor is only used for ORB detector, since no SURF detector is initialized
    public FeatureDetector(int feautureNum) {
        orb = ORB.create(feautureNum, 1.2f, 8, 15, 0, 2, ORB.HARRIS_SCORE, 31, 20);
    }

    private FeatureDetector() {
//        FAST = FastFeatureDetector.create();
        surf = SURF.create();
        surf.setHessianThreshold(400);
        orb = ORB.create(kMaxFeatures, 1.2f, 8, 15, 0, 2, ORB.HARRIS_SCORE, 31, 20);
    }

    public void extractORBFeatures(Mat img, MatOfKeyPoint keyPoints, Mat descriptors) {
        orb.detectAndCompute(img, new Mat(), keyPoints, descriptors);

    }

    public void extractSurfFeatures(Mat img, MatOfKeyPoint keyPoints, Mat descriptors) {
        surf.detectAndCompute(img, new Mat(), keyPoints, descriptors);

    }

    public void extractFeatures(Mat img, MatOfKeyPoint keyPoints, Mat descriptors, DescriptorType type) {
        switch (type) {
            case SURF:
                extractSurfFeatures(img, keyPoints, descriptors);
            case ORB:
            default:
                extractORBFeatures(img, keyPoints, descriptors);
        }
    }

    /**
     * extract feature points of images as well as its distorted images
     * @return  a list of list of integers, root list has the same size as original image features
     *          each sub-list, supposing its index is A_index, corresponds to each feature, supposing it's A_feature, in original image
     *          the integer in A_index sub-list stands for the index of distorted image which can find A_feature.
     */
    public ArrayList<HashSet<Integer>> trackFeatures(
            Mat img,
            List<Mat> distortedImages,
            MatOfKeyPoint oriKPs,
            Mat oriDes,
            List<MatOfKeyPoint> distortedKPs,
            List<Mat> distortedDes,
            List<MatOfDMatch> distortedMatches,
            DescriptorType type) {

        //calculate original image's key points and descriptors
        extractFeatures(img, oriKPs, oriDes, type);

        //record the index of images to which the key point get matched
        //ArrayList<ArrayList<Integer>> tracker = new ArrayList<>();
        ArrayList<HashSet<Integer>> tracker = new ArrayList<HashSet<Integer>>();

        for (int i = 0; i < oriDes.rows(); i++)
            tracker.add(new HashSet<Integer>());

        //calculate key points and descriptors of distorted images
        for (int i = 0; i < distortedImages.size(); i++) {
            MatOfKeyPoint k = new MatOfKeyPoint();
            Mat d = new Mat();
            extractFeatures(distortedImages.get(i), k, d, type);
            distortedKPs.add(k);
            distortedDes.add(d);
        }

        //match key points of original image to distorted images'
        for (int i = 0; i < distortedImages.size(); i++) {
            MatOfDMatch m = FeatureMatcher.getInstance().matchFeature(oriDes, distortedDes.get(i), oriKPs, distortedKPs.get(i), type);
//            MatOfDMatch m = FeatureMatcher.getInstance().BFMatchFeature(oriDes, distortedDes.get(i), type);
//            ArrayList<Integer> c = new ArrayList<>();

            //record the times that key point of original image is detected in distorted image
            List<DMatch> matches = m.toList();
            for (int d = 0; d < matches.size(); d++) {
                if (matches.get(d).distance<300) {
                    int index = matches.get(d).queryIdx;
                    tracker.get(index).add(i);
                }
            }
            distortedMatches.add(m);
        }
        return tracker;
    }

    /**
     * @param keyPoints         original template key points
     * @param descriptors       original template descriptors
     * @param type              the descriptor type
     * @param num               the number limit for returning key points
     * @param disThd            distance threshold using for filtering unqualified matches
     * @param minTracker        record how every returned key point influence the minimum matching ratio of distorted images
     * @return boolean indicates whether the method is done without problem
     */
    public boolean extractRobustFeatures(
            List<Mat> distortedImages,
            MatOfKeyPoint keyPoints,
            Mat descriptors,
            DescriptorType type,
            int num,
            int disThd,
            List<Integer> minTracker)
    {
//        ArrayList<MatOfKeyPoint> listOfKeyPoints = new ArrayList<>();
//        ArrayList<Mat> listOfDescriptors = new ArrayList<>();
//        MatOfKeyPoint kp = new MatOfKeyPoint();
//        Mat des = new Mat();
//        ArrayList<MatOfDMatch> listOfMatches = new ArrayList<>();

//        List<HashSet<Integer>> tracker = trackFeatures(img, distortedImages, kp, des, listOfKeyPoints, listOfDescriptors, listOfMatches, type);

        MatOfKeyPoint kp = keyPoints;
        Mat des = new Mat();
        List<List<Integer>> fpTrack = analyzeFPsInImages(distortedImages, descriptors, type, disThd);
        List<Integer> sizes = new ArrayList<>();
        for (List<Integer> t : fpTrack)
            sizes.add(t.size());
//        List<Integer> sizes = fpTrack.stream().map(o->o.size()).collect(Collectors.toList());
        for (int i : sizes) {
            if (i < num)
                num = i/10*10;
        }
        List<Integer> candidates = maxMin(fpTrack, num, minTracker);
        List<KeyPoint> tKP = kp.toList();
        List<KeyPoint> kpList = new ArrayList<>();
        for (int i : candidates) {
            kpList.add(tKP.get(i));
            des.push_back(descriptors.row(i));
        }
        keyPoints.fromList(kpList);
        des.copyTo(descriptors);

//        if (type == DescriptorType.SURF)
//            surf.compute(img, keyPoints, descriptors);
//        else if (type == DescriptorType.ORB)
//            orb.compute(img, keyPoints, descriptors);
        return true;

/*
print out matching results
        System.out.printf("\tqID\ttotal");
        for(int i=0;i<distortedImages.size();i++){
            System.out.printf("\tdImg%d",i);
        }
        System.out.println();
        for(int i=0;i<tracker.size();i++) {
            System.out.printf("\t%3d", i);
            HashSet<Integer> hs = tracker.get(i);
            System.out.printf("\t%5d", hs.size());
            for(int j=0;j<distortedImages.size();j++) {
                String str = "0";
                if (hs.contains(j)) {
                    str = "1";
                }
                System.out.printf("\t%5s",str);
            }
            System.out.println();
        }

        List<KeyPoint> rKeyPoints = new ArrayList<>();     //store key points that will be return
        List<KeyPoint> tKeyPoints = kp.toList();

        //create a list containing keypoints list and counter list
        List<List<Object>> merged =
                IntStream.range(0, tracker.size())
                        .mapToObj(i -> Arrays.asList((Object) tKeyPoints.get(i), tracker.get(i).size()))
                        .collect(Collectors.toList());

        //descending order sort by counter
        merged.sort((o1, o2) -> {return (Integer)o2.get(1) - (Integer)o1.get(1);});

        System.out.printf("position:(x,y)\tcounter\n");
        //remove feature points which appeared less than filterThreshold
        for (int i = 0; i < merged.size(); i++) {
//            if ((Integer)merged.get(i).get(1) > filterThreshold) {
                rKeyPoints.add((KeyPoint)merged.get(i).get(0));
            if (i == num)
                System.out.println("\n\nFeature points not taken");
            KeyPoint keyPoint = (KeyPoint)merged.get(i).get(0);
            System.out.printf("(%.2f,%.2f)\t%d\n", keyPoint.pt.x, keyPoint.pt.y, merged.get(i).get(1));
//            }
        }
        if (rKeyPoints.size() > num)
            rKeyPoints = rKeyPoints.subList(0, num);

        keyPoints.fromList(rKeyPoints);
        if (type == DescriptorType.SURF)
            surf.compute(img, keyPoints, descriptors);
        if (type == DescriptorType.ORB)
            orb.compute(img, keyPoints, descriptors);

        //release resources before return
        for (int i = 0; i < distortedImages.size(); i++) {
            distortedImages.get(i).release();
            listOfDescriptors.get(i).release();
            listOfKeyPoints.get(i).release();
        }
        kp.release();
        des.release();

        return true;
 */
    }

    //return a list of lists containing the index of matched feature points
    public List<List<Integer>> analyzeFPsInImages(List<Mat> images, Mat tDes, DescriptorType type, int disThd) {

        List<List<Integer>> ret = new ArrayList<>();

        for (Mat img : images) {
            MatOfKeyPoint k = new MatOfKeyPoint();
            Mat d = new Mat();
            extractFeatures(img, k, d, type);
            MatOfDMatch m = FeatureMatcher.getInstance().BFMatchFeature(d, tDes, type);
            List<DMatch> mL = new ArrayList<>();

            Map<Integer, List<DMatch>> recorder = new HashMap<>();
            for (DMatch match : m.toList()) {
                //filter out those unqualified matches
                if (match.distance < disThd) {
                    if (recorder.get(match.trainIdx) == null) {
                        recorder.put(match.trainIdx, new ArrayList<>());
                    }
                    recorder.get(match.trainIdx).add(match);
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
            List<Integer> tIdxs = new ArrayList<>();
            for (DMatch o : mL) {
                tIdxs.add(o.trainIdx);
            }
            ret.add(tIdxs);
//            ret.add(mL.stream().map(o->o.trainIdx).collect(Collectors.toList()));
        }
        return ret;
    }

    /**
     * Given a list of candidates, which is represent by a range from 0 to a specific number, this method finds out those
     * which can maximize the minimum counter value.
     * @param input for each target, this argument uses a list containing all qualified candidates
     * @param num   number of returned candidates
     * @param minTracker   track the Min value when keep the corresponding candidate in returned list, can be null
     * @return an integer indicating the minimum counter value and a list containing most promising candidates
     */
//    static Pair<Integer, List<Integer>> minMax(List<List<Integer>> input, int num) {
    List<Integer> maxMin(List<List<Integer>> input, int num, List<Integer> minTracker) {
        List<Integer> ret = new ArrayList<>();
        List<Integer> counters = new ArrayList<>();
        Map<Integer, Set<Integer>> tracker = new HashMap<>();   //using set rather than list just in case the input is not properly preprocessed

        //record input into a hashmap, which use candidate as key and a list containing matched target as value
        for (int i=0; i < input.size(); i++) {
            for (int k=0; k < input.get(i).size(); k++) {
                int key = input.get(i).get(k);
                if (tracker.get(key) == null) {
                    tracker.put(key, new HashSet<>());
                }
                //for every candidates, use a list to record its matched target
                tracker.get(key).add(i);
            }
        }

        for (int i=0; i < input.size(); i++)
            counters.add(0);

        int min = 0;
        while (ret.size() < num) {
            List<Integer> mins = new ArrayList<>();
            //find out minimums
            for (int i=0; i < counters.size(); i++) {
                if (counters.get(i)<=min)
                    mins.add(i);
            }
            int max = 0;
            int maxKey = -1;
            for (int i : tracker.keySet()) {
                int c = 0;  //count how many mins can get increased if this candidate is selected
                Set<Integer> ts = tracker.get(i);
//                int min_start=0;
//                for(int ii:ts){
//                    for (int j=min_start;j<mins.size();j++) {
//                        int m=mins.get(j);
//                        if(m<ii) continue;
//                        if(m>ii){
//                            min_start=j;
//                            break;
//                        }
//                        if (ii == m) {
//                            c++;
//                            min_start=j+1;
//                            break;
//                        }
//                    }
//                }
                for (int m : mins) {
                    if (ts.contains(m))
                        c++;
                }
                if (c > max) {
                    max = c;
                    maxKey = i;
                }
            }

            //no more optimization can be done, comment this condition if you wanna keep adding new candidate
            if (maxKey == -1)
                break;

            //update
            //all mins get a new matched candidate
            if (max >= mins.size())
                min++;
            if (maxKey != -1) {
                ret.add(maxKey);
                if (minTracker != null)
                    minTracker.add(min);
            }
            for (int i : tracker.get(maxKey))
                counters.set(i, counters.get(i)+1);
            tracker.remove(maxKey);
        }

//        return new Pair<Integer, List<Integer>>(min, ret);
        return ret;
    }


    /**
     * Get a group of distorted images by applying transformation on original image
     * For now only scale and rotation is applying on the image
     * @param image
     * @return          a group of distorted images
     */
    public ArrayList<Mat> distortImage(Mat image) {
        ArrayList<Mat> r = new ArrayList<>();
        r.addAll(scaleImage(image));
        r.addAll(rotateImage(image));
        r.addAll(changeImagePerspective(image));
        r.addAll(affineImage(image));

        return r;
    }

    private static final float kStepScale = 0.1f;        //the difference between scales of generating distorted images
    private static final int kNumOfScales = 6;    //the number of different scale distorted images

    /**
     * Scale original image to generate a group of distorted image
     * @param image     original image
     * @return          a list containing scaled images
     */
    private ArrayList<Mat> scaleImage(Mat image) {
        ArrayList<Mat> r = new ArrayList<>();
        for (int i = 1; i <= kNumOfScales /2; i++) {
            r.add(ImageUtil.scaleImage(image, (1 + i * kStepScale)));
            r.add(ImageUtil.scaleImage(image, (1 - i * kStepScale)));
        }

        return r;
    }

    private static final float kStepAngle = 5.0f;        //the step difference between angles of generating distorted images, in degree.
    private static final int kNumOfRotations = 6;    //the number of different rotated distorted images

    /**
     * Rotate original image to generate a group of distorted image
     * @param image     original image
     * @return          a list containing rotated images
     */
    private ArrayList<Mat> rotateImage(Mat image) {
        ArrayList<Mat> r = new ArrayList<>();
        for (int i = 1; i <= kNumOfRotations /2; i++) {
            r.add(ImageUtil.rotateImage(image, -kStepAngle * i));
            r.add(ImageUtil.rotateImage(image, kStepAngle * i));
        }

        return r;
    }

    static double kStepPerspective = 0.1;
    static int kNumOfPerspectives = 4;

    /**
     * Change original image's view perspective to generate a group of distorted image
     * @param image     original image
     * @return          a list containing rotated images
     */
    private ArrayList<Mat> changeImagePerspective(Mat image) {
        ArrayList<Mat> r = new ArrayList<>();
        List<Point> target = new ArrayList<Point>();

        //TODO: these points can be optimized
        target.add(new Point(0, 0));
        target.add(new Point(image.cols(), 0));
        target.add(new Point(image.cols(), image.rows()));
        target.add(new Point(0, image.rows()));

        for (int i = 0; i < kNumOfPerspectives/2; i++) {
            List<Point> corners = new ArrayList<>();
//            corners.add(new Point(image.cols()/5, image.rows()/5));
//            corners.add(new Point(image.cols(), image.rows()/5));
//            corners.add(new Point(image.cols()*3/4, image.rows()*3/4));
//            corners.add(new Point(image.cols()/5, image.rows()*3/4));
            //TODO: these points can be optimized
            corners.add(new Point(0, i * kStepPerspective * image.rows()));
            corners.add(new Point(image.cols(), i * kStepPerspective * image.rows()));
            corners.add(new Point(image.cols() * (1 - kStepPerspective * i), image.rows() * (1 - kStepPerspective * i)));
            corners.add(new Point(image.cols() * i * kStepPerspective, image.rows() * (1 - kStepPerspective * i)));

            r.add(ImageUtil.changeImagePerspective(image, corners, target));
            r.add(ImageUtil.changeImagePerspective(image, target, corners));
        }

        return r;
    }

    static int kStepAffine = 5;
    static int kNumOfAffines = 4;

    /**
     * Affine original image to generate a group of distorted image
     * @param image     original image
     * @return          a list containing rotated images
     */
    private ArrayList<Mat> affineImage(Mat image) {
        ArrayList<Mat> r = new ArrayList<>();
        List<Point> original = new ArrayList<>();

        //TODO: this is just a random number given without specific reason, can be optimized if possible
        original.add(new Point(10, 10));
        original.add(new Point(200,50));
        original.add(new Point(50, 200));

        MatOfPoint2f originalMat = new MatOfPoint2f();
        originalMat.fromList(original);

        for (int i = 0; i < kNumOfAffines/2; i++) {
            List<Point> targetA = new ArrayList<>();
            targetA.add(new Point(50 + i * kStepAffine, 100 + i * kStepAffine));
            targetA.add(new Point(200 + i * kStepAffine, 50 + i * kStepAffine));
            targetA.add(new Point(100 + i * kStepAffine, 250 + i * kStepAffine));

            MatOfPoint2f targetMatA = new MatOfPoint2f();
            targetMatA.fromList(targetA);

            r.add(ImageUtil.affineImage(image, original, targetA));
            r.add(ImageUtil.affineImage(image, targetA, original));
        }

        originalMat.release();

        return r;
    }


}
