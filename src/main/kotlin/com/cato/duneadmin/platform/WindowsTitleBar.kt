package com.cato.duneadmin.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Component

/** Applies the same Mocha non-client colors used by CatosResourceCalc. */
object WindowsTitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private const val MOCHA_BACKGROUND = 0xFF1E1614.toInt()
    private const val MOCHA_BORDER = 0xFF5A443B.toInt()
    private const val MOCHA_TEXT = 0xFFF2E7DF.toInt()

    fun apply(component: Component) {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return

        runCatching {
            val hwnd = HWND(Native.getComponentPointer(component))
            setInt(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1)
            setColor(hwnd, DWMWA_CAPTION_COLOR, MOCHA_BACKGROUND)
            setColor(hwnd, DWMWA_BORDER_COLOR, MOCHA_BORDER)
            setColor(hwnd, DWMWA_TEXT_COLOR, MOCHA_TEXT)
        }
    }

    private fun setInt(hwnd: HWND, attribute: Int, value: Int) {
        val reference = IntByReference(value)
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, reference.pointer, Int.SIZE_BYTES)
    }

    private fun setColor(hwnd: HWND, attribute: Int, argb: Int) {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        setInt(hwnd, attribute, red or (green shl 8) or (blue shl 16))
    }

    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, valueSize: Int): Int

        companion object {
            val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
