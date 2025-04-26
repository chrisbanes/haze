package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


internal class OpenGlBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null

  private var rendererThread: GlRendererThread? = null

  private val graphicsContext: GraphicsContext
    get() = node.currentValueOf(LocalGraphicsContext)

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

  private val density: Density
    get() = node.requireDensity()

  // private val surface: Surface = Surface()

  init {

    if (rendererThread == null) {
//      rendererThread = RendererThread(holder.getSurface()).apply {
//        startRendering()
//      }
    }
  }

  override fun DrawScope.drawEffect() {
    val context = node.currentValueOf(LocalContext)
    val offset = node.layerOffset
    val scaleFactor = node.calculateInputScaleFactor()
    val blurRadiusPx = scaleFactor * with(density) { node.resolveBlurRadius().toPx() }

    HazeLogger.d(TAG) { "drawEffect. blurRadius=${blurRadiusPx}px. scaleFactor=$scaleFactor" }

    createScaledContentLayer(
      node = node,
      scaleFactor = scaleFactor,
      layerSize = node.layerSize,
      layerOffset = offset,
    )?.let { layer ->
      if (contentLayer.size == IntSize.Zero) {
        // If the layer is released, or doesn't have a size yet, we'll generate
        // this blocking, so that the user doesn't see an un-blurred first frame
        runBlocking {
          updateSurface(content = layer, blurRadius = blurRadiusPx)
          // Release the graphics layer
          graphicsContext.releaseGraphicsLayer(layer)
        }
      } else {
        currentJob = node.coroutineScope.launch(Dispatchers.Main.immediate) {
          updateSurface(content = layer, blurRadius = blurRadiusPx)
          // Release the graphics layer
          graphicsContext.releaseGraphicsLayer(layer)
        }
      }
    }

    graphicsContext.withGraphicsLayer { layer ->
      layer.alpha = node.alpha

      val mask = node.mask ?: node.progressive?.asBrush()
      if (mask != null) {
        // If we have a mask, this needs to be drawn offscreen
        layer.compositingStrategy = CompositingStrategy.Companion.Offscreen
      }

      layer.record(size = contentLayer.size) {
        drawLayer(contentLayer)

        val contentSize = ceil(node.size * scaleFactor)
        val contentOffset = offset * scaleFactor

        translate(contentOffset) {
          // Draw the noise on top...
          val noiseFactor = node.resolveNoiseFactor()
          if (noiseFactor > 0f) {
            PaintPool.usePaint { paint ->
              val texture = context.getNoiseTexture(noiseFactor, scaleFactor)
              paint.shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
              drawContext.canvas.drawRect(contentSize.toRect(), paint)
            }
          }

          // Then the tints...
          for (tint in node.resolveTints()) {
            drawScrim(tint = tint, node = node, size = contentSize)
          }

          if (mask != null) {
            HazeLogger.d(TAG) {
              "Drawing mask. contentSize=$contentSize, offset=$contentOffset, canvas size=$size"
            }
            drawRect(brush = mask, size = contentSize, blendMode = BlendMode.Companion.DstIn)
          }
        }
      }

      drawScaledContent(offset = -offset, scaleFactor = scaleFactor) {
        drawLayer(layer)
      }
    }
  }

  private suspend fun updateSurface(content: GraphicsLayer, blurRadius: Float) {
    val output: Bitmap? = null // TODO

    if (output != null) {
      contentLayer.record(
        density = density,
        layoutDirection = node.currentValueOf(LocalLayoutDirection),
        size = IntSize(output.width, output.height),
      ) {
        drawImage(output.asImageBitmap())
      }
    }

    HazeLogger.d(TAG) { "Output updated in layer" }
  }

  override fun cleanup() {
    currentJob?.cancel()
    graphicsContext.releaseGraphicsLayer(contentLayer)
  }

  private fun createTexture(): Int {
    val textures = IntArray(1)

    // Generate the texture to where android view will be rendered
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glGenTextures(1, textures, 0)
    checkEglError(TAG, "Texture generate")

    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
    checkEglError(TAG, "Texture bind")

    GLES20.glTexParameterf(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GL10.GL_TEXTURE_MIN_FILTER,
      GL10.GL_LINEAR.toFloat()
    )
    GLES20.glTexParameterf(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GL10.GL_TEXTURE_MAG_FILTER,
      GL10.GL_LINEAR.toFloat()
    )
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GL10.GL_TEXTURE_WRAP_S,
      GL10.GL_CLAMP_TO_EDGE
    )
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GL10.GL_TEXTURE_WRAP_T,
      GL10.GL_CLAMP_TO_EDGE
    )

    return textures[0]
  }

  companion object {
    const val TAG = "OpenGlBlurEffect"
  }
}

private class GlRendererThread(initialSurface: Surface) : Thread() {
  @Volatile
  private var running = false
  private var surface: Surface? = initialSurface

