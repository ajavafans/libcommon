package com.serenegiant.mediastore;
/*
 * LoaderDrawable is a descendent of Drawable to load image asynchronusly and draw
 * We want to use BitmapDrawable but we can't because it has no public/protected method
 * to set Bitmap after construction.
 *
 * Most code of LoaderDrawable came from BitmapJobDrawable.java in Android Gallery app
 *
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public abstract class LoaderDrawable extends Drawable implements Runnable {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static final String TAG = LoaderDrawable.class.getSimpleName();

	private final ContentResolver mContentResolver;
    private final Paint mPaint = new Paint();
    private final Paint mDebugPaint = new Paint();
    private final Matrix mDrawMatrix = new Matrix();
	private Bitmap mBitmap;
    private int mRotation = 0;
    private ImageLoader mLoader;
    private final int mWidth, mHeight;

	public LoaderDrawable(final ContentResolver cr, final int width, final int height) {
		mContentResolver = cr;
		mDebugPaint.setColor(Color.RED);
		mDebugPaint.setTextSize(18);
		mWidth = width;
		mHeight = height;
	}

    @Override
    protected void onBoundsChange(final Rect bounds) {
        super.onBoundsChange(bounds);
        updateDrawMatrix(getBounds());
    }

    @Override
	public void draw(@NonNull final Canvas canvas) {
        final Rect bounds = getBounds();
        if (mBitmap != null) {
            canvas.save();
            canvas.clipRect(bounds);
            canvas.concat(mDrawMatrix);
            canvas.rotate(mRotation, bounds.centerX(), bounds.centerY());
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            canvas.restore();
        } else {
            mPaint.setColor(0xFFCCCCCC);
            canvas.drawRect(bounds, mPaint);
        }
           if (DEBUG) {
            canvas.drawText(Long.toString(mLoader != null ? mLoader.id() : -1),
            	bounds.centerX(), bounds.centerY(), mDebugPaint);
           }
	}

	public ContentResolver getContentResolver() {
		return mContentResolver;
	}

	public int getWidth() {
		return mWidth;
	}

	public int getHeight() {
		return mHeight;
	}

	private void updateDrawMatrix(final Rect bounds) {
	    if (mBitmap == null || bounds.isEmpty()) {
	        mDrawMatrix.reset();
	        return;
	    }

	    final float dwidth = mBitmap.getWidth();
	    final float dheight = mBitmap.getHeight();
	    final int vwidth = bounds.width();
	    final int vheight = bounds.height();

	    float scale;
	    int dx = 0, dy = 0;

	    // Calculates a matrix similar to ScaleType.CENTER_CROP
           if (dwidth * vheight > vwidth * dheight) {
               scale = vheight / dheight;
			dx = (int) ((vwidth - dwidth * scale) * 0.5f + 0.5f);
           } else {
               scale = vwidth / dwidth;
			dy = (int) ((vheight - dheight * scale) * 0.5f + 0.5f);
           }
/*		    // Calculates a matrix similar to ScaleType.CENTER_INSIDE
           if (dwidth <= vwidth && dheight <= vheight) {
               scale = 1.0f;
           } else {
               scale = Math.min((float) vwidth / (float) dwidth,
                       (float) vheight / (float) dheight);
           }
           dx = (int) ((vwidth - dwidth * scale) * 0.5f + 0.5f);
           dy = (int) ((vheight - dheight * scale) * 0.5f + 0.5f); */
		mDrawMatrix.setScale(scale, scale);
		mDrawMatrix.postTranslate(dx, dy);

	    invalidateSelf();
	}

	@Override
	public void setAlpha(final int alpha) {
        int oldAlpha = mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
	}

	@Override
	public void setColorFilter(final ColorFilter cf) {
        mPaint.setColorFilter(cf);
        invalidateSelf();
	}

    @Override
    public int getIntrinsicWidth() {
    	return mWidth;
    }

    @Override
    public int getIntrinsicHeight() {
    	return mHeight;
    }

	@Override
	public int getOpacity() {
        Bitmap bm = mBitmap;
        return (bm == null || bm.hasAlpha() || mPaint.getAlpha() < 255) ?
                PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
	}

    /**
     * callback to set bitmap on UI thread after asyncronus loading
     * request call this callback in ThumbnailLoader#run at the end of asyncronus loading
     */
	@Override
	public void run() {
		setBitmap(mLoader.getBitmap());
	}

	protected abstract ImageLoader createThumbnailLoader();
	protected abstract Bitmap checkBitmapCache(final int hashCode, final long id);

	/**
	 * start loading image asynchronously
	 * @param id
	 */
	public void startLoad(final int media_type, final int hashCode, final long id) {

		if (mLoader != null)
			mLoader.cancelLoad();

		// try to get from internal thumbnail cache
		final Bitmap newBitmap = checkBitmapCache(hashCode, id);
		if (newBitmap == null) {
			// only start loading if the thumbnail does not exist in internal thumbnail cache
			mBitmap = null;
			// re-using ThumbnailLoader will cause several problems on some devices...
			mLoader = createThumbnailLoader();
			mLoader.startLoad(media_type, hashCode, id);
		} else {
			setBitmap(newBitmap);
		}
		invalidateSelf();
	}

	private void setBitmap(final Bitmap bitmap) {
		if (bitmap != mBitmap) {
			mBitmap = bitmap;
            updateDrawMatrix(getBounds());
		}
	}
}
