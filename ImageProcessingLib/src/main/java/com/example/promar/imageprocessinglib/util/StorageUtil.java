package com.example.promar.imageprocessinglib.util;

import com.example.promar.imageprocessinglib.model.Recognition;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class StorageUtil {

    public static String RECOGNITION_TAG="Recognition";

    public static void saveRecognitionToFile(Recognition rec, String filename) {
        try {

            FileOutputStream fileOut = new FileOutputStream(filename);

            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);

            objectOut.writeObject(rec);

            objectOut.close();

            fileOut.close();

        }catch(Exception e){System.out.println(e);}
    }

    public static Recognition readRecognitionFromFile(String filename){
        Recognition ret=null;
        try {
            FileInputStream fileIn = new FileInputStream(filename);

            ObjectInputStream objectIn = new ObjectInputStream(fileIn);

            ret = (Recognition)objectIn.readObject();

            objectIn.close();

            fileIn.close();

        }catch(Exception e){System.out.println(e);}

        return ret;
    }

    public static Mat readMatFromFile(String filename){

        return Imgcodecs.imread(filename);
    }


}
