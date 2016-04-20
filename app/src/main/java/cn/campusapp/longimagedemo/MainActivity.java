package cn.campusapp.longimagedemo;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager pager = (ViewPager) findViewById(R.id.view_pager);
        if (null == pager) {
            return;
        }

        PagerAdapter adapter = new FragmentStatePagerAdapter(getSupportFragmentManager()) {
            private final String[] mAssetImages = {"beyond_earth.jpg", "super_long.png"};

            @Override
            public int getCount() {
                return mAssetImages.length;
            }

            @Override
            public Fragment getItem(int position) {
                return ImageFragment.newInstance(mAssetImages[position]);
            }
        };

        pager.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }
}
