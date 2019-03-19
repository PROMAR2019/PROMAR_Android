package com.example.promar.imageprocessinglib;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import com.example.promar.imageprocessinglib.model.BoxPosition;
import com.example.promar.imageprocessinglib.model.Recognition;
import com.example.promar.imageprocessinglib.tensorflow.Classifier;
import com.example.promar.imageprocessinglib.tensorflow.TensorFlowMultiBoxDetector;
import com.example.promar.imageprocessinglib.tensorflow.TensorFlowObjectDetectionAPIModel;
import com.example.promar.imageprocessinglib.tensorflow.TensorFlowYoloDetector;
import com.example.promar.imageprocessinglib.util.ImageUtil;

//import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class ObjectDetector {
    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
    // or YOLO.
    public enum DetectorMode {
        TF_OD_API, MULTIBOX, YOLO;
    }

    // Configuration values for the prepackaged multibox model.
    private static final int MB_INPUT_SIZE = 224;
    private static final int MB_IMAGE_MEAN = 128;
    private static final float MB_IMAGE_STD = 128;
    private static final String MB_INPUT_NAME = "ResizeBilinear";
    private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
    private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
    private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
    private static final String MB_LOCATION_FILE = "file:///android_asset/multibox_location_priors.txt";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE = "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
    private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    private static final int YOLO_BLOCK_SIZE = 32;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
    private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;
    private Matrix cropToFrameTransform;
    private Matrix frameToCropTransform;
//    private Bitmap croppedBitmap;

    private Classifier detector;
    private float minConfidence = 0.5f;
    public int  cropSize;   //the size of cropped image, which is the input of tensorflow model

    /*
    init TensorFlow
     */
    public void init(final Context context) {
        AssetManager assetManager = context.getAssets();

//        //if(tracker==null)
//        tracker = new MultiBoxTracker(this);

        cropSize = TF_OD_API_INPUT_SIZE;
        if (MODE == DetectorMode.YOLO) {
            detector =
                    TensorFlowYoloDetector.create(
                            assetManager,
                            YOLO_MODEL_FILE,
                            YOLO_INPUT_SIZE,
                            YOLO_INPUT_NAME,
                            YOLO_OUTPUT_NAMES,
                            YOLO_BLOCK_SIZE);
            cropSize = YOLO_INPUT_SIZE;
            minConfidence = MINIMUM_CONFIDENCE_YOLO;
        } else if (MODE == DetectorMode.MULTIBOX) {
            detector =
                    TensorFlowMultiBoxDetector.create(
                            assetManager,
                            MB_MODEL_FILE,
                            MB_LOCATION_FILE,
                            MB_IMAGE_MEAN,
                            MB_IMAGE_STD,
                            MB_INPUT_NAME,
                            MB_OUTPUT_LOCATIONS_NAME,
                            MB_OUTPUT_SCORES_NAME);
            cropSize = MB_INPUT_SIZE;
            minConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
        } else {
            try {
                detector = TensorFlowObjectDetectionAPIModel.create(
                        assetManager, TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
                cropSize = TF_OD_API_INPUT_SIZE;
                minConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

//        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
    }

    /*
    Process image recognition and save cropped object image
     */
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        Bitmap croppedBitmap = Bitmap.createScaledBitmap(bitmap, cropSize, cropSize, true);
//        Bitmap croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
//        final Canvas canvas = new Canvas(croppedBitmap);
//        canvas.drawBitmap(bitmap, frameToCropTransform, null);
        final List<Recognition> ret = new LinkedList<>();
        List<Recognition> recognitions = detector.recognizeImage(croppedBitmap);
//        long before=System.currentTimeMillis();
//        for (int k=0; k<100; k++) {
//        recognitions = detector.recognizeImage(croppedBitmap);
//            int a = 0;
//        }
//        long after=System.currentTimeMillis();
//        String log = String.format("tensorflow takes %.03f s\n", ((float)(after-before)/1000/100));
//        Log.d("MATCH TEST", log);
        for (Recognition r : recognitions) {
            if (r.getConfidence() < minConfidence)
                continue;
//            RectF location = r.getLocation();
//            cropToFrameTransform.mapRect(location);
//            r.setOriginalLoc(location);
//            Bitmap cropped = Bitmap.createBitmap(bitmap, (int)location.left, (int)location.top, (int)location.width(), (int)location.height());
//            r.setObjectImage(cropped);
            BoxPosition bp = r.getLocation();
//            r.rectF = new RectF(bp.getLeft(), bp.getTop(), bp.getRight(), bp.getBottom());
//            cropToFrameTransform.mapRect(r.rectF);
            ret.add(r);
        }

        return ret;
    }

    /*
    Process image recognition and save cropped object image
     */
    public List<Recognition> recognizeImage(Mat img) {
        frameToCropTransform =
                ImageUtil.getTransformationMatrix(
                        img.cols(), img.rows(),
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
        Bitmap tBM = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, tBM);
        return recognizeImage(tBM);
    }
}
