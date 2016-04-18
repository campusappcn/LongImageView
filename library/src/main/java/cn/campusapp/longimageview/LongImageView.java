package cn.campusapp.longimageview;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * A view to show long image.
 * <p/>
 * Supported features:
 * <ol>
 * <li>Region decode on long image</li>
 * <li>Scroll</li>
 * <li>Zoom</li>
 * <li>Gestures</li>
 * </ol>
 * <p/>
 * Created by chen on 16/4/14.
 */
@SuppressWarnings("UnusedDeclaration")
public class LongImageView extends View {
    private static final String TAG = "LargeImageView";
    public static long MIN_FLING_DELTA_TIME = 150L;
    private final GestureListener mOnGestureListener = new GestureListener();
    private final ScaleListener mOnScaleListener = new ScaleListener();
    private final Rect mViewPort = new Rect();
    private final PointF mStartPivot = new PointF();
    private final PointF mTargetPivot = new PointF();
    private final ValueAnimator mScaleAnimator = ValueAnimator.ofFloat();
    private final Rect mBitmapRegion = new Rect();
    private AnimatorSet mFlingAnimators;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private RegionDecoder mRegionDecoder;
    private long mPointerUpTime;
    private float mMinFlingVelocity;
    private float mMaxFlingVelocity;
    private boolean mImageChanged = true;

    public LongImageView(Context context) {
        super(context);
        init(context, null);
    }

