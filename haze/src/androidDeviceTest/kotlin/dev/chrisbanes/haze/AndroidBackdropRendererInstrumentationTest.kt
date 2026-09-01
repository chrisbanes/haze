// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AndroidBackdropRendererInstrumentationTest {

  private lateinit var activityScenario: ActivityScenario<ComponentActivity>
  private lateinit var activity: ComponentActivity

  @Before
  fun setUp() {
    activityScenario = ActivityScenario.launch(ComponentActivity::class.java)
    activityScenario.onActivity { activity = it }
  }

  @After
  fun tearDown() {
    activityScenario.close()
  }

  @Test
  fun backdropRenderer_samplesOrderingTransformsExpandedBoundsAndClip() {
    assumeTrue(
      "Backdrop RenderNode requires Android 37.2 or the matching preview",
      isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT),
    )

    val drawReady = CountDownLatch(PROTOTYPE_NODE_COUNT)
    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          // The ordering row has a black source with a white edge already in the window when the
          // backdrop node is drawn.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-90).dp, y = (-80).dp)
              .size(80.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-110).dp, y = (-80).dp)
              .size(20.dp)
              .background(Color.White),
          )

          // The transform row uses a source edge which only falls inside the node after its
          // translation, scale, and rotation are inherited by the RenderNode.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 80.dp, y = (-80).dp)
              .size(100.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 110.dp, y = (-80).dp)
              .size(10.dp)
              .background(Color.White),
          )

          // The expanded row places a contrasting source edge just outside the content bounds.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-90).dp, y = 80.dp)
              .size(90.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-60).dp, y = 80.dp)
              .size(10.dp)
              .background(Color.White),
          )

          // The clipping row uses the same edge as the expanded row. The sample just beyond the
          // content must remain black when the renderer applies its local clip.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 90.dp, y = 80.dp)
              .size(90.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 120.dp, y = 80.dp)
              .size(10.dp)
              .background(Color.White),
          )

          // The ordering node's own content is drawn after the backdrop and must remain sharp.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-90).dp, y = (-80).dp)
              .size(80.dp)
              .prototypeBackdrop(radius = 14.dp, clip = null, drawReady = drawReady)
              .background(Color.Transparent),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-70).dp, y = (-80).dp)
              .size(20.dp)
              .background(Color.Red),
          )

          // This node's backdrop RenderNode inherits the full Compose transform.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 20.dp, y = (-80).dp)
              .size(80.dp)
              .graphicsLayer {
                translationX = 60.dp.toPx()
                scaleX = 0.9f
                scaleY = 1.1f
                rotationZ = 4f
              }
              .prototypeBackdrop(radius = 14.dp, clip = null, drawReady = drawReady)
              .background(Color.Transparent),
          )

          // A caller clip must still stop expanded bounds from leaking into the surrounding area.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 90.dp, y = 80.dp)
              .size(60.dp)
              .prototypeBackdrop(
                radius = 14.dp,
                clip = PrototypeClip.Content,
                drawReady = drawReady,
              )
              .background(Color.Transparent),
          )

          // Expanded bounds are enabled for the same edge but without a local clip.
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = (-90).dp, y = 80.dp)
              .size(60.dp)
              .prototypeBackdrop(radius = 14.dp, clip = null, drawReady = drawReady)
              .background(Color.Transparent),
          )
        }
      }
    }
    assertThat(
      drawReady.await(5, TimeUnit.SECONDS),
      "All backdrop nodes presented their first draw",
    ).isTrue()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val centerX = bitmap.width / 2
    val centerY = bitmap.height / 2
    val radiusPx = 14.dp.roundToPx(density)

    // The black/white boundary is softened by the earlier backdrop content in the ordering row.
    val boundary = bitmap.pixel(
      centerX - 95.dp.roundToPx(density),
      centerY - 80.dp.roundToPx(density),
    )
    assertThat(boundary.red, "earlier pixels are blurred").isGreaterThan(0.05f)
    assertThat(boundary.red, "earlier pixels are blurred").isLessThan(0.95f)

    // The later red sibling is composited after the node and remains a solid red pixel.
    val later = bitmap.pixel(
      centerX - 70.dp.roundToPx(density),
      centerY - 80.dp.roundToPx(density),
    )
    assertThat(later.red, "later content remains sharp").isGreaterThan(0.9f)
    assertThat(later.green, "later content remains sharp").isLessThan(0.1f)

    // This sample is outside the untransformed node, but inside its translated, scaled, and
    // rotated bounds. A renderer which ignores the Compose canvas transform leaves the white
    // stripe edge sharp and fails this assertion.
    val transformed = bitmap.pixel(
      centerX + 122.dp.roundToPx(density),
      centerY - 80.dp.roundToPx(density),
    )
    assertThat(transformed.red, "Compose transforms reach the backdrop node").isGreaterThan(0.05f)

    // Sampling just outside the expanded row's content edge demonstrates that its bounds include
    // the blur kernel.
    val expanded = bitmap.pixel(
      centerX - 48.dp.roundToPx(density),
      centerY + 80.dp.roundToPx(density),
    )
    assertThat(expanded.red, "expanded bounds contain the kernel").isGreaterThan(0.05f)
    assertThat(radiusPx, "prototype uses non-zero expansion").isGreaterThan(0)

    val clippedLeak = bitmap.pixel(
      centerX + 132.dp.roundToPx(density),
      centerY + 80.dp.roundToPx(density),
    )
    assertThat(clippedLeak.red, "caller clipping prevents expanded leakage").isLessThan(0.05f)
  }

  @Test
  fun renderNodeBackdropEffect_blursEarlierSibling() {
    assumeTrue(
      "Backdrop RenderNode requires Android 37.2 or the matching preview",
      isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT),
    )

    val drawReady = CountDownLatch(1)
    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .offset(x = 50.dp)
              .size(width = 100.dp, height = 100.dp)
              .background(Color.White),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .prototypeBackdrop(
                radius = 14.dp,
                clip = PrototypeClip.Content,
                drawReady = drawReady,
              ),
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Backdrop node presented its first draw").isTrue()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val centerX = bitmap.width / 2
    val centerY = bitmap.height / 2
    bitmap.assertVerticalEdgeSoftened(centerX, centerY, density, "direct RenderNode backdrop")
  }

  @Test
  fun viewBackdropEffect_blursEarlierSibling() {
    assumeTrue(
      "View backdrop effect requires Android 37.2 or the matching preview",
      isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT),
    )

    val drawReady = CountDownLatch(1)
    lateinit var backdropView: View
    activityScenario.onActivity { activity ->
      val density = Density(activity.resources.displayMetrics.density)
      val width = 200.dp.roundToPx(density)
      val height = 100.dp.roundToPx(density)
      val radius = 14.dp.roundToPx(density).toFloat()
      val centered = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
      val root = FrameLayout(activity).apply {
        setBackgroundColor(AndroidColor.WHITE)
      }
      val source = BackdropSourceView(activity)
      val backdrop = BackdropProbeView(activity, drawReady)

      root.addView(source, centered)
      root.addView(backdrop, FrameLayout.LayoutParams(centered))
      View::class.java
        .getMethod("setBackdropRenderEffect", RenderEffect::class.java)
        .invoke(
          backdrop,
          RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
      activity.setContentView(root)
      backdropView = backdrop
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Backdrop View presented its first draw").isTrue()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val location = IntArray(2)
    var backdropWidth = 0
    var backdropHeight = 0
    activityScenario.onActivity {
      backdropView.getLocationInWindow(location)
      backdropWidth = backdropView.width
      backdropHeight = backdropView.height
    }
    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val centerX = location[0] + backdropWidth / 2
    val centerY = location[1] + backdropHeight / 2
    bitmap.assertVerticalEdgeSoftened(centerX, centerY, density, "public View backdrop")
  }

  @Test
  fun hazeInputBackdrop_usesNativeWithoutCapturingFallbackSource() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT),
    )

    val state = HazeState()
    val drawReady = CountDownLatch(1)
    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeSource(state)
              .background(Color.Black),
          ) {
            Box(
              Modifier
                .align(Alignment.CenterEnd)
                .size(width = 100.dp, height = 100.dp)
                .background(Color.White),
            )
          }
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeEffect(
                factory = BackdropBlurEffectFactory(drawReady),
                input = HazeInput.Backdrop(HazeInput.Sources(state)),
                style = 14.dp,
              ),
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Native Haze backdrop presented a draw").isTrue()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    bitmap.assertVerticalEdgeSoftened(
      centerX = bitmap.width / 2,
      centerY = bitmap.height / 2,
      density = density,
      rendererName = "HazeInput.Backdrop",
    )

    activityScenario.onActivity {
      val area = state.areas.single()
      assertThat(area.captureConsumerCount, "native backdrop capture consumers").isEqualTo(0)
      assertThat(area.contentVersion, "native backdrop source records").isEqualTo(0L)
      assertThat(area.contentLayer, "native backdrop source layer").isEqualTo(null)
    }
  }

  private fun ComponentActivity.copyWindow(): Bitmap {
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

  private companion object {
    const val PROTOTYPE_NODE_COUNT = 4

    fun fullSdkInt(): Int {
      if (Build.VERSION.SDK_INT < 36) return Build.VERSION.SDK_INT * 100_000
      return runCatching {
        Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null)
      }.getOrElse { Build.VERSION.SDK_INT * 100_000 }
    }
  }
}

