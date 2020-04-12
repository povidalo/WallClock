package ru.povidalo.dashboard.fragment;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.VideoView;

import java.io.File;
import java.util.List;

import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.Utils;

public class EndlessVideoFragment extends FSFragment implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private static final File VIDEO_BASE_PATH = new File(Environment.getExternalStorageDirectory(), "videos");

    private VideoView videoView;
    private List<File> videoUrls;
    private File currentVideo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        videoView = (VideoView) inflater.inflate(R.layout.video_fragment, container, false);
        videoView.setOnErrorListener(this);
        videoView.setOnCompletionListener(this);

        checkPermission();

        return videoView;
    }

    @Override
    protected void onPermissionApproved() {
        startNextVideo();
    }

    private void startNextVideo() {
        if (videoUrls == null) {
            videoUrls = getFileList(VIDEO_BASE_PATH, "mkv", "mp4", "ts", "avi");
        }

        if (videoUrls.size() > 0) {
            currentVideo = videoUrls.get((int) (Math.random()*videoUrls.size()));

            if (currentVideo.isFile()) {
                Utils.log("Loading bg movie "+currentVideo.getAbsolutePath());
                videoView.pause();
                videoView.setVideoURI(Uri.fromFile(currentVideo));
                videoView.start();
            } else {
                Utils.logError("No bg movie at "+currentVideo);
                onError(null, 0,0);
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Utils.logError("Error playing bg movie "+currentVideo);
        videoUrls.remove(currentVideo);
        if (videoUrls.isEmpty()) {
            videoUrls = null;
        }
        startNextVideo();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        startNextVideo();
    }
}
