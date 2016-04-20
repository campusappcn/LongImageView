package cn.campusapp.longimageview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;


/**
 * RegionDecoder takes care of fetching a certain region of image and providing corresponding {@link Bitmap}.
 * <p/>
 * By providing a series of methods, RegionDecoder allows user to manipulate
 * {@link RegionDecoder#mRegionRect} and get correct bitmap:
 * <ol>
 * <li>{@link RegionDecoder#scale(float, float, float)}</li>
 * <li>{@link RegionDecoder#scrollByScaled(float, float)}</li>
 * <li>{@link RegionDecoder#scrollByUnscaled(float, float)}</li>
 * </ol>
 * <p/>
 * Coordinate system in RegionDecoder differs from that in {@link LongImageView}, so transformation
 * of coordinates is necessary when passing coordinates between {@link LongImageView} and {@link RegionDecoder}
 * <p/>
 * Created by chen on 16/4/14.
 */
@SuppressWarnings("UnusedDeclaration")
class RegionDecoder {
    private static final String TAG = "RegionDecoder";
    /**
     * Default min scale factor
     */
    private static final float MIN_SCALE_FACTOR = 0.6F;
    /**
     * Default max scale factor
     */
    private static final float MAX_SCALE_FACTOR = 2.0F;
    private final BitmapFactory.Options mDecodeOptions = new BitmapFactory.Options();
    /**
     * Initial decode region
     */
    private final Rect mInitialRegionRect = new Rect();
    /**
     * Current decode region
     */
    private final Rect mRegionRect = new Rect();
    private final BitmapRegionDecoder mDecoder;
    /**
     * The rect where current image is shown
     */
    private final Rect mDisplayRect = new Rect();
    /**
     * Min scale
     */
    private float mMinScale;
    /**
     * Max scale
     */
    private float mMaxScale;
    /**
     * Initial scale
     */
    private float mInitialScale;
    /**
     * Current scale
     */
    private float mScale;
    /**
     * Image width in pixels
     */
    private int mImageWidth;
    /**
     * Image height in pixels
     */
    private int mImageHeight;

    RegionDecoder(InputStream is) throws Exception {
        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }

        mDecodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
        tmpOptions.inJustDecodeBounds = true;
        is.mark(1024);
        BitmapFactory.decodeStream(is, null, tmpOptions);
        is.reset();

        mImageWidth = tmpOptions.outWidth;
        mImageHeight = tmpOptions.outHeight;
        if (0 == mImageWidth || 0 == mImageHeight) {
            throw new Exception("Cannot decode input stream, width=" + mImageWidth + ", height=" + mImageHeight);
        }