private fun Modifier.prototypeBackdrop(
  radius: androidx.compose.ui.unit.Dp,
  clip: PrototypeClip?,
  drawReady: CountDownLatch,
): Modifier = this then PrototypeBackdropElement(radius, clip, drawReady)

private enum class PrototypeClip {
  Content,
}

private data class PrototypeBackdropElement(
  val radius: androidx.compose.ui.unit.Dp,
  val clip: PrototypeClip?,
  val drawReady: CountDownLatch,
) : ModifierNodeElement<PrototypeBackdropNode>() {
  override fun create(): PrototypeBackdropNode = PrototypeBackdropNode(radius, clip, drawReady)

  override fun update(node: PrototypeBackdropNode) {
    node.radius = radius
    node.clip = clip
    node.drawReady = drawReady
  }
}

private class PrototypeBackdropNode(
  radius: androidx.compose.ui.unit.Dp,
  clip: PrototypeClip?,
  drawReady: CountDownLatch,
) : Modifier.Node(), DrawModifierNode {
  private val renderer = createHazeBackdropRenderer()
  var radius = radius
  var clip = clip
  var drawReady = drawReady

  override fun ContentDrawScope.draw() {
    assertThat(
      drawContext.canvas.nativeCanvas.isHardwareAccelerated,
      "Backdrop renderer requires a hardware canvas",
    ).isTrue()
    assertThat(renderer.isSupported(drawContext.canvas), "Backdrop renderer is unavailable").isTrue()
    val radiusPx = radius.toPx()
    val effect = createBlurRenderEffect(
      radiusX = radiusPx,
      radiusY = radiusPx,
      tileMode = TileMode.Clamp,
    ) ?: return drawContent()
    check(
      renderer.configure(
        bounds = androidx.compose.ui.geometry.Rect(
          left = -radiusPx,
          top = -radiusPx,
          right = size.width + radiusPx,
          bottom = size.height + radiusPx,
        ),
        clip = if (clip == PrototypeClip.Content) {
          androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
        } else {
          null
        },
        effect = effect,
      ),
    )
    check(renderer.draw(drawContext.canvas))
    drawContent()
    drawReady.countDown()
  }

  override fun onDetach() {
    renderer.release()
  }
}

