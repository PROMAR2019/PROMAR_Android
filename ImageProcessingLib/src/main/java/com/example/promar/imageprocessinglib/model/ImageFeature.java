package com.example.promar.imageprocessinglib.model;

import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;

public class ImageFeature {
    private final MatOfKeyPoint objectKeypoints;
    private final Mat descriptors;
    private final DescriptorType descriptorType;

    public ImageFeature(MatOfKeyPoint objectKeypoints, Mat descriptors) {
        this.objectKeypoints = objectKeypoints;
        this.descriptors = descriptors;
        this.descriptorType = DescriptorType.ORB;
    }

    public ImageFeature(MatOfKeyPoint objectKeypoints, Mat descriptors, DescriptorType descriptorType) {
        this.objectKeypoints = objectKeypoints;
        this.descriptors = descriptors;
        this.descriptorType = descriptorType;
    }

    public MatOfKeyPoint getObjectKeypoints() {
        return objectKeypoints;
    }

    public Mat getDescriptors() {
        return descriptors;
    }

    public DescriptorType getDescriptorType() {
        return descriptorType;
    }

    public int getSize(){return objectKeypoints.rows();}

    public ImageFeature subImageFeature(int start, int end) {
        MatOfKeyPoint nKP = new MatOfKeyPoint(objectKeypoints.submat(start, end, 0, objectKeypoints.cols()));
        Mat nDes = descriptors.submat(start, end, 0, descriptors.cols());
        return new ImageFeature(nKP, nDes, descriptorType);
    }
}
