/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.promar;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SizeF;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.samples.promar.env.BorderedText;
import com.google.ar.sceneform.samples.promar.env.ImageUtils;
import com.google.ar.sceneform.samples.promar.env.Size;
import com.google.ar.sceneform.samples.promar.tracking.MultiBoxTracker;
import com.google.ar.sceneform.ux.TransformableNode;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.promar.imageprocessinglib.ImageProcessor;
import com.example.promar.imageprocessinglib.ObjectDetector;
import com.example.promar.imageprocessinglib.feature.FeatureStorage;
import com.example.promar.imageprocessinglib.model.BoxPosition;
import com.example.promar.imageprocessinglib.model.DescriptorType;
import com.example.promar.imageprocessinglib.model.ImageFeature;
import com.example.promar.imageprocessinglib.model.Recognition;

// main activity of Promar Android Demo
/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class PromarMainActivity extends AppCompatActivity implements SensorEventListener, SavingFeatureDialog.OnFragmentInteractionListener {
    private  static final String TAG = "MAIN_DEBUG";
    private static final int OWNER_STATE=1, VIEWER_STATE=2;
    private static final double MIN_OPENGL_VERSION = 3.1;

    //fixed file name for storing metadata of image features and recognitions
    private static final String dataFileName = "data_file";

    private int state=OWNER_STATE;

    private TransformableNode andy;

    private float v_viewangle=60, h_viewangle=48;

    private float VO_dist=0, VO_dist_for_viewer=0, v_dist=0;

    //image recognition object as key, value is a list of image features list recognized as this object by TF.
    //Each element is a distortion robust image feature, sorted as left, right, top and bottom
    private Map<String,List<List<ImageFeature>>> rs;
    private Map<String,List<BoxPosition>> bs; //store position
    Size imgSize;

    private MyArFragment arFragment;
    private ModelRenderable andyRenderable;

    private Session arSession;

    private float last_chk_time=0;
    private boolean opencvLoaded=false;
    //    private Classifier classifier;
    private ObjectDetector objectDetector;

    private OverlayView trackingOverlay;
    /*** from tensorflow sample code***/
    private Handler handler;
    private long timestamp = 0; //it's actually a counter
    private Bitmap cropCopyBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap rgbFrameBitmap=null;
    private Bitmap copyBitmp = null;

    private HandlerThread handlerThread;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private ImageView imgView;

    private Integer sensorOrientation;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Matrix frameToDisplayTransform;
    private int rotation=90;

    private MultiBoxTracker tracker;

    private byte[] luminanceCopy;
    private List<Recognition> recognitions;

    //    private Classifier detector;
    private BorderedText borderedText;

    static Boolean onRecord = false;
    static Boolean onRetrieve = false;

//    private final PlaneRenderer planeRenderer = new PlaneRenderer(new Renderer(new ));

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }
        OpenCVLoader.initDebug();

        //calculate filed of views
        setFOV();

        setContentView(R.layout.activity_ux);
        arFragment = (MyArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.getPlaneDiscoveryController().hide();
        arFragment.getPlaneDiscoveryController().setInstructionView(null);
        imgView = findViewById(R.id.imgview);

        //orientation sensor manager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(
                Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mRotationVectorSensor, 10000);

        Display display = this.getWindowManager().getDefaultDisplay();
        int stageWidth = display.getWidth();
        int stageHeight = display.getHeight();

        //ImageView imgview=findViewById(R.id.imgview);

        //imgview.setImageResource(R.drawable.ic_launcher);

        arFragment.setActivity(this);
        arFragment.setOnFrameListener((frameTime, frame) -> {
            float curTime=frameTime.getStartSeconds();
            Bitmap bitmap=null;//Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
            Image img=null;
            //if(curTime-last_chk_time<2) return;
            if(frame==null) {Log.d(TAG,"frame is null"); return;}
            try {
                img = frame.acquireCameraImage(); //catch the image from camera
                String msg = img.getFormat()+":"+Integer.toString(img.getWidth())+","+Integer.toString(img.getHeight());
                //setImage(img);

                luminanceCopy = MyUtils.imageToByte(img); //convert image to byte[]

                bitmap=MyUtils.imageToBitmap(img);

//                bitmap=Bitmap.createScaledBitmap(bitmap, 360,640,false);


                img.close();
                //if(bitmap!=null) setImage(bitmap);
                //else return;
            }
            catch(Exception e){
                return;
            }

//            if(detector==null) initTF(bitmap);
            if(objectDetector==null) {
                initTF(bitmap);
                initDistParameters();
            }

            processImage(bitmap);

        });



        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (andyRenderable == null) {
                        return;
                    }


                    float x = motionEvent.getRawX();
                    float y = motionEvent.getRawY();
                    float x1 = motionEvent.getX();
                    float y1 = motionEvent.getY();
                    float x2 = motionEvent.getXPrecision();
                    float y2 = motionEvent.getYPrecision();

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                    float[] xs = anchor.getPose().getXAxis();
                    float[] ys = anchor.getPose().getYAxis();
                    float[] zs = anchor.getPose().getZAxis();
                    Vector3 localPosition = anchorNode.getLocalPosition();
                    Vector3 worldPosition = anchorNode.getWorldPosition();

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
                    andy.setParent(anchorNode);
                    andy.setRenderable(andyRenderable);
                    andy.select();

