package cn.campusapp.longimagedemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.io.IOException;

import cn.campusapp.longimageview.LongImageView;

public class MainActivity extends AppCompatActivity {

    View.OnClickListener listener, listener1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final LongImageView liv = (LongImageView) findViewById(R.id.long_image_view);
        if (liv == null) {
            return;
        }
        liv.setDebugMode(true);
        try {
            // 这张图在首次设置时会显示为黑白图片, 但是第二次调用 invalidate() 后就变成彩色了
            liv.setImage(getAssets().open("beyond_earth.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    liv.setImage(getAssets().open("super_long.png"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                liv.setOnClickListener(listener1);
            }
        };

        listener1 = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    liv.setImage(getAssets().open("beyond_earth.jpg"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                liv.setOnClickListener(listener);
            }
        };

        liv.setOnClickListener(listener);
    }
}
