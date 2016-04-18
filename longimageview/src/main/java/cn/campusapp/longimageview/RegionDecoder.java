package cn.campusapp.longimageview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;


/**
 * 图片区域解码器<br/>
 * 图片区域的坐标系与{@link LongImageView} 的坐标系不一致, 需要做坐标系变换 (缩放 + 平移)
 * Created by chen on 16/4/14.
 */
@SuppressWarnings("UnusedDeclaration")
class RegionDecoder {
    private static final String TAG = "RegionDecoder";
    /**
     * 默认最小缩放比例
     */
    private static final float MIN_SCALE_FACTOR = 0.6F;
    /**
     * 默认最大缩放比例
     */
    private static final float MAX_SCALE_FACTOR = 2.0F;

    static {
    }

    private final BitmapFactory.Options mDecodeOptions = new BitmapFactory.Options();
    /**
     * 初始解码区域
     */
    private final Rect mInitialRegionRect = new Rect();
    /**
     * 当前解码区域
     */
    private final Rect mRegionRect = new Rect();
    /**
     * BitmapRegionDecoder 对象
     */
    private final BitmapRegionDecoder mDecoder;
    /**
     * 显示区域
     */
    private final Rect mDisplayRect = new Rect();
    /**
     * 最小缩放比例
     */
    private float mMinScale;
    /**
     * 最大缩放比例
     */
    private float mMaxScale;
    /**
     * 初始缩放比例
     */
    private float mInitialScale;
    /**
     * 当前缩放比例
     */
    private float mScale;
    /**
     * 图片宽(Px)
     */
    private int mImageWidth;
    /**
     * 图片高(Px)
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

    /**
     * 滑动图片
     *
     * @param dx x 轴偏移量 (未缩放)
     * @param dy y 轴偏移量 (为缩放)
     * @return 是否滑动了
     */
    boolean scrollByUnscaled(float dx, float dy) {
        return translateScaled((int) _scaled(dx, mScale), (int) _scaled(dy, mScale));
    }

    /**
     * 滑动图片
     *
     * @param scaledDx x 轴偏移量 (已缩放)
     * @param scaledDy y 轴偏移量 (已缩放)
     * @return 是否滑动了
     */
    boolean scrollByScaled(float scaledDx, float scaledDy) {
        return translateScaled((int) scaledDx, (int) scaledDy);
    }

    /**
     * 根据目标缩放比例和给定的坐标预测实际解码区域中心点
     *
     * @param targetScale    目标缩放比
     * @param pivotX         目标解码区域 x 轴中心点
     * @param pivotY         目标解码区域 y 轴中心点
     * @param outTargetPivot 出参, 用于存储实际的中心点
     */
    void predicateTargetPivot(float targetScale, float pivotX, float pivotY, PointF outTargetPivot) {
        targetScale = ensureScaleRange(targetScale);
        final float x = fixPivotX(transformAxisX(pivotX), targetScale);
        final float y = fixPivotY(transformAxisY(pivotY), targetScale);

        outTargetPivot.set(x, y);
    }

    /**
     * 根据给定的 x 轴坐标计算合法的解码区域 x 轴中心点, 确保解码区域不会超出图片边界
     *
     * @param transformedX 已进行坐标变换的 x 轴坐标
     * @param scale        缩放比例
     * @return 合法的 x 轴坐标
     */
    float fixPivotX(float transformedX, float scale) {
        final float targetWidthInset = getWidthInset(scale);
        return Math.min(mImageWidth - targetWidthInset, Math.max(targetWidthInset, transformedX));
    }

    /**
     * 根据给定的 y 轴坐标计算合法的解码区域 y 轴中心点, 确保解码区域不会超出图片边界
     *
     * @param transformY 已进行坐标变换的 y 轴坐标
     * @param scale      缩放比例
     * @return 合法的 y 轴坐标
     */
    float fixPivotY(float transformY, float scale) {
        final float targetHeightInset = getHeightInset(scale);
        return Math.min(mImageHeight - targetHeightInset, Math.max(targetHeightInset, transformY));
    }

