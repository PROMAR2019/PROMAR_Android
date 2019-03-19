package com.example.promar.imageprocessinglib.model;

import com.example.promar.imageprocessinglib.ImageProcessor;
import com.example.promar.imageprocessinglib.feature.FeatureStorage;
import com.example.promar.imageprocessinglib.util.StorageUtil;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.Serializable;
import java.util.UUID;

/**
 * An immutable result returned by a recognizer describing what was recognized.
 */
public final class Recognition implements Serializable {
    private static String TAG = StorageUtil.RECOGNITION_TAG;

    //Unique identifier for this object
    private String uuid;

    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final Integer id;
    private final String title;
    private final Float confidence;
    private BoxPosition location;
    private int modelSize;
//    private Mat img=null;

    public Recognition(final Integer id, final String title,
                       final Float confidence, final BoxPosition location, int modelSize) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
        this.location = location;
        this.modelSize = modelSize;
        uuid = UUID.randomUUID().toString();
    }

    public int getModelSize() {
        return modelSize;
    }

    public Integer getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public Float getConfidence() {
        return confidence;
    }

    public BoxPosition getScaledLocation(final float scaleX, final float scaleY) {
        return new BoxPosition(location, scaleX, scaleY);
    }

//    public Mat getPixels(){ return img;}

    public BoxPosition getLocation() {
        return new BoxPosition(location);
    }

    public void setLocation(BoxPosition location) {
        this.location = location;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "Recognition{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", confidence=" + confidence +
                ", location=" + location +
                '}';
    }

    public Mat cropPixels(Mat oriImage, int modelInSize) {
        float scaleX = (float) oriImage.size().width / modelInSize;
        float scaleY = (float) oriImage.size().height / modelInSize;

        BoxPosition slocation = getScaledLocation(scaleX, scaleY);
        Rect rect = new Rect(slocation.getLeftInt(), slocation.getTopInt(), slocation.getWidthInt(), slocation.getHeightInt());
//        img = oriImage.getSubimage(slocation.getLeftInt(),slocation.getTopInt(),slocation.getWidthInt(),slocation.getHeightInt());
//        img = new Mat(oriImage, rect);
        return new Mat(oriImage, rect);
    }

    public void savePixels(Mat oriImage, int modelInSize) {
        Mat pixels = cropPixels(oriImage, modelInSize);
        savePixels(pixels);
    }

    public void savePixels(Mat image) {
        Imgcodecs.imwrite(TAG + "_" + uuid + ".png", image);
//        BufferedImage img = ImageUtil.Mat2BufferedImage(pixels);
//        ImageUtil.saveImage(img, TAG + "_" + uuid + ".jpg" );
    }

    //read image from storage
    public Mat loadPixels() {
        return Imgcodecs.imread(TAG + "_" + uuid + ".png" );
    }

    //extract recognized object's feature points
    public ImageFeature extractFeature(Mat oriImage, int modelInSize) {
        return extractFeature(cropPixels(oriImage, modelInSize));
    }

    //extract recognized object's feature points
    public ImageFeature extractFeature(Mat croppedImage) {
        return ImageProcessor.extractFeatures(croppedImage);
    }

    public void saveFeature(Mat oriImage, int modelInSize) {
        Mat pixels = cropPixels(oriImage, modelInSize);
        saveFeature(pixels);
    }

    public void saveFeature(Mat croppedImage) {
        ImageFeature imageFeature = ImageProcessor.extractFeatures(croppedImage);
        saveFeature(imageFeature);
    }

    public void saveFeature(ImageFeature imageFeature) {
        FeatureStorage fs = new FeatureStorage();
        String filePath = TAG + "_" + "feature_" + uuid + ".xml";
        fs.saveFPtoFile(filePath, imageFeature);

    }

    //read image from storage
    public ImageFeature loadFeature() {
        FeatureStorage fs = new FeatureStorage();
        String filePath = TAG + "_" + "feature_" + uuid + ".xml";
        return fs.loadFPfromFile(filePath);
    }
}
