package com.google.ar.sceneform.samples.promar;

import android.view.MotionEvent;

import com.google.ar.core.CameraConfig;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.List;

public class MyArFragment extends ArFragment {
    private PromarMainActivity activity=null;
    private boolean configured=false;

    FrameListener listener = null;

    void setAutoFocus(){
        Session arSession = getArSceneView().getSession();
        Config config = arSession.getConfig();
        config.setFocusMode(Config.FocusMode.AUTO);
        arSession.configure(config);
    }


    void setResolution(){
        Session arSession = getArSceneView().getSession();
        List<CameraConfig> ccl = arSession.getSupportedCameraConfigs();
        CameraConfig c = null;
        int w = 0;
        for (CameraConfig cc : ccl) {
            if (w < cc.getImageSize().getWidth()) {
                c = cc;
                w = cc.getImageSize().getWidth();
            }
        }
        arSession.pause();
        arSession.setCameraConfig(c);
        try {
            arSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
    }

    public interface FrameListener {
        void onFrame(FrameTime frameTime, Frame frame);
    }

    public void setOnFrameListener(FrameListener listener) {
        this.listener = listener;
    }

    @Override
    public void onPeekTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
        super.onPeekTouch(hitTestResult, motionEvent);
        //if((hitTestResult.getNode()!=null)&&(activity!=null)) activity.onPeekTouch();
        if(activity!=null) activity.onPeekTouch();
    }

    void setActivity(PromarMainActivity a){
        activity=a;
    }

    @Override
    public void onUpdate(FrameTime frameTime){
        /*** add a listener ***/

        if(!configured){
            setAutoFocus();
            setResolution();
            configured=true;
        }

        super.onUpdate(frameTime);

        Frame arFrame = getArSceneView().getArFrame();
        if (listener != null) {
            listener.onFrame(frameTime, arFrame);
        }


    }
}