    private float getWidthInset(float scale) {
        return Math.min(getRegionWidth(scale), mImageWidth) / 2;
    }

    private float getHeightInset(float scale) {
        return Math.min(getRegionHeight(scale), mImageHeight) / 2;
    }

    /**
     * 存储当前的界面区域中心点
     *
     * @param outPivot 出参, 用于存储中心点
     */
    void restoreCurrentPivot(PointF outPivot) {
        outPivot.set(mRegionRect.centerX(), mRegionRect.centerY());
    }

    /**
     * 进行图片缩放
     *
     * @param targetScale       目标缩放比
     * @param transformedPivotX 变换后的 x 轴坐标
     * @param transformedPivotY 变换后的 y 轴坐标
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

    private boolean translateScaled(int scaledDx, int scaledDy) {
        final int fixedScrollDx = getFixedScrollX(-scaledDx);
        final int fixedScrollDy = getFixedScrollY(-scaledDy);
        mRegionRect.offset(fixedScrollDx, fixedScrollDy);
        return fixedScrollDx != 0 || fixedScrollDy != 0;
    }

    /**
     * x 轴坐标转换
     *
     * @param x 未转换的 x 轴坐标
     * @return 转换后的 x 轴坐标
     */
    float transformAxisX(float x) {
        return _scaled(x, mScale) + getAxisXOffset();
    }

    /**
     * 获取缩放后的长度
     *
     * @param unScaled 未缩放的长度
     * @return 缩放后的长度
     */
    float getScaled(float unScaled) {
        return _scaled(unScaled, mScale);
    }

    /**
     * y 轴坐标转换
     *
     * @param y 未转换的 y 轴坐标
     * @return 转换后的 y 轴坐标
     */
    float transformAxisY(float y) {
        return _scaled(y, mScale) + getAxisYOffset();
    }

    private float getAxisXOffset() {
        return mRegionRect.left;
    }

    private float getAxisYOffset() {
        return mRegionRect.top;
    }

    private float getRegionWidth(float scale) {
        return mInitialRegionRect.width() / scale;
    }

    private float getRegionHeight(float scale) {
        return mInitialRegionRect.height() / scale;
    }

    /**
     * 按照给定的比例进行缩放
     *
     * @param unScaled 未缩放的长度
     * @param scale    缩放比
     * @return 缩放后的长度
     */
    private float _scaled(float unScaled, float scale) {
        return unScaled * mInitialRegionRect.width() / mDisplayRect.width() / scale;
    }

    /**
     * 可否滑动
     *
     * @param dx x 方向滑动距离 (未转换)
     * @param dy y 方向滑动距离 (已转换)
     * @return 是否可以滑动
     */
    boolean canScroll(float dx, float dy) {
        return canScrollX(dx) || canScrollY(dy);
    }

    private boolean canScrollX(final float dx) {
        if (dx > 0) {
            return mRegionRect.left > 0;
        } else {
            return mRegionRect.right < mImageWidth;
        }
    }

    private boolean canScrollY(final float dy) {
        if (dy > 0) {
            return mRegionRect.top > 0;
        } else {
            return mRegionRect.bottom < mImageHeight;
        }
    }

    private int getFixedScrollX(int dx) {
        if (mScale >= mInitialScale) { // 放大
            if (mRegionRect.left + dx < 0) {
                return -mRegionRect.left;
            } else if (mRegionRect.right + dx > mImageWidth) {
                return mImageWidth - mRegionRect.right;
            } else {
                return dx;
            }
        } else { // 缩小
            if (mRegionRect.left + dx > 0) {
                return -mRegionRect.left;
            } else if (mRegionRect.right + dx < mImageWidth) {
                return mImageWidth - mRegionRect.right;
            } else {
                return dx;
            }
        }
    }

    private int getFixedScrollY(int dy) {
        if (mScale < mInitialScale) { // 缩小状态下不能纵向移动
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
}