        try {
            mDecoder = BitmapRegionDecoder.newInstance(is, false);
        } catch (Throwable t) {
            Log.e(TAG, "RegionDecoder: error creating BitmapRegionDecoder", t);
            throw t;
        }
        mScale = mInitialScale = 1F;
        mMinScale = mScale * MIN_SCALE_FACTOR;
        mMaxScale = mScale * MAX_SCALE_FACTOR;
    }

    float getMaxScale() {
        return mMaxScale;
    }

    float getScale() {
        return mScale;
    }

    float getInitialScale() {
        return mInitialScale;
    }

    boolean isZoomedIn() {
        return mScale > mInitialScale;
    }

    boolean isZoomedOut() {
        return mScale < mInitialScale;
    }

    boolean isZoomed() {
        return isZoomedIn() || isZoomedOut();
    }

    void setDisplayRect(@NonNull Rect displayRect) {
        mDisplayRect.set(displayRect);
        int initDisplayWidth = displayRect.width();
        int initDisplayHeight = displayRect.height();

        mInitialRegionRect.set(0, 0, mImageWidth, initDisplayHeight * mImageWidth / initDisplayWidth);
        if (mImageHeight / mImageWidth > initDisplayHeight / initDisplayWidth) {
            mInitialRegionRect.offsetTo(0, 0);
        } else {
            mInitialRegionRect.offsetTo(0, -(mInitialRegionRect.height() - mImageHeight) / 2);
            mMaxScale = mInitialScale * mInitialRegionRect.height() / mImageHeight;
        }
        mRegionRect.set(mInitialRegionRect);
    }

    Bitmap getBitmap() {
        try {
            final int displayWidth = mDisplayRect.width();
            if (displayWidth == 0) {
                mDecodeOptions.inSampleSize = 1;
            } else {
                mDecodeOptions.inSampleSize = mRegionRect.width() / displayWidth + 1;
            }
            mDecodeOptions.inPreferQualityOverSpeed = true;
            return mDecoder.decodeRegion(mRegionRect, mDecodeOptions);
        } catch (Throwable t) {
            Log.e(TAG, "getBitmap: failed", t);
            return null;
        }
    }

    Rect getRegion() {
        return mRegionRect;
    }

    void updateRegion(RectF rectF) {
        mScale = mInitialRegionRect.width() / rectF.width();
        float pivotX = fixPivotX(rectF.centerX(), mScale);
        float pivotY = fixPivotY(rectF.centerY(), mScale);

        float widthInset = getRegionWidth(mScale) / 2;
        float heightInset = getRegionHeight(mScale) / 2;
        mRegionRect.set(
                (int) (pivotX - widthInset),
                (int) (pivotY - heightInset),
                (int) (pivotX + widthInset),
                (int) (pivotY + heightInset)
        );
    }

    /**
     * Scroll image
     *
     * @param dx x offset (not transformed)
     * @param dy y offset (not transformed)
     * @return true if region is translated, otherwise false
     */
    boolean scrollByUnscaled(float dx, float dy) {
        return translateScaled(_scaled(dx, mScale), _scaled(dy, mScale));
    }

    /**
     * Scroll image
     *
     * @param scaledDx x offset (transformed)
     * @param scaledDy y offset (transformed)
     * @return true if region is translated, otherwise false
     */
    boolean scrollByScaled(float scaledDx, float scaledDy) {
        return translateScaled((int) scaledDx, (int) scaledDy);
    }

    void predicateTargetRegion(float targetScale, float pivotX, float pivotY, RectF outTargetRegion) {
        targetScale = ensureScaleRange(targetScale);
        final float transformedPivotX = fixPivotX(transformXCoordinate(pivotX), targetScale);
        final float transformedPivotY = fixPivotY(transformYCoordinate(pivotY), targetScale);

        final float widthInset = getRegionWidth(targetScale) / 2;
        final float heightInset = getRegionHeight(targetScale) / 2;
        outTargetRegion.set(
                (transformedPivotX - widthInset),
                (transformedPivotY - heightInset),
                (transformedPivotX + widthInset),
                (transformedPivotY + heightInset)
        );
    }

    /**
     * Determine actual x-coordinate by given transformed x-coordinate and scale, ensure that
     * {@link #mRegionRect} does not exceed the bounds of image
     *
     * @param transformedX transformed x-coordinate
     * @param scale        target scale
     * @return fixed x-coordinate
     */
    float fixPivotX(float transformedX, float scale) {
        final float targetWidthInset = getWidthInset(scale);
        return Math.min(mImageWidth - targetWidthInset, Math.max(targetWidthInset, transformedX));
    }

    /**
     * Determine actual y-coordinate by given transformed y-coordinate and scale, ensure that
     * {@link #mRegionRect} does not exceed the bounds of image
     *
     * @param transformedY transformed y-coordinate
     * @param scale        target scale
     * @return fixed y-coordinate
     */
    float fixPivotY(float transformedY, float scale) {
        final float targetHeightInset = getHeightInset(scale);
        return Math.min(mImageHeight - targetHeightInset, Math.max(targetHeightInset, transformedY));
    }

    private float getWidthInset(float scale) {
        return Math.min(getRegionWidth(scale), mImageWidth) / 2;
    }

    private float getHeightInset(float scale) {
        return Math.min(getRegionHeight(scale), mImageHeight) / 2;
    }

    /**
     * Save current decode region
     *
     * @param outRect out param, saves current decode region
     */
    void saveCurrentRegion(Rect outRect) {
        outRect.set(mRegionRect);
    }

    /**
     * Save current decode region
     *
     * @param outRect out param, saves current decode region
     */
    void saveCurrentRegion(RectF outRect) {
        outRect.set(mRegionRect);
    }

    /**
     * Scale current image and update {@link #mRegionRect}'s pivot
     *
     * @param targetScale       target Scale
     * @param transformedPivotX transformed x-coordinate
     * @param transformedPivotY transformed y-coordinate
     */
    void scale(float targetScale, float transformedPivotX, float transformedPivotY) {
        mScale = ensureScaleRange(targetScale);

        final float widthInset = getRegionWidth(mScale) / 2;
        final float heightInset = getRegionHeight(mScale) / 2;
        mRegionRect.set((int) (transformedPivotX - widthInset),
                (int) (transformedPivotY - heightInset),
                (int) (transformedPivotX + widthInset),
                (int) (transformedPivotY + heightInset));
    }

    private float ensureScaleRange(float targetScale) {
        return ensureRange(targetScale);
    }

    private float ensureRange(float targetScale) {
        return Math.min(mMaxScale, Math.max(targetScale, mMinScale));
    }

    private boolean translateScaled(float scaledDx, float scaledDy) {
        final float fixedScrollDx = getFixedScrollX(-scaledDx);
        final float fixedScrollDy = getFixedScrollY(-scaledDy);
        mRegionRect.offset((int) fixedScrollDx, (int) fixedScrollDy);
        return fixedScrollDx != 0 || fixedScrollDy != 0;
    }

    float transformXCoordinate(float x) {
        return _scaled(x, mScale) + getXCoordinateOffset();
    }

    float transformYCoordinate(float y) {
        return _scaled(y, mScale) + getYCoordinateOffset();
    }

    float getScaled(float unScaled) {
        return _scaled(unScaled, mScale);
    }

    private float getXCoordinateOffset() {
        return mRegionRect.left;
    }

    private float getYCoordinateOffset() {
        return mRegionRect.top;
    }

    private float getRegionWidth(float scale) {
        return mInitialRegionRect.width() / scale;
    }

    private float getRegionHeight(float scale) {
        return mInitialRegionRect.height() / scale;
    }

    /**
     * Scale given unscaled distance
     *
     * @param unScaled unscaled distance
     * @param scale    scale factor
     * @return scaled distance
     */
    private float _scaled(float unScaled, float scale) {
        return unScaled * mInitialRegionRect.width() / mDisplayRect.width() / scale;
    }

    /**
     * Determine whether current image can scroll
     *
     * @param dx x-offset (not transformed)
     * @param dy y-offset (not transformed)
     * @return true if current image can scroll, otherwise false
     */
    boolean canScroll(float dx, float dy) {
        return canScrollX(dx) || canScrollY(dy);
    }

    boolean canScrollX(final float dx) {
        if (dx > 0) {
            return mRegionRect.left > 0;
        } else {
            return mRegionRect.right < mImageWidth;
        }
    }

    boolean canScrollY(final float dy) {
        if (dy > 0) {
            return mRegionRect.top > 0;
        } else {
            return mRegionRect.bottom < mImageHeight;
        }
    }

    private float getFixedScrollX(float dx) {
        if (mScale >= mInitialScale) {
            if (mRegionRect.left + dx < 0) {
                return -mRegionRect.left;
            } else if (mRegionRect.right + dx > mImageWidth) {
                return mImageWidth - mRegionRect.right;
            } else {
                return dx;
            }
        } else {
            if (mRegionRect.left + dx > 0) {
                return -mRegionRect.left;
            } else if (mRegionRect.right + dx < mImageWidth) {
                return mImageWidth - mRegionRect.right;
            } else {
                return dx;
            }
        }
    }

    private float getFixedScrollY(float dy) {
        if (mScale < mInitialScale) {
            return 0;
        } else if (mRegionRect.top <= 0 && mRegionRect.bottom >= mImageHeight) { // 纵向已经全部展示
            return 0;
        } else if (mRegionRect.top >= 0 && dy + mRegionRect.top < 0) {
            return -mRegionRect.top;
        } else if (mRegionRect.bottom <= mImageHeight && dy + mRegionRect.bottom > mImageHeight) {
            return mImageHeight - mRegionRect.bottom;
        } else {
            return dy;
        }
    }

    int getImageHeight() {
        return mImageHeight;
    }

    int getImageWidth() {
        return mImageWidth;
    }

    void close() {
        if (null != mDecoder) {
            try {
                mDecoder.recycle();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }
}
