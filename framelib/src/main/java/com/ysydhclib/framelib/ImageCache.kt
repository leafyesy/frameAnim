package com.ysydhclib.framelib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build

import java.lang.ref.SoftReference
import java.util.Collections
import java.util.HashSet

/**
 * 缓存图片类
 */
class ImageCache {

    private val mReusableBitmaps: MutableSet<SoftReference<Bitmap>>? = Collections.synchronizedSet(HashSet())

    // This method iterates through the reusable bitmaps, looking for one
    // to use for inBitmap:
    internal fun getBitmapFromReusableSet(options: BitmapFactory.Options): Bitmap? {
        var bitmap: Bitmap? = null

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized(mReusableBitmaps) {
                val iterator = mReusableBitmaps.iterator()
                var item: Bitmap?
                while (iterator.hasNext()) {
                    item = iterator.next().get()
                    if (null != item && item.isMutable) {
                        // Check to see it the item can be used for inBitmap.
                        if (canUseForInBitmap(item, options)) {
                            bitmap = item
                            iterator.remove()
                            break
                        }
                    } else {
                        iterator.remove()
                    }
                }
            }
        }
        return bitmap
    }

    companion object {

        private fun canUseForInBitmap(candidate: Bitmap, targetOptions: BitmapFactory.Options): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val width = targetOptions.outWidth / targetOptions.inSampleSize
                val height = targetOptions.outHeight / targetOptions.inSampleSize
                val byteCount = width * height * getBytesPerPixel(candidate.config)
                return byteCount <= candidate.allocationByteCount
            }

            // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return (candidate.width == targetOptions.outWidth
                    && candidate.height == targetOptions.outHeight
                    && targetOptions.inSampleSize == 1)
        }

        /**
         * A helper function to return the byte usage per pixel of a bitmap based on its configuration.
         */
        private fun getBytesPerPixel(config: Bitmap.Config): Int {
            return when (config) {
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ARGB_4444 -> 2
                Bitmap.Config.ALPHA_8 -> 1
                else -> 1
            }
        }
    }
}
