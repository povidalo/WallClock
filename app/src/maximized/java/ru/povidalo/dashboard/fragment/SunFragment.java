package ru.povidalo.dashboard.fragment;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.alphamovie.lib.AlphaMovieView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.command.SunLoaderCommand;
import ru.povidalo.dashboard.util.Event;
import ru.povidalo.dashboard.util.Utils;

/**
 * Created by povidalo on 29.06.18.
 */

public class SunFragment extends Fragment implements MediaPlayer.OnErrorListener {
    private AlphaMovieView videoView;
    private File currentVideoFile = null;
    private int retriesCount = 3;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        videoView = (AlphaMovieView) inflater.inflate(R.layout.sun_widget, container, false);
        videoView.setOnErrorListener(this);
        showMovie(null);
        
        return videoView;
    }

    @Override
    public void onResume() {
        super.onResume();
        videoView.onResume();
        videoView.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        videoView.onPause();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void movieUpdated(Event.SunMovieUpdated event) {
        retriesCount = 3;
        showMovie(event.getFilePath());
    }
    
    private void showMovie(String file) {
        if (videoView != null) {
            retriesCount--;
            File videoFile = file != null ? new File(file) : SunLoaderCommand.getCurrentMovieFile();
            if (videoFile != null && videoFile.isFile()) {

                Utils.log("Loading SUN movie "+videoFile.getAbsolutePath());
                videoView.setVisibility(View.VISIBLE);
                videoView.stop();
                videoView.setVideoFromUri(getContext(), Uri.fromFile(videoFile));
                videoView.setLooping(true);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    videoView.getMediaPlayer().setPlaybackParams(videoView.getMediaPlayer().getPlaybackParams().setSpeed(2f));
                }
                videoView.start();
                
                if (currentVideoFile != null && !videoFile.getAbsolutePath().equals(currentVideoFile.getAbsolutePath()) && currentVideoFile.isFile()) {
                    Utils.log("Delete prev SUN movie");
                    currentVideoFile.delete();
                }
                currentVideoFile = videoFile;
            } else {
                onError(null, 0,0);
                Utils.logError("No SUN movie at "+videoFile);
            }
        }
    }
    
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        videoView.setVisibility(View.INVISIBLE);
        Utils.logError("Error playing SUN movie "+currentVideoFile);
        if (retriesCount > 0) {
            videoView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showMovie(null);
                }
            }, 2000);
        }
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (videoView != null) {
            videoView.stop();
            videoView = null;
        }
        super.onDestroy();
    }
}
