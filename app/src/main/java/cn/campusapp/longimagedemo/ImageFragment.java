package cn.campusapp.longimagedemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

import cn.campusapp.longimageview.LongImageView;

/**
 * Created by chen on 16/4/20.
 */
public class ImageFragment extends Fragment {
    private static final String KEY_ASSET_NAME = "assetName";

    public static ImageFragment newInstance(@NonNull String assetName) {
        Bundle args = new Bundle();
        args.putString(KEY_ASSET_NAME, assetName);
        ImageFragment fragment = new ImageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Context context = container.getContext();
        final LongImageView longImageView = new LongImageView(container.getContext());
        try {
            longImageView.setImage(context.getAssets().open(getArguments().getString(KEY_ASSET_NAME, "super_long.png")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return longImageView;
    }
}