  // EGL variables
  private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
  private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

  // Surface dimensions
  private var surfaceWidth: Int = -1
  private var surfaceHeight: Int = -1

  // OpenGL variables
  private var glProgram: Int = 0
  private var positionHandle: Int = -1

  // Allocate and prepare vertex buffer
  private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(TRIANGLE_COORDS.size * 4)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply {
      put(TRIANGLE_COORDS)
      position(0)
    }

  companion object {
    private const val TAG = "RendererThread"

    private const val VERTEX_SHADER_SOURCE = """
        attribute vec4 aPosition;
        void main() {
          gl_Position = aPosition;
        }
        """
    private const val FRAGMENT_SHADER_SOURCE = """
        precision mediump float;
        void main() {
          gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0); // Green color
        }
        """

    // Geometry data (as shown in step 1)
    private val TRIANGLE_COORDS = floatArrayOf(
      0.0f, 0.5f, 0.0f, -0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f
    )
    private const val COORDS_PER_VERTEX = 3
    private const val VERTEX_STRIDE = COORDS_PER_VERTEX * 4
    private val VERTEX_COUNT = TRIANGLE_COORDS.size / COORDS_PER_VERTEX

    // --- Helper Functions ---

    private fun loadShader(type: Int, source: String): Int {
      val shader = GLES20.glCreateShader(type)
      if (shader == 0) {
        Log.e(TAG, "Could not create shader type $type")
        return 0
      }
      GLES20.glShaderSource(shader, source)
      GLES20.glCompileShader(shader)
      val compiled = IntArray(1)
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        Log.e(TAG, "Could not compile shader type $type:")
        Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        return 0
      }

      return shader
    }

