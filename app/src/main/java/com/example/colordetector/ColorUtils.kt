package com.example.colordetector

object ColorUtils {
    fun toHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
}
