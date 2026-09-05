// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.Rect as AndroidRect
import android.graphics.RenderNode
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.ceil
import kotlin.math.floor

@InternalHazeApi
internal actual fun createHazeBackdropRenderer(): HazeBackdropRenderer? =
  if (isAndroidBackdropSdkSupported()) {
    AndroidHazeBackdropRenderer()
  } else {
    null
  }

private class AndroidHazeBackdropRenderer : HazeBackdropRenderer {
  private var renderNode: RenderNode? = null

  override fun isSupported(canvas: Canvas): Boolean {
    if (!canvas.nativeCanvas.isHardwareAccelerated) return false
    if (renderNode == null) {
      renderNode = RenderNode("HazeBackdrop")
    }
    return true
  }

  override fun configure(
    bounds: Rect,
    clip: Rect?,
    effect: PlatformRenderEffect,
    alpha: Float,
  ): Boolean {
    val node = renderNode ?: return false
    val left = floor(bounds.left).toInt()
    val top = floor(bounds.top).toInt()
    val right = ceil(bounds.right).toInt()
    val bottom = ceil(bounds.bottom).toInt()
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return false

    node.setPosition(left, top, right, bottom)
    node.setClipToBounds(clip != null)
    node.setClipRect(
      clip?.let {
        AndroidRect(
          floor(it.left - left).toInt(),
          floor(it.top - top).toInt(),
          ceil(it.right - left).toInt(),
          ceil(it.bottom - top).toInt(),
        )
      },
    )
    node.setAlpha(alpha)
    node.setBackdropRenderEffect(effect)

    // A transparent SRC_OVER draw leaves the node visually empty but keeps its backdrop filter
    // composited. Recording a CLEAR operation instead suppresses the backdrop on Android 37.2.
    val recordingCanvas = node.beginRecording(width, height)
    recordingCanvas.drawColor(android.graphics.Color.TRANSPARENT)
    node.endRecording()
    return true
  }

  override fun draw(canvas: Canvas): Boolean {
    val node = renderNode ?: return false
    if (!canvas.nativeCanvas.isHardwareAccelerated) return false
    canvas.nativeCanvas.drawRenderNode(node)
    return true
  }

  override fun release() {
    renderNode?.discardDisplayList()
    renderNode = null
  }
}

private fun isAndroidBackdropSdkSupported(): Boolean {
  if (Build.VERSION.SDK_INT < 36) return false
  return isHazeBackdropSdkSupported(
    fullSdkInt = Build.VERSION.SDK_INT_FULL,
    previewSdkInt = Build.VERSION.PREVIEW_SDK_INT,
  )
}