    public LongImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LongImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LongImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    static Bitmap drawableToBitmap(@NonNull Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        final Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565
        );
        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @CallSuper
    protected void init(Context context, AttributeSet attrs) {
        mMinFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mMaxFlingVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        mGestureDetector = new GestureDetector(context, mOnGestureListener);
        mScaleGestureDetector = new ScaleGestureDetector(context, mOnScaleListener);
        mScaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final RegionDecoder regionDecoder = mRegionDecoder;
                if (null == regionDecoder || mImageChanged) {
                    animation.cancel();
                    return;
                }
                final Float scale = ((Float) animation.getAnimatedValue());
                final float fraction = animation.getAnimatedFraction();
                if (scale != null) {
                    regionDecoder.scale(scale,
                            mStartPivot.x + (mTargetPivot.x - mStartPivot.x) * fraction,
                            mStartPivot.y + (mTargetPivot.y - mStartPivot.y) * fraction
                    );
                    invalidate();
                }
            }
        });

        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LongImageView);
        try {
            final Drawable drawable = ta.getDrawable(R.styleable.LongImageView_src);
            if (null != drawable) {
                setImage(drawable);
            }
        } finally {
            ta.recycle();
        }
    }

    @UiThread
    public void setImage(@NonNull InputStream is) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("must call this method on main thread");
        }
        try {
            mImageChanged = true;
            final RegionDecoder lastDecoder = mRegionDecoder;
            if (null != lastDecoder) {
                lastDecoder.close();
            }
            mRegionDecoder = new RegionDecoder(is);
            requestLayout();
            invalidate();

            // double invalidate to avoid some bugs
            post(new Runnable() {
                @Override
                public void run() {
                    postInvalidate();
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "setImage(InputStream): failed", t);
        }
    }

    @UiThread
    public void setImage(@NonNull Bitmap bitmap) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 1, byteArrayOutputStream);

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        setImage(byteArrayInputStream);
    }

    @UiThread
    public void setImage(@NonNull Drawable drawable) {
        setImage(drawableToBitmap(drawable));
    }

    @UiThread
    public void setImage(@DrawableRes int resId) {
        setImage(ContextCompat.getDrawable(getContext(), resId));
    }

    @UiThread
    public void setImage(@NonNull File file) {
        try {
            setImage(new FileInputStream(file));
        } catch (Throwable t) {
            Log.e(TAG, "setImage(File): failed", t);
        }
    }

    @UiThread
    public void setImage(@NonNull String localPath) {
        setImage(new File(localPath));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_UP) {
            mPointerUpTime = event.getEventTime();
        }

        boolean handled = mScaleGestureDetector.onTouchEvent(event);
        if (!mScaleGestureDetector.isInProgress()) {
            handled |= mGestureDetector.onTouchEvent(event);
        }

        return action == MotionEvent.ACTION_UP ? handled | handleKeyUp(event) : handled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mViewPort.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        final RegionDecoder regionDecoder = mRegionDecoder;
        if (regionDecoder != null) {
            regionDecoder.setDisplayRect(mViewPort);
        }
        mImageChanged = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final RegionDecoder regionDecoder = mRegionDecoder;
        if (regionDecoder != null) {
            Bitmap bitmap = regionDecoder.getBitmap();
            mBitmapRegion.set(regionDecoder.getRegion());
            mBitmapRegion.offsetTo(0, 0);

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, mBitmapRegion, mViewPort, null);
            }
        }
    }

    protected boolean handleKeyUp(MotionEvent event) {
        final RegionDecoder regionDecoder = mRegionDecoder;
        if (!mImageChanged && null != regionDecoder && regionDecoder.isZoomedOut()) {
            stopAllAnimation();
            final float targetScale = regionDecoder.getInitialScale();
            regionDecoder.saveCurrentPivot(mStartPivot);
            mTargetPivot.x = regionDecoder.fixPivotX(mStartPivot.x, targetScale);
            mTargetPivot.y = regionDecoder.fixPivotY(mStartPivot.y, targetScale);
            mScaleAnimator.setFloatValues(regionDecoder.getScale(), targetScale);
            mScaleAnimator.start();
            return true;
        }
        return false;
    }

    private void stopAllAnimation() {
        mScaleAnimator.cancel();

        final AnimatorSet flingAnimators = mFlingAnimators;

        if (null != flingAnimators) {
            flingAnimators.cancel();
            flingAnimators.removeAllListeners();
        }
    }

    private boolean onFling(
            @NonNull MotionEvent e1,
            @NonNull MotionEvent e2,
            float velocityX,
            float velocityY) {
        if (Math.abs(velocityX) <= this.mMinFlingVelocity && Math.abs(velocityY) <= this.mMinFlingVelocity) {
            return false;
        }
        stopAllAnimation();
        float distanceX = velocityX / mMaxFlingVelocity * getWidth();
        float distanceY = velocityY / mMaxFlingVelocity * getHeight();
        final RegionDecoder regionDecoder = mRegionDecoder;
        if (null == regionDecoder || mImageChanged || !regionDecoder.canScroll(distanceX, distanceY)) {
            return false;
        }
        double totalDistance = Math.hypot(distanceX, distanceY);
        long duration = (long) Math.min(Math.max(300, totalDistance / 5), 800);

        final ValueAnimator xAnim = ValueAnimator
                .ofFloat(0F, regionDecoder.getScaled(distanceX))
                .setDuration(duration);
        final ValueAnimator yAnim = ValueAnimator
                .ofFloat(0F, regionDecoder.getScaled(distanceY))
                .setDuration(duration);
        mFlingAnimators = new AnimatorSet();
        mFlingAnimators.playTogether(xAnim, yAnim);
        mFlingAnimators.setInterpolator(new DecelerateInterpolator());
        yAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            float previousX = 0F;
            float previousY = 0F;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float x = (Float) xAnim.getAnimatedValue();
                float y = (Float) yAnim.getAnimatedValue();
                if (regionDecoder.scrollByScaled(x - previousX, y - previousY)) {
                    invalidate();
                    previousX = x;
                    previousY = y;
                }
            }
        });
        mFlingAnimators.start();
        return true;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true; // 这里要返回 true, 否则无法监听到 onScroll
        }

        @Override
        public void onShowPress(MotionEvent e) {
            stopAllAnimation();
        }

        /**
         * 处理拖动事件<br/>
         * {@inheritDoc}
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            distanceX = -distanceX;
            distanceY = -distanceY;
            final RegionDecoder regionDecoder = mRegionDecoder;

            if (null == regionDecoder || mImageChanged || !regionDecoder.canScroll(distanceX, distanceY)) {
                return false;
            }
            final boolean scrolled = regionDecoder.scrollByUnscaled(distanceX, distanceY);
            if (scrolled) {
                invalidate();
            }
            return scrolled;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            LongImageView.this.performLongClick();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 != null && e2 != null) {
                if (e1.getPointerCount() <= 1 && e2.getPointerCount() <= 1) {
                    if (mScaleGestureDetector.isInProgress()) {
                        return false;
                    } else {
                        long delta = SystemClock.uptimeMillis() - mPointerUpTime;
                        return delta > MIN_FLING_DELTA_TIME && LongImageView.this.onFling(e1, e2, velocityX, velocityY);
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return LongImageView.this.performClick();
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            stopAllAnimation();

            final RegionDecoder regionDecoder = mRegionDecoder;
            if (null == regionDecoder || mImageChanged) {
                return false;
            }
            regionDecoder.saveCurrentPivot(mStartPivot);
            float scaleStart = regionDecoder.getScale();
            float scaleEnd;
            if (regionDecoder.isZoomed()) {
                scaleEnd = regionDecoder.getInitialScale();
            } else {
                scaleEnd = regionDecoder.getMaxScale();
            }

            regionDecoder.predicateTargetPivot(scaleEnd, e.getX(), e.getY(), mTargetPivot);
            mScaleAnimator.setFloatValues(scaleStart, scaleEnd);
            mScaleAnimator.start();
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float targetPivotX;
        private float targetPivotY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            final RegionDecoder regionDecoder = mRegionDecoder;
            if (null == regionDecoder || mImageChanged) {
                return false;
            }

            targetPivotX = regionDecoder.transformXCoordinate(detector.getFocusX());
            targetPivotY = regionDecoder.transformYCoordinate(detector.getFocusY());
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final RegionDecoder regionDecoder = mRegionDecoder;
            if (null == regionDecoder || mImageChanged) {
                return false;
            }
            stopAllAnimation();
            float span = detector.getCurrentSpan() - detector.getPreviousSpan();
            float targetScale = detector.getScaleFactor() * regionDecoder.getScale();

            if (span != 0F) {
                regionDecoder.scale(targetScale,
                        regionDecoder.fixPivotX(targetPivotX, targetScale),
                        regionDecoder.fixPivotY(targetPivotY, targetScale)
                );
                invalidate();
            }
            return true;
        }
    }
}