//                    float[] mAnchorMatrix = new float[16];
//                    anchor.getPose().toMatrix(mAnchorMatrix, 0);
//                    objectRenderer.updateModelMatrix(mAnchorMatrix, 1);
//                    objectRenderer.draw(cameraView, cameraPerspective, lightIntensity);
//
//                    float[] centerVertexOf3dObject = {0f, 0f, 0f, 1};
//                    float[] vertexResult = new float[4];
//                    Matrix.multiplyMV(vertexResult, 0,
//                            objectRenderer.getModelViewProjectionMatrix(), 0,
//                            centerVertexOf3dObject, 0);
//// circle hit test
//                    float radius = (viewWidth / 2) * (cubeHitAreaRadius/vertexResult[3]);
//                    float dx = event.getX() - (viewWidth / 2) * (1 + vertexResult[0]/vertexResult[3]);
//                    float dy = event.getY() - (viewHeight / 2) * (1 - vertexResult[1]/vertexResult[3]);
//                    double distance = Math.sqrt(dx * dx + dy * dy);
                });

        Button recBtn = findViewById(R.id.record);  //record button
        Button rteBtn = findViewById(R.id.retrieve);    //retrieve button
        recBtn.setTag("Place VO");
        rteBtn.setTag("Retrieve");
        recBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                SeekBar sbar=findViewById(R.id.seekBar);
                Button btn=(Button) view;
                String tag=(String)btn.getTag();
                if(tag.equals("Place VO")) {
                    placeAndy();
                    runOnUiThread(()-> {
                        btn.setText("Confirm");
                        btn.setTag("Confirm");

                        sbar.setProgress(50);
                        sbar.setVisibility(View.VISIBLE);

                    });

                }
                else{
                    rs=null;//delete previous data
                    onRecord = true;
                    runOnUiThread(()-> {
                        btn.setTag("Place VO");
                        btn.setText("Place VO");
                        sbar.setVisibility(View.INVISIBLE);
                    });


                }
            }
        });
        rteBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                AsyncTask.execute(()->{
                    Button btn=(Button) view;
                    String tag=(String)btn.getTag();
                    if(tag.equals("Retrieve")) {

                        loadData();
                        onRetrieve = true;
                        runOnUiThread(()-> {
                            btn.setText("Clear");
                            btn.setTag("Clear");
                            //btn.setEnabled(false);
                        });
                    }
                    else{
                        onRetrieve=false;
                        runOnUiThread(()-> {
                            btn.setTag("Retrieve");
                            btn.setText("Retrieve");
                            andy.setParent(null);
                        });

                    }

                });
            }
        });


        RadioButton rb=findViewById(R.id.rb_owner);
        rb.setChecked(true);
        rteBtn.setEnabled(false);

        RadioGroup radioGroup=findViewById(R.id.rg_role);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = (RadioButton) group.findViewById(checkedId);
                String msg= "Switch to "+ rb.getText();
                if (null != rb ) {
                    Toast.makeText(PromarMainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
                if(rb.getId()==R.id.rb_owner){
                    recBtn.setEnabled(true);
                    rteBtn.setEnabled(false);
                    state=OWNER_STATE;
                    onRetrieve=false;
                }else{
                    recBtn.setEnabled(false);
                    rteBtn.setEnabled(true);
                    state=VIEWER_STATE;
                    andy.setParent(null);
                }
            }
        });

        SeekBar sbar=findViewById(R.id.seekBar);
        sbar.setMax(100);
        sbar.setMin(0);
        sbar.setVisibility(View.INVISIBLE);

        sbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                            @Override
                                            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                                                float dist=(float)((i-50)*0.01+1);
                                                VO_dist=dist;
                                                placeAndyWithDist(dist);
                                            }

                                            @Override
                                            public void onStartTrackingTouch(SeekBar seekBar) {

                                            }

                                            @Override
                                            public void onStopTrackingTouch(SeekBar seekBar) {

                                            }
                                        }


        );
    }

    //FOV (rectilinear) =  2 * arctan (frame size/(focal length * 2))
    void setFOV() {
        //suppose there is only one camera
        int camNum = 0;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            for (String id : cameraIds) {
//            if (cameraIds.length > camNum) {
//                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIds[camNum]);
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
//                size = character.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    float w = size.getWidth();
                    float h = size.getHeight();
                    h_viewangle = (float) (2 * Math.atan(w / (maxFocus[0] * 2)))/(float)(2*Math.PI)*360;
                    v_viewangle = (float) (2 * Math.atan(h / (maxFocus[0] * 2)))/(float)(2*Math.PI)*360;
                }
            }
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    void loadData() {
        if (rs==null) {
            FeatureStorage fs = new FeatureStorage();
            String data = MyUtils.readFromFile(dataFileName, this);
            rs = new HashMap<>();
            bs = new HashMap<>();
            String[] recStrs = data.split("\n");
            String[] os = recStrs[0].split(" ");
            String dirPath = getFilesDir().getPath();
            //get orientation data
            refRD = new RotationData(new Float(os[0]),new Float(os[1]),new Float(os[2]));
            imgSize = new Size(new Integer(os[3]), new Integer(os[4]));
            VO_dist_for_viewer= Float.parseFloat(os[5]);
            for (int i=1; i<recStrs.length; i++) {
//                data.append("\n" + r.getTitle() + "\t" + r.getConfidence() + "\t" + r.getUuid() //recognition
//                        + "\t" + location.getTop() + "\t" + location.getLeft() + "\t" + location.getBottom() + "\t" + location.getRight()); //location
                String r = recStrs[i];
                String[] rec = r.split("\t");
                if (rs.get(rec[0]) == null)
                    rs.put(rec[0], new ArrayList<>());
                if (bs.get(rec[0]) == null)
                    bs.put(rec[0], new ArrayList<>());
                //restore image features
                String fName = dirPath + "/" + rec[2];
                List<ImageFeature> IFs = new ArrayList<>();
                IFs.add(fs.loadFPfromFile(fName + "_left"));
                IFs.add(fs.loadFPfromFile(fName + "_right"));
                IFs.add(fs.loadFPfromFile(fName + "_top"));
                IFs.add(fs.loadFPfromFile(fName + "_bottom"));
                IFs.add(fs.loadFPfromFile(fName + "_scale_up"));
                IFs.add(fs.loadFPfromFile(fName + "_scale_down"));
                rs.get(rec[0]).add(IFs);
                bs.get(rec[0]).add(new BoxPosition(new Float(rec[4]), new Float(rec[3]),
                        new Float(rec[6])-new Float(rec[4]),new Float(rec[5])-new Float(rec[3])));
                //bs.put(rec[0],new BoxPosition(new Float(rec[4]), new Float(rec[3]),
                //        new Float(rec[6])-new Float(rec[4]),new Float(rec[5])-new Float(rec[3])));
            }
        }
        runOnUiThread(()->{
            Toast.makeText(getApplicationContext(), "Data loaded", Toast.LENGTH_SHORT).show();
            Button btn= findViewById(R.id.retrieve);
            btn.setEnabled(true);
        });
    }

    void initDistParameters() {
        float width=previewHeight;
        float height=previewWidth;
        float v_dist_center_x=(float) (width/2/Math.tan(h_viewangle/2/180*Math.PI)); //virtual distance to the center of the cameraview
        float v_dist_center_y=(float) (height/2/Math.tan(v_viewangle/2/180*Math.PI)); //virtual distance to the center of the cameraview
        Log.d("match_strings",String.format("width:%.02f,height:%.02f",width, height));
        Log.d("match_strings","dist_center:"+Float.toString(v_dist_center_x)+" "+Float.toString(v_dist_center_y));
        //TODO:how about adding v_dist_center_y? Why their value varied so much
        v_dist=v_dist_center_x;//(v_dist_center_x+v_dist_center_y)/2; //distance in units of pixels
    }

    void initTF(Bitmap bitmap) {
        previewWidth = bitmap.getWidth();
        previewHeight = bitmap.getHeight();
        sensorOrientation = rotation - getScreenOrientation();
        objectDetector = new ObjectDetector();
        objectDetector.init(this);

        //last_chk_time=curTime;
        /*** from detector activity in tensorflow sample code***/
//        final float textSizePx =
//                TypedValue.applyDimension(
//                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
//        borderedText = new BorderedText(textSizePx);
//        borderedText.setTypeface(Typeface.MONOSPACE);
//
        //if(tracker==null)
        tracker = new MultiBoxTracker(this);




//        sensorOrientation = rotation - getScreenOrientation();
//        /*
//        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
//
//        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
//        */
//        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
//
        croppedBitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        copyBitmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
//
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        300, 300,
                        sensorOrientation, false);
