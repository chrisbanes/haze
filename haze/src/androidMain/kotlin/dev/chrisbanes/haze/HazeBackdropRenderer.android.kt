// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import java.lang.reflect.Method
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Android's backdrop RenderNode API is introduced in the second 37.x platform release. The
 * library currently compiles against a 37.0 SDK, so resolving and invoking that method through
 * reflection keeps older devices safe while retaining the exact public platform API at runtime.
 */
@InternalHazeApi
internal actual fun createHazeBackdropRenderer(): HazeBackdropRenderer =
  AndroidHazeBackdropRenderer()

private class AndroidHazeBackdropRenderer : HazeBackdropRenderer {
  private var renderNode: Any? = null
  private var drawRenderNode: Method? = null
  private var setPosition: Method? = null
  private var setClipToBounds: Method? = null
  private var setClipRect: Method? = null
  private var setBackdropRenderEffect: Method? = null
  private var beginRecording: Method? = null
  private var endRecording: Method? = null
  private var discardDisplayList: Method? = null

  override fun isSupported(canvas: Canvas): Boolean {
    if (!canvas.nativeCanvas.isHardwareAccelerated) return false
    if (!isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT)) return false
    return resolveReflection()
  }

  override fun configure(
    bounds: Rect,
    clip: Rect?,
    effect: PlatformRenderEffect,
  ): Boolean {
    val node = renderNode ?: return false
    val left = floor(bounds.left).toInt()
    val top = floor(bounds.top).toInt()
    val right = ceil(bounds.right).toInt()
    val bottom = ceil(bounds.bottom).toInt()
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return false

    setPosition?.invoke(node, left, top, right, bottom)
    setClipToBounds?.invoke(node, clip != null)
    if (clip != null) {
      setClipRect?.invoke(
        node,
        AndroidRect(
          floor(clip.left - left).toInt(),
          floor(clip.top - top).toInt(),
          ceil(clip.right - left).toInt(),
          ceil(clip.bottom - top).toInt(),
        ),
      )
    }
    setBackdropRenderEffect?.invoke(node, effect)

    // A transparent SRC_OVER draw leaves the node visually empty but keeps its backdrop filter
    // composited. Recording a CLEAR operation instead suppresses the backdrop on Android 37.2.
    val recordingCanvas = beginRecording?.invoke(node, width, height)
    if (recordingCanvas == null) return false
    val drawColor = recordingCanvas.javaClass.getMethod(
      "drawColor",
      Int::class.javaPrimitiveType,
    )
    drawColor.invoke(recordingCanvas, android.graphics.Color.TRANSPARENT)
    endRecording?.invoke(node)
    return true
  }

  override fun draw(canvas: Canvas): Boolean {
    val node = renderNode ?: return false
    val drawMethod = drawRenderNode ?: return false
    if (!canvas.nativeCanvas.isHardwareAccelerated) return false
    drawMethod.invoke(canvas.nativeCanvas, node)
    return true
  }

  override fun release() {
    renderNode?.let { discardDisplayList?.invoke(it) }
    renderNode = null
    drawRenderNode = null
    setPosition = null
    setClipToBounds = null
    setClipRect = null
    setBackdropRenderEffect = null
    beginRecording = null
    endRecording = null
    discardDisplayList = null
  }

  private fun resolveReflection(): Boolean {
    if (renderNode != null) return true

    return try {
      val nodeClass = Class.forName("android.graphics.RenderNode")
      val node = nodeClass.getConstructor(String::class.java).newInstance("HazeBackdropPrototype")
      val nativeCanvasClass = Class.forName("android.graphics.Canvas")
      val renderEffectClass = Class.forName("android.graphics.RenderEffect")

      renderNode = node
      drawRenderNode = nativeCanvasClass.getMethod("drawRenderNode", nodeClass)
      setPosition = nodeClass.getMethod(
        "setPosition",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
      setClipToBounds = nodeClass.getMethod("setClipToBounds", Boolean::class.javaPrimitiveType)
      setClipRect = nodeClass.getMethod("setClipRect", AndroidRect::class.java)
      setBackdropRenderEffect = nodeClass.getMethod("setBackdropRenderEffect", renderEffectClass)
      beginRecording = nodeClass.getMethod(
        "beginRecording",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
      endRecording = nodeClass.getMethod("endRecording")
      discardDisplayList = nodeClass.getMethod("discardDisplayList")
      true
    } catch (_: ReflectiveOperationException) {
      release()
      false
    }
  }

  private companion object {
    fun fullSdkInt(): Int {
      if (Build.VERSION.SDK_INT < 36) return Build.VERSION.SDK_INT * 100_000
      return runCatching {
        Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null)
      }.getOrElse { Build.VERSION.SDK_INT * 100_000 }
    }
  }
}
