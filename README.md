LongImageView

[![](https://jitpack.io/v/campusappcn/LongImageView.svg)](https://jitpack.io/#campusappcn/LongImageView)

A view to show long image.

Usage:

```
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <cn.campusapp.longimageview.LongImageView
        android:id="@+id/long_image_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
</RelativeLayout>
```

```Java

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final LongImageView liv = (LongImageView) findViewById(R.id.long_image_view);

    }
}
```