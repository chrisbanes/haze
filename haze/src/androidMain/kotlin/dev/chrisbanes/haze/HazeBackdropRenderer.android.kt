// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.ceil
import kotlin.math.floor

@InternalHazeApi
internal actual fun createHazeBackdropRenderer(): HazeBackdropRenderer? =
  if (isAndroidBackdropSdkSupported() && AndroidBackdropRenderEffectApi.isAvailable) {
    AndroidHazeBackdropRenderer()
  } else {
    null
  }

private class AndroidHazeBackdropRenderer : HazeBackdropRenderer {
  private var renderNode: RenderNode? = null
  private var configuredEffect: RenderEffect? = null

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
    if (configuredEffect !== effect) {
      if (!AndroidBackdropRenderEffectApi.setBackdropRenderEffect(node, effect)) return false
      configuredEffect = effect
    }

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
    configuredEffect = null
  }
}

private object AndroidBackdropRenderEffectApi {
  private val method = try {
    RenderNode::class.java.getMethod("setBackdropRenderEffect", RenderEffect::class.java)
  } catch (_: ReflectiveOperationException) {
    null
  }

  val isAvailable: Boolean get() = method != null

  fun setBackdropRenderEffect(node: RenderNode, effect: RenderEffect): Boolean {
    val method = method ?: return false
    return try {
      method.invoke(node, effect)
      true
    } catch (_: ReflectiveOperationException) {
      false
    }
  }
}

private fun isAndroidBackdropSdkSupported(): Boolean {
  if (Build.VERSION.SDK_INT < 36) return false
  // Robolectric 4.17 implements native backdrop rendering on its SDK 37 runtime,
  // which reports 37.0 rather than the device API's 37.2.
  // https://github.com/robolectric/robolectric/issues/11510
  if (Build.VERSION.SDK_INT == 37 && Build.FINGERPRINT == "robolectric") return true
  return isHazeBackdropSdkSupported(
    fullSdkInt = Build.VERSION.SDK_INT_FULL,
    previewSdkInt = Build.VERSION.PREVIEW_SDK_INT,
  )
}
