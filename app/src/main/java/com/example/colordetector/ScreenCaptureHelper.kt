package com.example.clickcolor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.*
import android.widget.Toast
import java.nio.ByteBuffer

class ScreenCaptureHelper(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {

    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val x = prefs.getInt("x", -1)
    private val y = prefs.getInt("y", -1)
    private val targetColor = prefs.getInt("color", 0)

    private val handlerThread = HandlerThread("ScreenMonitorThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val metrics = context.resources.displayMetrics
    private val width = metrics.widthPixels
    private val height = metrics.heightPixels
    private val dpi = metrics.densityDpi

    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

    fun startColorMonitoring() {
        if (x == -1 || y == -1) {
            Toast.makeText(context, "No color coordinates set", Toast.LENGTH_SHORT).show()
            return
        }

        mediaProjection.createVirtualDisplay(
            "MonitorDisplay",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler
        )

        handler.postDelayed(::checkColorLoop, 1000)
    }

    private fun checkColorLoop() {
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            try {
                val pixelColor = bitmap.getPixel(x, y)
                if (colorsMatch(pixelColor, targetColor)) {
                    Toast.makeText(context, "Color match at ($x,$y)!", Toast.LENGTH_SHORT).show()
                    performClick(x, y)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Repeat check
        handler.postDelayed(::checkColorLoop, 1000)
    }

    private fun colorsMatch(a: Int, b: Int): Boolean {
        val tolerance = 15
        val r1 = (a shr 16) and 0xFF
        val g1 = (a shr 8) and 0xFF
        val b1 = a and 0xFF

        val r2 = (b shr 16) and 0xFF
        val g2 = (b shr 8) and 0xFF
        val b2 = b and 0xFF

        return (Math.abs(r1 - r2) < tolerance &&
                Math.abs(g1 - g2) < tolerance &&
                Math.abs(b1 - b2) < tolerance)
    }

    private fun performClick(x: Int, y: Int) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
            process.waitFor()
        } catch (e: Exception) {
            Toast.makeText(context, "Click failed (root required)", Toast.LENGTH_SHORT).show()
        }
    }
}
