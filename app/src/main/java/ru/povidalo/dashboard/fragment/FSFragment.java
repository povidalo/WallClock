package ru.povidalo.dashboard.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.povidalo.dashboard.util.Utils;

public abstract class FSFragment extends Fragment {
    private static final int MY_PERMISSIONS_REQUEST = 8797;

    protected void checkPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST);
        } else {
            onPermissionApproved();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Utils.log("Permission granted");
                    onPermissionApproved();
                } else {
                    Utils.log("Permission not granted");
                }
                break;
        }
    }

    protected abstract void onPermissionApproved();

    protected List<File> getFileList(File rootDir, String... extensions) {
        return getFileList(rootDir, new HashSet<>(Arrays.asList(extensions)));
    }

    protected List<File> getFileList(File rootDir, Set<String> extensions) {
        List<File> urls = new ArrayList<>();

        if (rootDir != null) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File f : rootDir.listFiles()) {
                    if (f.isFile()) {
                        String lowercasedPath = f.getPath().toLowerCase();
                        lowercasedPath = lowercasedPath.substring(lowercasedPath.lastIndexOf(".") + 1);
                        if (extensions.contains(lowercasedPath)) {
                            urls.add(f);
                        }
                    } else if (f.isDirectory()) {
                        urls.addAll(getFileList(f, extensions));
                    }
                }
            }
        }

        return urls;
    }
}