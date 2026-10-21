package com.god.ApplicationManager.Helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Process-wide icon cache keyed by package name.
 *
 * Icons are stored as [Bitmap]s in an [LruCache] sized to 1/8 of the max heap,
 * so scrolling the app list stops re-decoding / re-drawing every icon and the
 * list items no longer each pin a full [Drawable] (the old AppInfo-held icons
 * were a standing memory cost).
 */
object IconCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxKb / 8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @JvmStatic
    fun getIcon(context: Context, packageName: String): Drawable? {
        cache.get(packageName)?.let { return BitmapDrawable(context.resources, it) }
        val drawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            return null
        }
        val bitmap = drawable.toBitmap()
        if (bitmap != null) {
            cache.put(packageName, bitmap)
            return BitmapDrawable(context.resources, bitmap)
        }
        return drawable
    }

    @JvmStatic
    fun evict(packageName: String) {
        cache.remove(packageName)
    }

    @JvmStatic
    fun clear() {
        cache.evictAll()
    }

    private fun Drawable.toBitmap(): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val width = if (intrinsicWidth > 0) intrinsicWidth else 96
        val height = if (intrinsicHeight > 0) intrinsicHeight else 96
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
