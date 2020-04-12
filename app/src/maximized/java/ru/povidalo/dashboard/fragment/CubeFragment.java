package ru.povidalo.dashboard.fragment;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ru.povidalo.dashboard.openGL.CubeRenderer;

/**
 * Created by povidalo on 29.06.18.
 */

public class CubeFragment extends Fragment {
    private GLSurfaceView mGLView;
    private CubeRenderer  renderer;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mGLView = new GLSurfaceView(getActivity());
    
        final ActivityManager   activityManager   = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean           supportsEs2       = configurationInfo.reqGlEsVersion >= 0x20000;
    
        if (supportsEs2) {
            mGLView.setEGLContextClientVersion(2);
            renderer = new CubeRenderer(getActivity());
            mGLView.setRenderer(renderer);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Error")
                    .setMessage("No OpenGL ES 2.0 available!")
                    .setCancelable(true)
                    .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() { // Кнопка ОК
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                        }
                    }).create().show();
        }
        
        return mGLView;
    }
    
    @Override
    public void onPause() {
        super.onPause();
        renderer.onPause();
        mGLView.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        renderer.onResume();
        mGLView.onResume();
    }
}