//        frameToCropTransform = new Matrix();
//        frameToCropTransform.postRotate(sensorOrientation);
//        frameToCropTransform =
//                ImageUtils.getTransformationMatrix(
//                        previewWidth, previewHeight,
//                        previewWidth, previewHeight,
//                        sensorOrientation, MAINTAIN_ASPECT);
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
//
//        /**/
//        float h = arFragment.getView().getHeight();//1944
//        float w = arFragment.getView().getWidth();//1080
        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
//        float dpHeight = 1005;//trackingOverlay.getHeight() / 2;
//        float dpWidth = 540;//trackingOverlay.getWidth() / 2;
//        //75 is the height of image view
////        frameToDisplayTransform =
////                ImageUtils.getTransformationMatrix(
////                        previewWidth, previewHeight,
////                        (int)dpHeight, (int)dpWidth,
////                        0, MAINTAIN_ASPECT);
        //track object in AR fragment view
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
//                        tracker.drawDebug(canvas);
                    }
                });
    }

    long timeStamp = 0;
    static private long kInterval = 500;

    void processImage(Bitmap bitmap) {
        if(bitmap==null) return;

        //byte[] originalLuminance = getLuminance();

        ++timestamp;
        final long currTimestamp = timestamp;

        if (luminanceCopy == null) {
            //luminanceCopy = new byte[originalLuminance.length];
            return;
        }

        tracker.onFrame(
                previewWidth,
                previewHeight,
                previewWidth, //stride is the same as previewWidth
                sensorOrientation,
                luminanceCopy,
                timestamp);
//        trackingOverlay.postInvalidate();
        //System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);

        //stop the background thread when program is halted
        if (System.currentTimeMillis() - timeStamp > kInterval && handler != null) {
            timeStamp = System.currentTimeMillis();
        } else return;

        final Canvas canvas = new Canvas(croppedBitmap);
//        final Canvas canvas = new Canvas(rgbFrameBitmap);
        canvas.drawBitmap(bitmap, frameToCropTransform, null);

//        Bitmap copy = Bitmap.createBitmap(bitmap);
//        Bitmap copy = bitmap.copy(bitmap.getConfig(), true);
        final Canvas c = new Canvas(copyBitmp);
//        final Canvas canvas = new Canvas(rgbFrameBitmap);
        c.drawBitmap(bitmap, new Matrix(), null);

//        setImage(croppedBitmap);
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        final long startTime = System.currentTimeMillis();
                        final List<Recognition> results = objectDetector.recognizeImage(croppedBitmap);
//                        final List<Recognition> results = objectDetector.recognizeImage(rgbFrameBitmap);
                        long endTime = System.currentTimeMillis();
//
                        org.opencv.core.Rect roi = new org.opencv.core.Rect();
//
                        for (final Recognition result : results) {
                            BoxPosition pos = result.getLocation();
                            final RectF location = new RectF(pos.getLeft(), pos.getTop(), pos.getRight(), pos.getBottom());
//                            roi = new org.opencv.core.Rect((int)location.left, (int)location.top, (int)(location.right - location.left), (int)(location.bottom - location.top));
                            cropToFrameTransform.mapRect(location);
                            roi = new org.opencv.core.Rect((int)location.left, (int)location.top, (int)(location.right - location.left), (int)(location.bottom - location.top));
                            result.setLocation(new BoxPosition(location.left, location.top, location.width(), location.height()));
                        }

                        if (results.size() > 0) {

                            if (onRecord) {
                                record(copyBitmp, results);
                            }
                            else if (onRetrieve) {
                                retrieve(copyBitmp, results);
                            }
//                            Mat mat = new Mat();
//                            Utils.bitmapToMat(copyBitmp, mat);
//                            Mat cropMat = new Mat(mat, roi);
//                            Bitmap tBM = Bitmap.createBitmap(roi.width, roi.height, Bitmap.Config.ARGB_8888);
//                            Utils.matToBitmap(cropMat, tBM);
//                            mat.release();
//                            cropMat.release();
//                            setImage(tBM);
//                            copy.recycle();
                        }
                        else if (onRecord) {
                            runOnUiThread(()->{
                                Toast.makeText(getApplicationContext(), "There is no recognized object in frame", Toast.LENGTH_SHORT).show();
                            });
                            onRecord = false;
                        }
//                        RectF rectF = new RectF(9, 79, 283, 216);//previewWidth, previewHeight;
//                        cropToFrameTransform.mapRect(rectF);
//                        RectF rectF = new RectF(0, 0, previewWidth, previewHeight);
//                        Classifier.Recognition result = new Classifier.Recognition("1434", "test", 0.99f, rectF);
//                        mappedRecognitions.add(result);

//                        str=String.format("mapped:%d, cropped image size(%d, %d)",mappedRecognitions.size(), bitmap.getWidth(), bitmap.getHeight());
//                        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                        tracker.trackResults(results, luminanceCopy, currTimestamp);
//                        if (mappedRecognitions.size() > 0)
                        if (results.size() > 0)
                            trackingOverlay.postInvalidate();
                    }
                });
        bitmap.recycle();
    }

    static int kTemplateFPNum = 100;
    static int kDisThd = 400;
    private void record(Bitmap img, List<Recognition> recognitions) {
        runOnUiThread(()->{
            FragmentManager fm=getFragmentManager();
            SavingFeatureDialog sf=new SavingFeatureDialog();
            sf.show(fm, "sf_dialog");
        });

        setAngle();
//        FeatureStorage fs = new FeatureStorage();
        Mat mat = new Mat();
        Utils.bitmapToMat(img, mat);
        StringBuilder data = new StringBuilder();
        data.append(refRD);
        data.append(" " + img.getWidth() + " " + img.getHeight()+" " + VO_dist);
        String dirPath = getFilesDir().getPath();
        AtomicInteger c = new AtomicInteger(0); //used to count the completed threads

        for (Recognition r : recognitions) {
            BoxPosition location = r.getLocation();
            Rect roi = new Rect(location.getLeftInt(), location.getTopInt(), location.getWidthInt(), location.getHeightInt());
            Mat tMat = new Mat(mat, roi);

            data.append("\n" + r.getTitle() + "\t" + r.getConfidence() + "\t" + r.getUuid() //recognition
                    + "\t" + location.getTop() + "\t" + location.getLeft() + "\t" + location.getBottom() + "\t" + location.getRight()); //location

            new Thread(() -> {
                FeatureStorage fs = new FeatureStorage();
                fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_left",
                        ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.changeToLeftPerspective(tMat, 5f, 10),
                                kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_right",
                        ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.changeToRightPerspective(tMat, 5f, 10),
                                kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                c.getAndIncrement();
            }).start();
            new Thread(() -> {
                FeatureStorage fs = new FeatureStorage();
            fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_bottom",
                    ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.changeToBottomPerspective(tMat, 5f, 10),
                            kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_top",
                        ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.changeToTopPerspective(tMat, 5f, 10),
                                kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                c.getAndIncrement();
            }).start();
            new Thread(() -> {
                FeatureStorage fs = new FeatureStorage();
                fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_scale_up",
                        ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.scaleImage(tMat, 0.05f, 10),
                                kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                fs.saveFPtoFile( dirPath + "/" + r.getUuid() + "_scale_down",
                        ImageProcessor.extractRobustFeatures(tMat, ImageProcessor.scaleImage(tMat, -0.05f, 10),
                                kTemplateFPNum, kDisThd, DescriptorType.ORB, null));
                c.getAndIncrement();
            }).start();
        }
        MyUtils.writeToFile(dataFileName, data.toString(), this);

        int count = 3*recognitions.size();
        Handler handler = new Handler();
        int kInterval = 2;

        Runnable statusCheck = new Runnable() {
            @Override
            public void run() {
                if (c.get() >= count) {
                    runOnUiThread(()->{
                        Toast.makeText(getApplicationContext(), "Image features saved", Toast.LENGTH_SHORT).show();
                        FragmentManager fm=getFragmentManager();
                        SavingFeatureDialog sf=(SavingFeatureDialog)fm.findFragmentByTag("sf_dialog");
                        if(sf!=null) sf.dismiss();;
                    });
                }
                else handler.postDelayed(this, kInterval);
            }
        };
        statusCheck.run();

        onRecord = false;
    }

    static float kConThd = 0.6f;

    private void retrieve(Bitmap img, List<Recognition> recognitions) {

        double mr_th=0.15; //matching ratio threshold
        boolean match=false;
        Mat mat = new Mat();
        Utils.bitmapToMat(img, mat);
        Set<String> recs = rs.keySet();
        StringBuilder sb = new StringBuilder();
        //horizontal angle difference, positive stands for right perspective, negative for left perspective
        float hd = (Math.abs(cRD.z - refRD.z)>180)?(360-(cRD.z-refRD.z)) : (cRD.z-refRD.z);
        //vertical angle difference, assume the angle can't exceed 90
        float vd = cRD.x-refRD.x;
        float vo_x=0;
        float vo_y=0;
        float scale=0;

        if (Math.abs(hd) > 90 || Math.abs(vd) > 90)
            sb.append("angle difference larger than 90 degree");
        else {
            int count_r=0;

            MatOfDMatch m = null;
            ImageFeature qmIF = null;
            ImageFeature tmIF = null;
            for (Recognition r : recognitions) {
                if (r.getConfidence()<kConThd) continue;
                double mr=0; //temporarily save the matching ratio
                if (recs.contains(r.getTitle())) {
                    BoxPosition location = r.getLocation();
                    Rect roi = new Rect(location.getLeftInt(), location.getTopInt(), location.getWidthInt(), location.getHeightInt());
                    Mat qMat = new Mat(mat, roi);
                    ImageFeature qIF = ImageProcessor.extractORBFeatures(qMat, 500);
                    List<List<ImageFeature>> tIFs = rs.get(r.getTitle());
                    List<BoxPosition> tBPs = bs.get(r.getTitle());
                    int match_idx=-1;
                    for (int i=0; i < tIFs.size(); i++) {
                        List<ImageFeature> ts = tIFs.get(i);
                        BoxPosition bp = tBPs.get(i);
                        //construct template image feature candidates
                        List<ImageFeature> ifs = new ArrayList<>();
                        float area_ratio = bp.getHeight()*bp.getWidth() / (r.getLocation().getWidth()*r.getLocation().getHeight());
                        if (hd > 0)
                            ifs.add(ts.get(1));
                        else ifs.add(ts.get(0));
                        if (vd>0)
                            ifs.add(ts.get(3));
                        else ifs.add(ts.get(2));
                        if (area_ratio > 1)
                            ifs.add(ts.get(5));
                        else ifs.add(ts.get(4));

                        long startTime = System.currentTimeMillis();

                        ImageFeature tIF = constructTemplateFP(ifs, new float[]{Math.abs(hd)/45, Math.abs(vd)/45, Math.abs(area_ratio-1)}, kTemplateFPNum);
                        MatOfDMatch matches = ImageProcessor.matchWithRegression(qIF, tIF, 5, 400, 20);

                        long endTime = System.currentTimeMillis();

                        double tmr = (double) matches.total() / tIF.getSize();
                        sb.append(r.getTitle() + " " + tmr + ",");
                        if (tmr > mr){
                            mr = tmr;
                            match_idx=tIFs.indexOf(ts);
                            m = matches;
                            qmIF = qIF;
                            tmIF = tIF;
                        }
                    }


                    //derive the position of the VO
                    if (mr > mr_th) {
                        match = true;
                        List<BoxPosition> bpList=bs.get(r.getTitle());
                        BoxPosition bp = bpList.get(match_idx);
                        if (bp == null) return;

                        double tmin_x, tmin_y, tmax_x, tmax_y;
                        double qmin_x, qmin_y, qmax_x, qmax_y;
                        qmax_x = qmax_y = tmax_x = tmax_y = Double.MIN_VALUE;
                        qmin_x = qmin_y = tmin_x = tmin_y = Double.MAX_VALUE;

                        List<KeyPoint> tKP = tmIF.getObjectKeypoints().toList();
                        List<KeyPoint> qKP = qmIF.getObjectKeypoints().toList();
                        //get a rectangle that can bound the matched key point
                        for (DMatch dMatch : m.toList()) {
                            KeyPoint q = qKP.get(dMatch.queryIdx);
                            KeyPoint t = tKP.get(dMatch.trainIdx);
                            if (q.pt.x > qmax_x) qmax_x = q.pt.x;
                            if (q.pt.y > qmax_y) qmax_y = q.pt.y;
                            if (t.pt.x > tmax_x) tmax_x = t.pt.x;
                            if (t.pt.y > tmax_y) tmax_y = t.pt.y;
                            if (q.pt.x < qmin_x) qmin_x = q.pt.x;
                            if (q.pt.y < qmin_y) qmin_y = q.pt.y;
                            if (t.pt.x < tmin_x) tmin_x = t.pt.x;
                            if (t.pt.y < tmin_y) tmin_y = t.pt.y;
                        }
                        float dx = (float)(imgSize.height/2 -(tmax_x+tmin_x)/2);
                        float dy = (float)(imgSize.width/2 -(tmax_y+tmin_y)/2);
                        float r_scale = (float)((tmax_x-tmin_x) / (qmax_x-qmin_x) + (tmax_y-tmin_y) / (qmax_y-qmin_y)) / 2;
                        float r_center_x = (float)(qmax_x+qmin_x) / 2;
                        float r_center_y = (float)(qmax_y+qmin_y) / 2;

                        double radiant_v = vd/180*Math.PI;
                        double radiant_h = hd/180*Math.PI;
                        dy = angleChangeHelper(dy, r_center_y, r_scale, (float)radiant_v);
                        dx = angleChangeHelper(dx, r_center_x, r_scale, (float)radiant_h);

                        vo_x += r_center_x + dx * r_scale;
                        vo_y += r_center_y + dy * r_scale;
                        scale += r_scale;
                        count_r++;
                    }
                }
            }
            if(match) {//use average value for multiple recognitions
                vo_x = vo_x / count_r;
                vo_y = vo_y / count_r;
                scale= scale / count_r;
            }
        }
        if(match) {

            float finalScale = scale;
            float finalVo_x = vo_x;
            float finalVo_y = vo_y;


            runOnUiThread(() -> {

//                DisplayMetrics displayMetrics = new DisplayMetrics();
//                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//                float width = displayMetrics.heightPixels;
//                float height = displayMetrics.widthPixels;

                float width=previewHeight;
                float height=previewWidth;
                float v_dist_center= (float)Math.sqrt((finalVo_x -width/2)*(finalVo_x -width/2)+(finalVo_y -height/2)*(finalVo_y -height/2));
                float v_angle=(float)Math.atan(v_dist_center/v_dist);
//                float x_angle= (float)Math.atan((finalVo_x -width/2)/v_dist);//x angle of the VO
//                float y_angle= (float)Math.atan((finalVo_y -height/2)/v_dist);

                float x,y,z;
                float dist_to_pixel= (float) (VO_dist_for_viewer * finalScale * Math.cos(v_angle) / v_dist);
                z = -v_dist*dist_to_pixel;
                x= (finalVo_x-width/2)*dist_to_pixel;
                y= (finalVo_y-height/2)*dist_to_pixel;
                Log.d("match_strings",String.format("before placeAndy:%.02f,%.02f,%.02f",x,y,z));
                //prevent unrealistic cases
                if (Math.abs(x*y) > 1) return;
                placeAndy(x, y, z);
                onRetrieve=false;

                TextView tv = findViewById(R.id.mratio);
                tv.setText(sb.toString() + String.format("(%f,%f,%f)", x, y, z));
            });
        }
    }

    //theta: the change of the view angle
    float angleChangeHelper(float d_ro_ori, float d_ro, float scale, float theta){
        float h_ro = v_dist * scale;
        double beta = Math.atan(d_ro/h_ro);
        double alhpa = 90-beta+theta;
        double l = h_ro / Math.cos(beta);
        double m = Math.sqrt(d_ro_ori*d_ro_ori+l*l-2*d_ro_ori*l*Math.cos(alhpa));
        double zeta = Math.acos((m*m+l*l-d_ro_ori*d_ro_ori)/(2*m*l));
        float dx = (float)Math.tan(zeta-beta)*h_ro+d_ro;
        return dx;
    }

    static class KPoint{
        double x,y;
        boolean selected =false;
        int[] idx;

        int idx1, idx2;
        KPoint(double x, double y, int len){
            this.x=x;this.y=y;
            idx=new int[len];
            Arrays.fill(idx,-1);
        }

        KPoint(double x, double y){
            this.x=x;this.y=y;
        }
        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof KPoint)) return false;
            KPoint kobj=(KPoint) obj;
            return ((kobj.getX()==x)&&(kobj.getY()==y));
        }

        public double getX() {
            return x;
        }


        public double getY() {
            return y;
        }

        public void setIdx(int kp_list_idx, int fp_idx) {
            idx[kp_list_idx]=fp_idx;
        }


        public int getIdx(int i) {
            return idx[i];
        }



        public void setSelected(boolean selected){
            this.selected=selected;
        }



        public boolean isSelected(){ return selected;}

        public boolean isInList(int i) {
            return (idx[i]>=0);
        }

    }

    static ImageFeature constructTemplateFP(List<ImageFeature> tIFs, float[] weights, int tNum) {
        //calculate ratios
        //float hr = Math.abs(hd)/(Math.abs(hd) + Math.abs(vd));
        //float vr = Math.abs(vd)/(Math.abs(hd) + Math.abs(vd));
        //ImageFeature IF1=tIFs.get(0); //horizontal
        //ImageFeature IF2=tIFs.get(1); //vertical

        float sum=0;
        for(float f : weights) sum+=f;
        for(int i=0;i<weights.length;i++) weights[i]=weights[i]/sum;

        for (int i=0; i<weights.length; i++)
            if (weights[i]==1f)
                return (tIFs.get(i).getSize()>tNum)? tIFs.get(i).subImageFeature(0, tNum) : tIFs.get(i);

        List<KeyPoint> kp= new ArrayList<>();//(IF1.getObjectKeypoints().toList());
        Mat des = new Mat();//new Size(IF1.getDescriptors().cols(),tNum), IF1.getDescriptors().type());
        //des.push_back(IF1.getDescriptors());

        //List<KeyPoint> kp1 = IF1.getObjectKeypoints().toList();
        //List<KeyPoint> kp2 = IF2.getObjectKeypoints().toList();

        List<List<KeyPoint>> kp_list = new ArrayList();
        for(int i=0;i<tIFs.size();i++){
            kp_list.add((tIFs.get(i).getObjectKeypoints().toList()));
        }


        List<KPoint> distKPs=new ArrayList<>(); //distinct key points

        for(int i=0;i<kp_list.size();i++){
            for(int j=0;j<kp_list.get(i).size();j++){
                KeyPoint k1= kp_list.get(i).get(j);
                KPoint tkp=new KPoint(k1.pt.x, k1.pt.y, kp_list.size());
                tkp.setIdx(i,j);
                int idx=distKPs.indexOf(tkp);
                if(idx<0) {
                    distKPs.add(tkp);
                }else{
                    distKPs.get(idx).setIdx(i,j);
                }
            }
        }

        int[] c_list=new int[kp_list.size()];
        int[] p_list=new int[kp_list.size()];
        int sum_c=0;
        while( kp.size()<tNum){
            KeyPoint k;
            float max_deficit=-1;
            int candidate_idx=-1;
            for(int i=0;i<c_list.length;i++){
                int n_sum= (sum_c==0)? tNum : sum_c;
                //System.out.printf("%d:%.02f,%.02f\n",i,weights[i],(float)c_list[i]/n_sum);

                float deficit=weights[i]-(float)c_list[i]/n_sum;
                //add the feature points of list with largest deficit when it still has candidates
                if(deficit>max_deficit && kp_list.get(i).size() > p_list[i]) {
                    max_deficit=deficit;
                    candidate_idx=i;
                }
            }

            if (candidate_idx==-1) break;//can't add candidate anymore

            k=kp_list.get(candidate_idx).get(p_list[candidate_idx]++);

            KPoint kkp=new KPoint(k.pt.x,k.pt.y);
            int idx=distKPs.indexOf(kkp);
            if(idx<0) System.out.println("sth is wrong, idx<0");
            kkp=distKPs.get(idx);
            if(kkp.isSelected()){
                continue;
            }

            for(int i=0;i<c_list.length;i++){
                if(kkp.isInList(i)){
                    c_list[i]++;
                    sum_c++;
                }
            }
            kkp.setSelected(true);

            kp.add(k);

            Mat tMat=null;
            for(int i=0;i<kp_list.size();i++){
                if(kkp.isInList(i)){
                    int rowidx=kkp.getIdx(i);
                    tMat=tIFs.get(i).getDescriptors().row(rowidx);
                    break;
                    //System.out.printf("kp_idx:%d,row_idx:%d\n",i,rowidx);
                }
            }
            /*if(kkp.isInFirst()){
                tMat = IF1.getDescriptors().row(kkp.getIdx1());
            }else if(kkp.isInSecond()){
                tMat = IF2.getDescriptors().row(kkp.getIdx2());
            }else{System.out.println("sth. is wrong, not in 1 or 2");}*/
            des.push_back(tMat);
            //System.out.println(des.size().toString());
            //System.out.printf("d1:%.02f, d2:%.02f, p1:%d,p2:%d,kp:%d\n",deficit1,deficit2,p1,p2,kp.size());
        }

        MatOfKeyPoint tKP = new MatOfKeyPoint();
        tKP.fromList(kp);
        //System.out.printf("construct FP size: %d, %s\n", kp.size(),des.size().toString());
        return new ImageFeature(tKP, des, tIFs.get(0).getDescriptorType());
    }

    public void onPeekTouch (){

        return;
/*
        if (andyRenderable == null) {
//        if (true) {
            return;
        }

        Camera camera=arFragment.getArSceneView().getArFrame().getCamera();
        Pose mCameraRelativePose= Pose.makeTranslation(0.0f, 0.0f, -1f);
        arSession = arFragment.getArSceneView().getSession();


        Pose cPose = camera.getPose().compose(mCameraRelativePose).extractTranslation();
        if(cPose!=null)
        Anchor anchor=arSession.createAnchor(cPose);


        //copy&paste
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());


        // Create the transformable andy and add it to the anchor.
        TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
        andy.setParent(anchorNode);
        andy.setRenderable(andyRenderable);
        andy.select();
*/
    }


    void placeAndy(){
        if (andyRenderable == null) {
//        if (true) {
            return;
        }

        Camera camera=arFragment.getArSceneView().getArFrame().getCamera();
        Pose mCameraRelativePose= Pose.makeTranslation(0.0f, 0.0f, -1f);
        arSession = arFragment.getArSceneView().getSession();


        Pose cPose = camera.getPose().compose(mCameraRelativePose).extractTranslation();
        Anchor anchor=arSession.createAnchor(cPose);
        //copy&paste
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable andy and add it to the anchor.
        if(andy==null) andy = new TransformableNode(arFragment.getTransformationSystem());
        placeAndy(anchorNode);
    }

    void placeAndyWithDist(float dist){
        if (andyRenderable == null) {
//        if (true) {
            return;
        }

        Camera camera=arFragment.getArSceneView().getArFrame().getCamera();
        arSession = arFragment.getArSceneView().getSession();

        float y=(float) (dist*Math.tan(Math.PI/6));
        Pose mCameraRelativePose= Pose.makeTranslation(0.0f, 0, -dist);

        Pose cPose = camera.getPose().compose(mCameraRelativePose).extractTranslation();
        Anchor anchor=arSession.createAnchor(cPose);

        //copy&paste
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        if(andy==null) andy = new TransformableNode(arFragment.getTransformationSystem());
        placeAndy(anchorNode);
    }

    void placeAndy(float x, float y, float z){
        Camera camera=arFragment.getArSceneView().getArFrame().getCamera();
        Pose mCameraRelativePose= Pose.makeTranslation(x, y, z);
        arSession = arFragment.getArSceneView().getSession();

        Pose cPose = camera.getPose().compose(mCameraRelativePose).extractTranslation();
        Anchor anchor=arSession.createAnchor(cPose);

        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        if(andy==null) andy = new TransformableNode(arFragment.getTransformationSystem());

        placeAndy(anchorNode);
    }

    void placeAndy(AnchorNode an){
        andy.setParent(an);
        andy.setRenderable(andyRenderable);
        andy.select();
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.1 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.1 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.1 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    //miniature image view set image
    void setImage(Bitmap image){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imgView.setImageBitmap(image);
            }
        });
    }

    void setImage(Image image){
        if(!opencvLoaded) return;
        Mat mat=  MyUtils.imageToMat(image);

        //ImageView imgview=findViewById(R.id.imgview);
//      Bitmap bitmap=Bitmap.createBitmap(image.getWidth(),  image.getHeight(),Bitmap.Config.ARGB_8888);
        Bitmap bitmap=Bitmap.createBitmap(mat.cols(),  mat.rows(), Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(mat,bitmap);


        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        //imgview.setImageBitmap(rotatedBitmap);
    }



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            opencvLoaded=false;
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    opencvLoaded=true;
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        arFragment.onResume();
    }


    public void addCallback(final OverlayView.DrawCallback callback) {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }

    @Override
    public synchronized void onPause() {

//        handlerThread.quitSafely();
        handlerThread.quit();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
//            LOGGER.e(e, "Exception!");
           Log.e(TAG, "Can't stop handler thread");
        }
        arFragment.onPause();

        super.onPause();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }


    public void requestRender() {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }


    //Codes below this line is for orientation monitor
    private RotationData refRD =null;
    private RotationData cRD=null;
    private SensorManager mSensorManager;
    private Sensor mRotationVectorSensor;
    private boolean checkAngle=false, firstValue=false;

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
    public void onSensorChanged(SensorEvent event) {
        if (firstValue) {
            refRD =new RotationData(event.values);
            firstValue=false;
        } else {
            cRD=new RotationData(event.values);
            displayData(cRD);
        }
    }

    void displayData(RotationData temp){
        TextView textView=findViewById(R.id.cangle);
        if (refRD != null)
            textView.setText(refRD.toString()+", "+temp.toString());
    }

    void setAngle(){
        refRD = cRD;    //just in case cRD is re-assigned value
        checkAngle=true;
        firstValue=true;
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    private class RotationData{
        private float x,y,z,cos;
        private static final int FROM_RADS_TO_DEGS = -57;

        RotationData(float x, float y, float z){
            this.x = x;
            this.y = y; //
            this.z = z;
        }

        RotationData(float[] values){
//            x=values[0];
//            y=values[1];
//            z=values[2];
//            cos=values[3];
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, values);
            int worldAxisX = SensorManager.AXIS_X;
            int worldAxisZ = SensorManager.AXIS_Z;
            float[] adjustedRotationMatrix = new float[9];
            SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisX, worldAxisZ, adjustedRotationMatrix);
            float[] orientation = new float[3];
            SensorManager.getOrientation(adjustedRotationMatrix, orientation);
            float pitch = orientation[1] * FROM_RADS_TO_DEGS;
            float roll = orientation[2] * FROM_RADS_TO_DEGS;
            float azimuth = orientation[0] * FROM_RADS_TO_DEGS;
            x = pitch;  //top,bottom perspective. -90~90.
            y = roll;   //rotation on same vertical plane. -180~180
            z = azimuth;    //left,right perspective. -180~180
        }

        public String toString(){
            //return Float.toString(x)+" "+Float.toString(y)+" "+Float.toString(z);

//            return String.format("%.02f %.02f %.02f",Math.asin(x)/Math.PI*180,Math.asin(y)/Math.PI*180,Math.asin(z)/Math.PI*180);
            return String.format("%.02f %.02f %.02f", x, y, z);
            //return Double.toString(Math.asin(x))+" "+Double.toString(Math.asin(y))+" "+Double.toString(Math.asin(z));
        }
    }
}