    private fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
      val program = GLES20.glCreateProgram()
      if (program == 0) {
        Log.e(TAG, "Could not create GL program")
        return 0
      }
      GLES20.glAttachShader(program, vertexShader)
      GLES20.glAttachShader(program, fragmentShader)
      GLES20.glLinkProgram(program)
      val linkStatus = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] == 0) {
        Log.e(TAG, "Could not link GL program:")
        Log.e(TAG, GLES20.glGetProgramInfoLog(program))
        GLES20.glDeleteProgram(program)
        return 0
      }
      return program
    }
  } // End Companion Object

  fun startRendering() {
    if (!running) {
      running = true
      start() // Start the thread execution (calls run())
    }
  }

  fun stopRendering() {
    running = false // Signal the loop to stop
  }

  // Called by SurfaceView when surface is (re)created
  fun onSurfaceCreated(newSurface: Surface) {
    Log.d(TAG, "Renderer notified: surfaceCreated")
    // If EGL setup depends on the specific surface instance and needs reset:
    // 1. Signal the thread to re-initialize EGL with the new surface.
    // 2. Or, handle surface replacement *within* the EGL init if possible
    // For this simple case, we assume the initial surface passed to constructor
    // is the one we use until destroyed. If surface recreation needs complex
    // EGL re-init, more synchronization logic would be needed.
    // We *could* update the surface reference here IF the EGL surface creation
    // happens dynamically in the loop based on this reference.
    this.surface = newSurface
  }


  // Called by SurfaceView when surface dimensions change
  fun onSurfaceChanged(width: Int, height: Int) {
    Log.d(TAG, "Renderer notified: surfaceChanged ($width x $height)")
    // Thread-safe update might require synchronization if accessed concurrently
    // but usually called from UI thread -> posted to render thread is safer.
    // For simplicity, direct update assuming run loop checks before use.
    surfaceWidth = width
    surfaceHeight = height
  }


  override fun run() {
    Log.d(TAG, "Renderer thread starting.")
    if (!initEgl()) {
      Log.e(TAG, "EGL initialization failed.")
      cleanupEgl()
      return
    }

    if (!initGl()) {
      Log.e(TAG, "OpenGL ES initialization failed.")
      cleanupGl()
      cleanupEgl()
      return
    }

    Log.d(TAG, "EGL and GL initialized successfully.")

    while (running) {
      // Check if surface is ready and dimensions are set
      if (surfaceWidth > 0 && surfaceHeight > 0 && eglSurface != EGL14.EGL_NO_SURFACE) {
        drawFrame()
      } else {
        // Optional: Wait briefly if surface isn't ready yet
        try {
          sleep(10)
        } catch (e: InterruptedException) {
          currentThread().interrupt()
          // Exit loop on interrupt
          running = false
        }
      }
    }

    Log.d(TAG, "Renderer thread stopping. Cleaning up...")
    cleanupGl()
    cleanupEgl()
    surface = null // Release surface reference
    Log.d(TAG, "Renderer thread finished.")
  }

  private fun initEgl(): Boolean {
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      Log.e(TAG, "eglGetDisplay failed")
      return false
    }

    val version = IntArray(2)
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      Log.e(TAG, "eglInitialize failed")
      eglDisplay = EGL14.EGL_NO_DISPLAY
      return false
    }
    Log.d(TAG, "EGL version: ${version[0]}.${version[1]}")

    // Specify attributes for EGLConfig (find a config supporting our surface)
    val attribList = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // Request GLES 2
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_NONE
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
      Log.e(TAG, "eglChooseConfig failed")
      return false
    }

    if (numConfigs[0] == 0) {
      Log.e(TAG, "No suitable EGLConfig found")
      return false
    }
    val eglConfig = configs[0]

    // Create EGLContext
    val contextAttribs = intArrayOf(
      EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, // Request GLES 2.0 context
      EGL14.EGL_NONE
    )
    eglContext =
      EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      checkEglError(TAG, "eglCreateContext")
      return false
    }

    // Create EGLSurface from the Android Surface
    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
    // Ensure surface is valid before creating EGLSurface
    if (surface?.isValid != true) {
      Log.e(TAG, "Android surface is invalid before creating EGL surface.")
      return false
    }

    eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
    if (eglSurface == EGL14.EGL_NO_SURFACE) {
      checkEglError(TAG, "eglCreateWindowSurface")
      return false
    }

    // Make the context current
    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      checkEglError(TAG, "eglMakeCurrent")
      return false
    }

    return true
  }

  private fun initGl(): Boolean {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE)
    val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE)
    if (vertexShader == 0 || fragmentShader == 0) return false

    glProgram = createProgram(vertexShader, fragmentShader)
    if (glProgram == 0) return false

    // No need to keep shaders attached after successful linking
    GLES20.glDetachShader(glProgram, vertexShader)
    GLES20.glDetachShader(glProgram, fragmentShader)
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    checkGlError(TAG, "Shader setup")

    positionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
    if (positionHandle == -1) {
      Log.e(TAG, "Could not get attrib location for aPosition")
      return false
    }
    checkGlError(TAG, "glGetAttribLocation")

    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f) // Set background dark grey
    return true
  }

  private fun drawFrame() {
    if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE) {
      Log.w(TAG, "drawFrame called without valid EGL context/surface")
      return
    }

    // Set the viewport
    GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    checkGlError(TAG, "glViewport")

    // Clear the color buffer
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    checkGlError(TAG, "glClear")

    // Use the program
    GLES20.glUseProgram(glProgram)
    checkGlError(TAG, "glUseProgram")

    // Enable vertex attribute
    GLES20.glEnableVertexAttribArray(positionHandle)
    checkGlError(TAG, "glEnableVertexAttribArray")

    // Prepare the triangle coordinate data
    GLES20.glVertexAttribPointer(
      positionHandle, COORDS_PER_VERTEX,
      GLES20.GL_FLOAT, false,
      VERTEX_STRIDE, vertexBuffer
    )
    checkGlError(TAG, "glVertexAttribPointer")

    // Draw the triangle
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_COUNT)
    checkGlError(TAG, "glDrawArrays")

    // Disable vertex array
    GLES20.glDisableVertexAttribArray(positionHandle)
    checkGlError(TAG, "glDisableVertexAttribArray")

    // Swap buffers to display the rendered frame
    if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
      checkEglError(TAG, "eglSwapBuffers")
      // Handle error, maybe stop rendering? Surface might be invalid.
      // running = false // Example: Stop if swap fails
    }
  }


  private fun cleanupGl() {
    Log.d(TAG, "Cleaning up GL resources")
    if (glProgram != 0) {
      GLES20.glDeleteProgram(glProgram)
      glProgram = 0
    }
    // Add deletion for VBOs, textures etc. if used
  }

  private fun cleanupEgl() {
    Log.d(TAG, "Cleaning up EGL resources")
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      // Important: Make context non-current before destroying surface/context
      EGL14.eglMakeCurrent(
        eglDisplay,
        EGL14.EGL_NO_SURFACE,
        EGL14.EGL_NO_SURFACE,
        EGL14.EGL_NO_CONTEXT
      )

      if (eglSurface != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
      }
      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        eglContext = EGL14.EGL_NO_CONTEXT
      }
      EGL14.eglTerminate(eglDisplay)
      eglDisplay = EGL14.EGL_NO_DISPLAY
    }
  }
} // End RendererThread class

private fun checkEglError(tag: String, msg: String) {
  val error = EGL14.eglGetError()
  if (error != EGL14.EGL_SUCCESS) {
    Log.e(tag, "$msg: EGL error: 0x${Integer.toHexString(error)}")
  }
}

private fun checkGlError(tag: String, op: String) {
  val error = GLES20.glGetError()
  if (error != GLES20.GL_NO_ERROR) {
    Log.e(tag, "$op: glError 0x${Integer.toHexString(error)}")
    // Consider throwing an exception in critical areas
  }
}
