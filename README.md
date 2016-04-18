LongImageView

[![](https://jitpack.io/v/campusappcn/LongImageView.svg)](https://jitpack.io/#campusappcn/LongImageView)

A view to show long image.

Features:

1. Long image display + scroll
2. Double click to zoom in/out
3. Multi-touch to zoom in/out

Usage:

```XML

<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- set drawable from xml -->
    <cn.campusapp.longimageview.LongImageView
        android:id="@+id/long_image_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:src="@drawable/some_drawable"/>
</RelativeLayout>
```

```Java

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final LongImageView liv = (LongImageView) findViewById(R.id.long_image_view);
        // 1. set drawable resource id
        liv.setImage(R.drawable.some_drawable);
        // 2. set input stream
        liv.setImage(getAssets().open("some_picture.jpg")); // only supports "jpg"/"jpeg" and "png" format
        // 3. set bitmap
        liv.setImage(/* a bitmap*/);
        // 4. set file
        liv.setImage(new File("/data/local/tmp/some_image.jpg"));
        // 5. set drawable
        liv.setImage(ContextCompat.getDrawable(this, R.drawable.some_drawable));
        // 6. set path
        liv.setImage("/data/local/tmp/some_image.jpg");
    }
}
```
