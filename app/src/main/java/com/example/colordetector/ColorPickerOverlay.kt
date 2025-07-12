package com.example.clickcolor

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast

class ColorPickerOverlay(private val context: Context, private val bitmap: Bitmap) {

    fun show(onColorPicked: (x: Int, y: Int, r: Int, g: Int, color: Int) -> Unit) {
        val dialog = Dialog(context)
        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)
        imageView.adjustViewBounds = true

        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val imageBitmap = (imageView.drawable as BitmapDrawable).bitmap
                val x = event.x.toInt()
                val y = event.y.toInt()

                if (x in 0 until imageBitmap.width && y in 0 until imageBitmap.height) {
                    val pixel = imageBitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    Toast.makeText(context, "Picked: ($x,$y) #${Integer.toHexString(pixel)}", Toast.LENGTH_SHORT).show()
                    onColorPicked(x, y, r, g, pixel)
                    dialog.dismiss()
                }
            }
            true
        }

        dialog.setContentView(imageView)
        dialog.setTitle("Tap to pick a color")
        dialog.show()
    }
}
