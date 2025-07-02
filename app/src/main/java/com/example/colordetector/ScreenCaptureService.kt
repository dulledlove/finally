package com.example.colordetector

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var settingsPanel: View
    private lateinit var imageReader: ImageReader
    private var displayMetrics = DisplayMetrics()

    private var autoClickEnabled = false
    private val targetColors = mutableListOf(
        0xFFFF0000.toInt(), // Red
        0xFF00FF00.toInt(), // Green
        0xFF0000FF.toInt()  // Blue
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        setupOverlay()
        setupImageReader()
        startForegroundNotification()

        return START_NOT_STICKY
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        settingsPanel = LayoutInflater.from(this).inflate(R.layout.settings_panel, null)

        // Handle taps on screen
        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                val color = capturePixelColor(x, y)
                Toast.makeText(this, "Tapped ($x, $y): #${Integer.toHexString(color)}", Toast.LENGTH_SHORT).show()

                if (autoClickEnabled && targetColors.contains(color)) {
                    performClick(x, y)
                }
            }
            true
        }

        // Settings UI logic
        val checkbox = settingsPanel.findViewById<CheckBox>(R.id.enable_click)
        val colorInput = settingsPanel.findViewById<EditText>(R.id.color_input)
        val applyButton = settingsPanel.findViewById<Button>(R.id.apply_button)

        checkbox.setOnCheckedChangeListener { _, isChecked ->
            autoClickEnabled = isChecked
            Toast.makeText(this, "Auto Click: $autoClickEnabled", Toast.LENGTH_SHORT).show()
        }

        applyButton.setOnClickListener {
            val hex = colorInput.text.toString().uppercase().trim()
            if (hex.matches(Regex("^[0-9A-F]{6}\$"))) {
                try {
                    val color = ("FF$hex").toLong(16).toInt()
                    targetColors.add(color)
                    Toast.makeText(this, "Color #$hex added", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Invalid color format", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Enter valid hex (e.g., FF00FF)", Toast.LENGTH_SHORT).show()
            }
        }

        // Add overlays
        windowManager.addView(overlayView, layoutParams)
        windowManager.addView(settingsPanel, layoutParams)
    }

    private fun setupImageReader() {
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )
    }

    private fun capturePixelColor(x: Int, y: Int): Int {
        val image: Image = imageReader.acquireLatestImage() ?: return 0
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        return try {
            bitmap.getPixel(x, y)
        } catch (e: Exception) {
            0
        }
    }

    private fun performClick(x: Int, y: Int) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
            process.waitFor()
        } catch (e: Exception) {
            Toast.makeText(this, "Click failed (root required)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "color_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Color Detector")
            .setContentText("Running screen monitoring...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Color Detector", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        startForeground(1, notification)
    }

    override fun onDestroy() {
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        if (::settingsPanel.isInitialized) windowManager.removeView(settingsPanel)
        imageReader.close()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
