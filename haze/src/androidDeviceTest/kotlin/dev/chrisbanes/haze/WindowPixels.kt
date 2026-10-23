// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Copies this activity's window, as the hardware renderer composited it, into a bitmap. */
internal fun ComponentActivity.copyWindow(): Bitmap {
  val targetWindow = this.window
  val bitmap = Bitmap.createBitmap(
    targetWindow.decorView.width,
    targetWindow.decorView.height,
    Bitmap.Config.ARGB_8888,
  )
  val latch = CountDownLatch(1)
  var result = PixelCopy.ERROR_UNKNOWN
  PixelCopy.request(targetWindow, bitmap, { copyResult ->
    result = copyResult
    latch.countDown()
  }, Handler(Looper.getMainLooper()))
  assertThat(latch.await(5, TimeUnit.SECONDS), "Window PixelCopy completed").isTrue()
  assertThat(result, "Window PixelCopy result").isEqualTo(PixelCopy.SUCCESS)
  return bitmap
}

/** The pixel at ([x], [y]), clamped to this bitmap's bounds. */
internal fun Bitmap.pixel(x: Int, y: Int): Color {
  val pixel = getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
  return Color(
    red = AndroidColor.red(pixel) / 255f,
    green = AndroidColor.green(pixel) / 255f,
    blue = AndroidColor.blue(pixel) / 255f,
    alpha = AndroidColor.alpha(pixel) / 255f,
  )
}
