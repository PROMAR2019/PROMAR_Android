package com.google.ar.sceneform.samples.promar;

import android.app.DialogFragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link SavingFeatureDialog.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link SavingFeatureDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SavingFeatureDialog extends DialogFragment{


    private OnFragmentInteractionListener mListener;
    private Integer count=0;
    private TextView countTextView;


    private Thread timer_thread;

    public Handler threadHandler = new Handler(){
        public void handleMessage (android.os.Message message){
            if(getView()==null) return;
            countTextView = (TextView) getView().findViewById(R.id.sftext);
            if(countTextView!=null) countTextView.setText("Saving features... "+Float.toString((float)count/10)+"s");
        }

    };
    public SavingFeatureDialog() {
        // Required empty public constructor

        timer_thread=new Thread(
                new Runnable() {
                    private static final int DELAY = 100;
                    @Override
                    public void run() {
                        try {
                            while (true) {
                                count ++;
                                Thread.sleep (DELAY);
                                threadHandler.sendEmptyMessage(0);
                            }
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                }

        );
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_saving_feature_dialog, container, false);
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }

        timer_thread.start();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        timer_thread.interrupt();
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
