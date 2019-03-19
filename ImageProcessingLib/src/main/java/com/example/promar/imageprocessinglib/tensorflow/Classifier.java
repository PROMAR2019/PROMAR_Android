package com.example.promar.imageprocessinglib.tensorflow;

import android.graphics.Bitmap;
import java.util.List;
import com.example.promar.imageprocessinglib.model.Recognition;

/**
 * Generic interface for interacting with different recognition engines.
 */
public interface Classifier {
    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();
}