private fun androidx.compose.ui.unit.Dp.roundToPx(density: Density): Int =
  kotlin.math.round(value * density.density).toInt()

private fun Bitmap.pixel(x: Int, y: Int): Color {
  val pixel = getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
  return Color(
    red = AndroidColor.red(pixel) / 255f,
    green = AndroidColor.green(pixel) / 255f,
    blue = AndroidColor.blue(pixel) / 255f,
    alpha = AndroidColor.alpha(pixel) / 255f,
  )
}

private fun Bitmap.assertVerticalEdgeSoftened(
  centerX: Int,
  centerY: Int,
  density: Density,
  rendererName: String,
) {
  val blackInterior = pixel(centerX - 60.dp.roundToPx(density), centerY)
  val blackNearEdge = pixel(centerX - 3.dp.roundToPx(density), centerY)
  val whiteNearEdge = pixel(centerX + 3.dp.roundToPx(density), centerY)
  val whiteInterior = pixel(centerX + 60.dp.roundToPx(density), centerY)

  assertThat(
    blackNearEdge.red - blackInterior.red,
    "$rendererName softens the black side of the earlier edge",
  ).isGreaterThan(0.05f)
  assertThat(
    whiteInterior.red - whiteNearEdge.red,
    "$rendererName softens the white side of the earlier edge",
  ).isGreaterThan(0.05f)
}

private class BackdropSourceView(context: Context) : View(context) {
  private val paint = Paint()

  override fun onDraw(canvas: AndroidCanvas) {
    val edge = width / 2f
    paint.color = AndroidColor.BLACK
    canvas.drawRect(0f, 0f, edge, height.toFloat(), paint)
    paint.color = AndroidColor.WHITE
    canvas.drawRect(edge, 0f, width.toFloat(), height.toFloat(), paint)
  }
}

private class BackdropProbeView(
  context: Context,
  private val drawReady: CountDownLatch,
) : View(context) {
  override fun onDraw(canvas: AndroidCanvas) {
    canvas.drawColor(AndroidColor.argb(0x99, 0xF0, 0xF0, 0xF0))
    drawReady.countDown()
  }
}

private class BackdropBlurEffectFactory(
  private val drawReady: CountDownLatch,
) : HazeEffectFactory<androidx.compose.ui.unit.Dp> {
  override fun createRenderer(): HazeEffectRenderer<androidx.compose.ui.unit.Dp> =
    BackdropBlurEffectRenderer(drawReady)
}

private class BackdropBlurEffectRenderer(
  private val drawReady: CountDownLatch,
) :
  HazeEffectRenderer<androidx.compose.ui.unit.Dp>,
  HazeEffectRendererBackdrop<androidx.compose.ui.unit.Dp>,
  HazeEffectRendererDrawHooks<androidx.compose.ui.unit.Dp> {

  override fun HazeEffectDrawScope.draw(style: androidx.compose.ui.unit.Dp) {
    drawInput()
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(
    style: androidx.compose.ui.unit.Dp,
  ): androidx.compose.ui.geometry.Rect = modifierBounds.inflate(style.toPx())

  override fun HazeEffectRuntimeDrawScope.backdropEffect(
    style: androidx.compose.ui.unit.Dp,
  ): HazeEffectBackdrop? {
    val radius = style.toPx()
    val effect = createBlurRenderEffect(
      radiusX = radius,
      radiusY = radius,
      tileMode = TileMode.Clamp,
    ) ?: return null
    return HazeEffectBackdrop(effect)
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: androidx.compose.ui.unit.Dp) {
    drawReady.countDown()
  }

  override fun shouldClipToNodeBounds(): Boolean = true
}
