// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze // Replace with your actual package name

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.ImageFormat
import android.graphics.RenderNode
import android.graphics.SurfaceTexture
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix as GlMatrix
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.sqrt

@RequiresApi(29)
class OpenGlRenderNodeBlurrer() {

  private companion object {
    const val TAG = "RenderNodeBlurrer"
    const val MAX_RADIUS: Int = 25 // Define max blur radius

    // Max samples needed for MAX_RADIUS = 2 * MAX_RADIUS + 1
    private const val MAX_SAMPLES: Int = 2 * MAX_RADIUS + 1

    // Basic Vertex Shader
    const val VERTEX_SHADER_CODE = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

    // Gaussian Blur Fragment Shader (Updated for MAX_SAMPLES)
    const val GAUSSIAN_FRAGMENT_SHADER_CODE = """
            #define MAX_SAMPLES $MAX_SAMPLES
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uOffsets[MAX_SAMPLES];
            uniform float uWeights[MAX_SAMPLES];
            uniform int uNumSamples;
            uniform vec2 uDirection; // Blur direction (e.g., vec2(1.0/width, 0.0) for horizontal)

            void main() {
                vec4 color = vec4(0.0);
                // Use loop up to the current number of samples (uNumSamples)
                // Clamp loop iterations just in case uNumSamples exceeds MAX_SAMPLES
                int numSamplesClamped = (uNumSamples > MAX_SAMPLES) ? MAX_SAMPLES : uNumSamples;
                for (int i = 0; i < numSamplesClamped; ++i) {
                    color += texture2D(sTexture, vTexCoord + uDirection * uOffsets[i]) * uWeights[i];
                }
                gl_FragColor = color;
            }
        """

    // Vertex data for a screen-filling quad
    val FULL_QUAD_COORDS = floatArrayOf(
      -1.0f,
      -1.0f, // bottom left
      1.0f,
      -1.0f, // bottom right
      -1.0f,
      1.0f, // top left
      1.0f,
      1.0f, // top right
    )

    // Texture coordinates (flipped vertically for Android textures)
    val FLIPPED_TEX_COORDS = floatArrayOf(
      0.0f,
      1.0f, // bottom left
      1.0f,
      1.0f, // bottom right
      0.0f,
      0.0f, // top left
      1.0f,
      0.0f, // top right
    )

    // Buffer for vertex coordinates
    val quadVertexBuffer: FloatBuffer =
      ByteBuffer.allocateDirect(FULL_QUAD_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(FULL_QUAD_COORDS)
          position(0)
        }

    // Buffer for texture coordinates
    val quadTexCoordBuffer: FloatBuffer =
      ByteBuffer.allocateDirect(FLIPPED_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(FLIPPED_TEX_COORDS)
          position(0)
        }

    // Compiles a shader.
    fun compileShader(type: Int, shaderCode: String): Int {
      val shader = GLES20.glCreateShader(type)
      checkGlError("glCreateShader type $type")
      GLES20.glShaderSource(shader, shaderCode)
      checkGlError("glShaderSource")
      GLES20.glCompileShader(shader)
      checkGlError("glCompileShader")

      val compiled = IntArray(1)
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        HazeLogger.d(TAG) {
          "Could not compile shader $type: ${GLES20.glGetShaderInfoLog(shader)}"
        }
        GLES20.glDeleteShader(shader)
        return 0
      }
      return shader
    }

    // Creates a GL program.
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
      val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
      if (vertexShader == 0) error("Failed to compile vertex shader")
      val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
      if (fragmentShader == 0) {
        GLES20.glDeleteShader(vertexShader)
        error("Failed to compile fragment shader")
      }

      val program = GLES20.glCreateProgram()
      checkGlError("glCreateProgram")
      if (program == 0) error("Could not create program")

      GLES20.glAttachShader(program, vertexShader)
      checkGlError("glAttachShader vertex")

      GLES20.glAttachShader(program, fragmentShader)
      checkGlError("glAttachShader fragment")

      GLES20.glLinkProgram(program)
      checkGlError("glLinkProgram")

      val linkStatus = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] != GLES20.GL_TRUE) {
        HazeLogger.d(TAG) { "Could not link program: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteProgram(program)
        error("Failed to link program")
      }

      GLES20.glDetachShader(program, vertexShader)
      GLES20.glDetachShader(program, fragmentShader)
      GLES20.glDeleteShader(vertexShader)
      GLES20.glDeleteShader(fragmentShader)

      return program
    }

    // Checks for GL errors.
    fun checkGlError(op: String) {
      val error: Int = GLES20.glGetError()
      if (error != GLES20.GL_NO_ERROR) {
        HazeLogger.d(TAG) { "$op: glError ${glGetErrorString(error)}" }
      }
    }

    // Converts GL error code to string.
    fun glGetErrorString(error: Int): String = when (error) {
      GLES20.GL_NO_ERROR -> "GL_NO_ERROR"
      GLES20.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
      GLES20.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
      GLES20.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
      GLES20.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
      GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
      else -> "UNKNOWN GL ERROR (0x${Integer.toHexString(error)})"
    }

    // Calculates Gaussian weights and offsets.
    fun calculateGaussianKernel(radius: Int, sigma: Float): Pair<FloatArray, FloatArray> {
      // Radius is already clamped before calling this function
      val size = radius * 2 + 1
      if (size > MAX_SAMPLES) {
        error("Calculated kernel size $size exceeds MAX_SAMPLES $MAX_SAMPLES for radius $radius")
      }

      val weights = FloatArray(size)
      val offsets = FloatArray(size)
      var sum = 0f

      for (i in 0 until size) {
        val x = (i - radius).toFloat()
        weights[i] = (exp(-(x * x) / (2 * sigma * sigma)) / (sqrt(2 * Math.PI.toFloat()) * sigma))
        sum += weights[i]
      }
      // Normalize weights
      for (i in 0 until size) {
        weights[i] /= sum
      }
      // Calculate sample offsets
      for (i in 0 until size) {
        offsets[i] = (i - radius).toFloat()
      }

      return Pair(weights, offsets)
    }
  }

  // EGL
  private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
  private var eglConfig: EGLConfig? = null
  private var dummyPbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE

  private val hardwareRenderer = HardwareRenderer()

  // OpenGL Resources
  private var blurProgram = 0 // Back to single program for Gaussian

  // Handles for Gaussian blur program
  private var vPositionHandle = -1
  private var aTexCoordHandle = -1
  private var uMVPMatrixHandle = -1
  private var uTexMatrixHandle = -1
  private var sTextureHandle = -1
  private var uOffsetsHandle = -1
  private var uWeightsHandle = -1
  private var uNumSamplesHandle = -1
  private var uDirectionHandle = -1

  // Textures: 0:Input, 1:Intermediate
  private val textureHandles = IntArray(2) { 0 }

  // FBOs: 0:Targets Tex1
  private val fboHandles = IntArray(1) { 0 }
  private val vaoHandle = IntArray(1) { 0 }
  private val vboHandles = IntArray(2) { 0 }

  // State Cache
  @Volatile
  private var currentWidth: Int = -1

  @Volatile
  private var currentHeight: Int = -1

  @Volatile
  private var currentRadius: Int = -1 // Cache the actual radius used

  private lateinit var blurWeights: FloatArray // Gaussian weights
  private lateinit var blurOffsets: FloatArray // Gaussian offsets
  private var numSamples: Int = 0 // Number of samples for current kernel

  // Output
  private var imageReader: ImageReader? = null
  private var outputEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
  private val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

  // Input
  private var inputSurfaceTexture: SurfaceTexture? = null
  private var inputSurface: Surface? = null

  // Matrices
  private val mvpMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val scratchMatrix = FloatArray(16)
  private val textureTransformMatrix = FloatArray(16)

  @Volatile
  private var initialized = false

  private val resourceLock = Any()

  init {
    // Initialize matrices
    GlMatrix.setIdentityM(viewMatrix, 0)
    GlMatrix.setIdentityM(projectionMatrix, 0)
    GlMatrix.setIdentityM(mvpMatrix, 0)
    GlMatrix.setIdentityM(scratchMatrix, 0)
    GlMatrix.setIdentityM(textureTransformMatrix, 0)
  }

  /**
   * Initializes EGL and GL resources if not already done.
   */
  fun initialize() = synchronized(resourceLock) {
    if (initialized) return

    try {
      setupEgl()
      if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT || dummyPbufferSurface == EGL14.EGL_NO_SURFACE) {
        HazeLogger.d(TAG) { "ERROR: EGL not ready, cannot initialize GL resources." }
        return
      }
      makeCurrent(dummyPbufferSurface)
      setupGlResources()
      initialized = true
      HazeLogger.d(TAG) { "RenderNodeBlurrer initialized (Gaussian)." }
    } catch (e: Exception) {
      HazeLogger.d(TAG) { "ERROR: Initialization failed: ${e.message}" }
      closeEgl() // Cleanup partial EGL setup on error
      initialized = false
    } finally {
      // Release context from thread after setup/failure
      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        EGL14.eglMakeCurrent(
          eglDisplay,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT,
        )
      }
    }
  }

  // Sets up EGL Display, Context, and dummy Pbuffer surface
  private fun setupEgl() {
    // Assumes called within resourceLock
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (eglDisplay === EGL14.EGL_NO_DISPLAY) error("Unable to get EGL14 display")

    val version = IntArray(2)
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      val error = EGL14.eglGetError()
      eglDisplay = EGL14.EGL_NO_DISPLAY // Reset on failure
      error("Unable to initialize EGL14, error: ${eglGetErrorString(error)}")
    }
    HazeLogger.d(TAG) { "EGL Initialized: version ${version[0]}.${version[1]}" }

    val attribList = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, // Try ES3
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_NONE,
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    var configFound =
      EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
    if (!configFound || numConfigs[0] < 1) {
      HazeLogger.d(TAG) { "ES3 config not found, falling back to ES2." }
      attribList[9] = EGL14.EGL_OPENGL_ES2_BIT
      configFound =
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
    }
    if (!configFound || numConfigs[0] < 1) {
      error("Unable to find suitable EGL config (ES3 or ES2)")
    }
    eglConfig = configs[0]!!
    val ctxAttribList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
    eglContext =
      EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribList, 0)

    if (eglContext === EGL14.EGL_NO_CONTEXT) {
      HazeLogger.d(TAG) { "ES3 context creation failed, falling back to ES2." }
      ctxAttribList[1] = 2
      eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribList, 0)
      if (eglContext === EGL14.EGL_NO_CONTEXT) {
        error("Unable to create EGL context (ES3 or ES2)")
      }
    }

    val clientVersion = IntArray(1)
    EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, 0)
    HazeLogger.d(TAG) { "Created EGL context client version: ${clientVersion[0]}" }

    val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
    dummyPbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
    if (dummyPbufferSurface === EGL14.EGL_NO_SURFACE) {
      error("Unable to create dummy Pbuffer surface")
    }
    checkEglError("setupEgl")
    HazeLogger.d(TAG) { "EGL Setup Complete." }
  }

  // Sets up non-size-dependent GL resources (Gaussian program, VAO, VBOs)
  private fun setupGlResources() {
    // Assumes EGL context is current, called within resourceLock
    HazeLogger.d(TAG) { "Setting up GL resources (Gaussian Blur program)." }

    // --- Create Gaussian Shader Program ---
    if (blurProgram != 0) {
      GLES20.glDeleteProgram(blurProgram)
    }
    blurProgram = createProgram(VERTEX_SHADER_CODE, GAUSSIAN_FRAGMENT_SHADER_CODE)
    if (blurProgram == 0) {
      error("Failed to create Gaussian blur program")
    }

    // --- Get Attrib & Uniform Locations for Gaussian Shader ---
    vPositionHandle = GLES20.glGetAttribLocation(blurProgram, "aPosition")
    aTexCoordHandle = GLES20.glGetAttribLocation(blurProgram, "aTexCoord")
    uMVPMatrixHandle = GLES20.glGetUniformLocation(blurProgram, "uMVPMatrix")
    uTexMatrixHandle = GLES20.glGetUniformLocation(blurProgram, "uTexMatrix")
    sTextureHandle = GLES20.glGetUniformLocation(blurProgram, "sTexture")
    uOffsetsHandle = GLES20.glGetUniformLocation(blurProgram, "uOffsets")
    uWeightsHandle = GLES20.glGetUniformLocation(blurProgram, "uWeights")
    uNumSamplesHandle = GLES20.glGetUniformLocation(blurProgram, "uNumSamples")
    uDirectionHandle = GLES20.glGetUniformLocation(blurProgram, "uDirection")
    checkGlError("Get Gaussian Uniform Locations")

    // --- Setup VAO and VBOs ---
    val useVao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    if (vaoHandle[0] != 0 && useVao) {
      GLES30.glDeleteVertexArrays(1, vaoHandle, 0)
      vaoHandle[0] = 0
    }
    if (vboHandles[0] != 0) {
      GLES20.glDeleteBuffers(vboHandles.size, vboHandles, 0)
      vboHandles.fill(0)
    }
    if (useVao) {
      GLES30.glGenVertexArrays(1, vaoHandle, 0)
      GLES30.glBindVertexArray(vaoHandle[0])
    }
    GLES20.glGenBuffers(vboHandles.size, vboHandles, 0)
    // VBO 0 (Vertex Pos)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboHandles[0])
    GLES20.glBufferData(
      GLES20.GL_ARRAY_BUFFER,
      quadVertexBuffer.capacity() * 4,
      quadVertexBuffer,
      GLES20.GL_STATIC_DRAW,
    )
    GLES20.glEnableVertexAttribArray(vPositionHandle)
    GLES20.glVertexAttribPointer(vPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    // VBO 1 (Tex Coords)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboHandles[1])
    GLES20.glBufferData(
      GLES20.GL_ARRAY_BUFFER,
      quadTexCoordBuffer.capacity() * 4,
      quadTexCoordBuffer,
      GLES20.GL_STATIC_DRAW,
    )
    GLES20.glEnableVertexAttribArray(aTexCoordHandle)
    GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    if (useVao) GLES30.glBindVertexArray(0)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    checkGlError("VBO/VAO Setup")

    GLES20.glDisable(GLES20.GL_CULL_FACE)
    GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    checkGlError("setupGlResources end state set")
    HazeLogger.d(TAG) { "GL Resources Setup Complete (Gaussian)." }
  }

  // Sets up or updates size-dependent GL resources for Gaussian Blur
  private fun setupSizeDependentResources(width: Int, height: Int, radius: Int) {
    // Called within resourceLock, assumes GL context is current
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
      HazeLogger.d(TAG) { "ERROR: Invalid dimensions for texture resources: ${width}x$height" }
      return
    }
    HazeLogger.d(TAG) {
      "Setting up size-dependent resources for ${width}x$height, radius $radius"
    }
    checkGlError("setupSizeDependentResources start")

    releaseSizeDependentResources()

    // Clamp radius and calculate kernel
    val clampedRadius = radius.coerceIn(0, MAX_RADIUS)
    if (radius > clampedRadius && radius > 0) {
      HazeLogger.d(TAG) { "WARN: Requested radius $radius exceeds MAX_RADIUS $MAX_RADIUS, clamping to $clampedRadius" }
    }

    if (clampedRadius > 0) {
      val sigma = clampedRadius / 3.0f // Simple sigma
      val (weights, offsets) = calculateGaussianKernel(clampedRadius, sigma)
      blurWeights = weights
      blurOffsets = offsets
      numSamples = blurWeights.size
    } else {
      // radius is 0
      blurWeights = floatArrayOf(1.0f)
      blurOffsets = floatArrayOf(0.0f)
      numSamples = 1
    }
    HazeLogger.d(TAG) {
      "Calculated Gaussian kernel for radius $clampedRadius ($numSamples samples)"
    }

    // Create Textures (Size 2: Input, Intermediate)
    GLES20.glGenTextures(textureHandles.size, textureHandles, 0)
    checkGlError("glGenTextures")
    for (i in textureHandles.indices) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandles[i])
      GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_MIN_FILTER,
        GLES20.GL_LINEAR,
      )
      GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_MAG_FILTER,
        GLES20.GL_LINEAR,
      )
      GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_WRAP_S,
        GLES20.GL_CLAMP_TO_EDGE,
      )
      GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D,
        GLES20.GL_TEXTURE_WRAP_T,
        GLES20.GL_CLAMP_TO_EDGE,
      )
      GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        0,
        GLES20.GL_RGBA,
        width,
        height,
        0,
        GLES20.GL_RGBA,
        GLES20.GL_UNSIGNED_BYTE,
        null,
      )
      checkGlError("glTexImage2D texture $i")
    }
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

    // Create FBO (Size 1: Targets Intermediate Tex1)
    GLES20.glGenFramebuffers(fboHandles.size, fboHandles, 0)
    checkGlError("glGenFramebuffers")
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboHandles[0])
    GLES20.glFramebufferTexture2D(
      GLES20.GL_FRAMEBUFFER,
      GLES20.GL_COLOR_ATTACHMENT0,
      GLES20.GL_TEXTURE_2D,
      textureHandles[1],
      0,
    )
    if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      error(
        "FBO 0 not complete",
      )
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    checkGlError("FBO Setup")

    // --- Create SurfaceTexture/Surface (linked to Tex0) ---
    inputSurfaceTexture?.release()
    inputSurfaceTexture = SurfaceTexture(textureHandles[0]).apply {
      setDefaultBufferSize(width, height)
    }
    inputSurface?.release()
    inputSurface = Surface(inputSurfaceTexture)

    // --- Create ImageReader/EGLSurface ---
    imageReader?.close()
    imageReader = ImageReader.newInstance(
      width,
      height,
      ImageFormat.PRIVATE,
      1,
      HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    if (outputEglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroySurface(
        eglDisplay,
        outputEglSurface,
      )
    }
    outputEglSurface = EGL14.eglCreateWindowSurface(
      eglDisplay,
      eglConfig,
      imageReader!!.surface,
      intArrayOf(EGL14.EGL_NONE),
      0,
    )
    if (outputEglSurface === EGL14.EGL_NO_SURFACE) error("Unable to create EGL surface for ImageReader")

    // --- Setup Projection Matrix ---
    GlMatrix.orthoM(projectionMatrix, 0, -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f)
    // Note: flippedProjectionMatrix removed

    // --- Update cache ---
    currentWidth = width
    currentHeight = height
    currentRadius = radius // Cache the *requested* radius

    EGL14.eglMakeCurrent(
      eglDisplay,
      EGL14.EGL_NO_SURFACE,
      EGL14.EGL_NO_SURFACE,
      EGL14.EGL_NO_CONTEXT,
    )
    HazeLogger.d(TAG) { "Size-dependent resources setup complete" }
  }

  // Releases size-dependent GL resources
  private fun releaseSizeDependentResources() {
    // Assumes context is current if needed
    HazeLogger.d(TAG) { "Releasing size-dependent resources." }
    // --- Delete FBOs (Size 1) ---
    if (fboHandles[0] != 0) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      GLES20.glDeleteFramebuffers(fboHandles.size, fboHandles, 0)
      fboHandles.fill(0)
      checkGlError("glDeleteFramebuffers")
      HazeLogger.d(TAG) { "FBOs deleted" }
    }
    // --- Delete Textures (Size 2) ---
    if (textureHandles[0] != 0 || textureHandles[1] != 0) {
      GLES20.glDeleteTextures(textureHandles.size, textureHandles, 0)
      textureHandles.fill(0)
      checkGlError("glDeleteTextures")
      HazeLogger.d(TAG) { "Textures deleted" }
    }
    // --- Release Surfaces/ImageReader ---
    inputSurface?.release()
    inputSurface = null
    inputSurfaceTexture?.release()
    inputSurfaceTexture = null
    HazeLogger.d(TAG) { "Input Surface/SurfaceTexture released" }
    if (outputEglSurface !== EGL14.EGL_NO_SURFACE && eglDisplay !== EGL14.EGL_NO_DISPLAY) {
      if (!EGL14.eglDestroySurface(
          eglDisplay,
          outputEglSurface,
        )
      ) {
        checkEglError("eglDestroySurface (ImageReader)")
      }
      outputEglSurface = EGL14.EGL_NO_SURFACE
      HazeLogger.d(TAG) { "Output EGLSurface released" }
    }
    imageReader?.close()
    imageReader = null
    HazeLogger.d(TAG) { "ImageReader closed" }

    // --- Reset Cache ---
    currentWidth = -1
    currentHeight = -1
    currentRadius = -1
    HazeLogger.d(TAG) { "Size-dependent resources released." }
  }

  /**
   * Blurs the given RenderNode using a 2-pass Gaussian blur.
   *
   * @param renderNode The RenderNode to blur.
   * @param radius The blur radius (clamped 0-25). Pass 0 to skip blur.
   * @return A HardwareBitmap containing the blurred output. Null if an error occurred.
   */
  fun blurRenderNode(renderNode: RenderNode, radius: Int): Bitmap? {
    if (!initialized) {
      HazeLogger.d(TAG) { "WARN: Not initialized, attempting lazy init..." }
      initialize()
      if (!initialized) {
        HazeLogger.d(TAG) { "ERROR: Initialization failed." }
        return null
      }
    }

    val width = renderNode.width
    val height = renderNode.height
    if (width <= 0 || height <= 0) {
      HazeLogger.d(TAG) { "WARN: RenderNode has invalid size: ${width}x$height" }
      return null
    }

    var acquiredImage: android.media.Image? = null
    var hardwareBuffer: HardwareBuffer? = null
    var currentEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE // Track current surface

    synchronized(resourceLock) {
      try {
        // --- Resource Check and Update ---
        if (width != currentWidth || height != currentHeight || radius != currentRadius) {
          HazeLogger.d(TAG) { "Size or radius changed. Setting up resources." }
          makeCurrent(dummyPbufferSurface)
          // Recalculates kernel with clamping
          setupSizeDependentResources(width, height, radius)
        }
        // Final check
        if (inputSurface == null || outputEglSurface === EGL14.EGL_NO_SURFACE || blurProgram == 0 || textureHandles[0] == 0 || fboHandles[0] == 0) {
          HazeLogger.d(TAG) { "ERROR: Essential resources unavailable. Aborting." }
          return null
        }

        // --- RenderNode Draw ---
        hardwareRenderer.setContentRoot(renderNode)
        hardwareRenderer.setSurface(inputSurface!!)
        val renderResult = hardwareRenderer.createRenderRequest().syncAndDraw()
        if (renderResult != HardwareRenderer.SYNC_OK) {
          HazeLogger.d(TAG) { "WARN: HardwareRenderer syncAndDraw failed: $renderResult" }
        }

        // --- OpenGL Blur Passes ---
        makeCurrent(dummyPbufferSurface)
        currentEglSurface = dummyPbufferSurface
        checkGlError("MakeCurrent Dummy")
        inputSurfaceTexture?.updateTexImage()
        inputSurfaceTexture?.getTransformMatrix(textureTransformMatrix)
        checkGlError("updateTexImage / getTransformMatrix")

        val useVao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        // --- Common setup for blur passes ---
        GLES20.glUseProgram(blurProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(sTextureHandle, 0)
        GLES20.glUniform1fv(uOffsetsHandle, numSamples, blurOffsets, 0)
        GLES20.glUniform1fv(uWeightsHandle, numSamples, blurWeights, 0)
        GLES20.glUniform1i(uNumSamplesHandle, numSamples)
        // MVP matrix (use normal projection)
        GlMatrix.setIdentityM(mvpMatrix, 0)
        GlMatrix.multiplyMM(scratchMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GlMatrix.multiplyMM(mvpMatrix, 0, scratchMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        checkGlError("Common Uniform Setup")

        // --- Horizontal Blur Pass (Render Tex0 -> FBO/Tex1) ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboHandles[0])
        GLES20.glViewport(0, 0, currentWidth, currentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandles[0]) // Read Tex0

        GLES20.glUniformMatrix4fv(
          uTexMatrixHandle,
          1,
          false,
          textureTransformMatrix,
          0,
        ) // Use ST matrix
        GLES20.glUniform2f(uDirectionHandle, 1.0f / currentWidth, 0f) // Horizontal
        checkGlError("Horizontal Uniforms")

        if (useVao) GLES30.glBindVertexArray(vaoHandle[0]) else bindVBOs()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (useVao) GLES30.glBindVertexArray(0) // Unbind VAO after draw
        checkGlError("Horizontal Draw")

        // --- Vertical Blur Pass (Render Tex1 -> ImageReader Surface) ---
        makeCurrent(outputEglSurface)
        currentEglSurface = outputEglSurface
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) // Target Default FBO
        GLES20.glViewport(0, 0, currentWidth, currentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandles[1]) // Read Tex1

        GlMatrix.setIdentityM(scratchMatrix, 0) // Identity texture matrix
        GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, scratchMatrix, 0)
        GLES20.glUniform2f(uDirectionHandle, 0f, 1.0f / currentHeight) // Vertical
        checkGlError("Vertical Uniforms")

        if (useVao) GLES30.glBindVertexArray(vaoHandle[0]) else bindVBOs()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (useVao) GLES30.glBindVertexArray(0) // Unbind VAO after draw
        checkGlError("Vertical Draw")

        // --- Finish GL and Get Bitmap ---
        // Unbind resources needed before swap (program, texture already unbound implicitly by use/bind)
        if (!useVao) unbindVBOs() // Unbind VBOs if they were manually bound
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) // Ensure default FBO bound
        checkGlError("Unbind GL objects post-render")

        GLES20.glFlush() // Use flush
        checkGlError("glFlush")
        if (!EGL14.eglSwapBuffers(eglDisplay, outputEglSurface)) {
          checkEglError("eglSwapBuffers failed")
          HazeLogger.d(TAG) { "ERROR: eglSwapBuffers failed" }
          return null
        }
        checkGlError("eglSwapBuffers success")

        // Acquire Image
        acquiredImage = imageReader?.acquireLatestImage()
        if (acquiredImage == null) {
          HazeLogger.d(TAG) { "ERROR: Failed to acquire image from ImageReader." }
          return null
        }

        // Get HardwareBuffer
        hardwareBuffer = acquiredImage.hardwareBuffer
        if (hardwareBuffer == null) {
          HazeLogger.d(TAG) { "ERROR: Acquired image does not contain a HardwareBuffer." }
          acquiredImage.close()
          return null
        }

        // Wrap and return Bitmap
        return Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace).also {
          HazeLogger.d(TAG) { "HardwareBitmap created successfully (Gaussian)." }
          acquiredImage?.close()
          hardwareBuffer?.close()
        }
      } catch (e: Exception) {
        HazeLogger.d(TAG) { "ERROR: Error during blur process: ${e.message}" }
        acquiredImage?.close()
        hardwareBuffer?.close()
        return null
      } finally {
        // Release EGL context from this thread
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
          EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
          )
          checkEglError("eglMakeCurrent cleanup in finally")
        }
        HazeLogger.d(TAG) { "Blur process finished." }
      }
    }
  }

  // Helper for binding VBOs when not using VAO
  private fun bindVBOs() {
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboHandles[0])
    GLES20.glEnableVertexAttribArray(vPositionHandle)
    GLES20.glVertexAttribPointer(vPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboHandles[1])
    GLES20.glEnableVertexAttribArray(aTexCoordHandle)
    GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
    checkGlError("bindVBOs")
  }

  // Helper for unbinding VBOs when not using VAO
  private fun unbindVBOs() {
    GLES20.glDisableVertexAttribArray(vPositionHandle)
    GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    checkGlError("unbindVBOs")
  }

  // Makes the specified EGL surface current on the calling thread.
  private fun makeCurrent(surface: EGLSurface) {
    if (eglDisplay === EGL14.EGL_NO_DISPLAY || eglContext === EGL14.EGL_NO_CONTEXT) {
      if (initialized) HazeLogger.d(TAG) { "ERROR: EGLDisplay/Context invalid. Cannot make current." }
      error("EGL not properly initialized before makeCurrent call")
    }
    if (surface == EGL14.EGL_NO_SURFACE) {
      // HazeLogger.d(TAG) { "Attempted to make EGL_NO_SURFACE current, releasing context." }
      if (!EGL14.eglMakeCurrent(
          eglDisplay,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT,
        )
      ) {
        checkEglError("eglMakeCurrent (Release Context)")
      }
      return
    }
    if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
      val error = EGL14.eglGetError()
      HazeLogger.d(TAG) {
        "ERROR: eglMakeCurrent failed on surface $surface: ${
          eglGetErrorString(
            error,
          )
        } (0x${Integer.toHexString(error)})"
      }
      if (error == EGL14.EGL_CONTEXT_LOST) {
        HazeLogger.d(TAG) { "ERROR: EGL Context Lost - Requires full re-initialization" }
        synchronized(resourceLock) { initialized = false } // Mark as uninitialized
      }
      error("eglMakeCurrent failed severely (see logcat)")
    }
  }

  // Checks for the last EGL error and logs it if present using HazeLogger.
  private fun checkEglError(op: String) {
    val error = EGL14.eglGetError()
    if (error != EGL14.EGL_SUCCESS) {
      HazeLogger.d(TAG) {
        "$op: eglError ${eglGetErrorString(error)} (0x${Integer.toHexString(error)})"
      }
      if (error == EGL14.EGL_CONTEXT_LOST) {
        HazeLogger.d(TAG) { "EGL Context Lost detected during $op" }
        synchronized(resourceLock) { initialized = false }
      }
    }
  }

  // Converts an EGL error code to a human-readable string.
  private fun eglGetErrorString(error: Int): String = when (error) {
    EGL14.EGL_SUCCESS -> "EGL_SUCCESS"
    EGL14.EGL_NOT_INITIALIZED -> "EGL_NOT_INITIALIZED"
    EGL14.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
    EGL14.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
    EGL14.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
    EGL14.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
    EGL14.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
    EGL14.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
    EGL14.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
    EGL14.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
    EGL14.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
    EGL14.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
    EGL14.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
    EGL14.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
    EGL14.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST" // 0x300E
    else -> "UNKNOWN EGL ERROR (0x${Integer.toHexString(error)})"
  }

  /**
   * Releases all EGL and GL resources held by this instance.
   */
  fun close() {
    HazeLogger.d(TAG) { "Closing RenderNodeBlurrer instance (Gaussian)." }
    synchronized(resourceLock) {
      if (!initialized && eglDisplay == EGL14.EGL_NO_DISPLAY) {
        HazeLogger.d(TAG) { "Already closed or never initialized." }
        return
      }

      // Make context current for GL cleanup if possible
      if (initialized && eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT && dummyPbufferSurface != EGL14.EGL_NO_SURFACE) {
        try {
          makeCurrent(dummyPbufferSurface)
        } catch (e: Exception) {
          HazeLogger.d(TAG) { "Exception during makeCurrent in close: ${e.message}" }
        }
      } else {
        HazeLogger.d(TAG) { "WARN: Skipping makeCurrent in close." }
      }

      // Release size-dependent GL resources
      releaseSizeDependentResources()

      // Release non-size-dependent GL resources
      if (blurProgram != 0) {
        GLES20.glDeleteProgram(blurProgram)
        blurProgram = 0
        checkGlError("glDeleteProgram")
      }
      val useVao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
      if (useVao && vaoHandle[0] != 0) {
        GLES30.glDeleteVertexArrays(1, vaoHandle, 0)
        vaoHandle[0] = 0
        checkGlError("glDeleteVertexArrays")
      }
      if (vboHandles[0] != 0 || vboHandles[1] != 0) {
        GLES20.glDeleteBuffers(vboHandles.size, vboHandles, 0)
        vboHandles.fill(0)
        checkGlError("glDeleteBuffers")
      }
      HazeLogger.d(TAG) { "Non-size dependent GL resources released." }

      closeEgl() // Separate EGL cleanup

      hardwareRenderer.destroy()
      initialized = false // Mark as uninitialized
      HazeLogger.d(TAG) { "RenderNodeBlurrer resources fully released (Gaussian)." }
    }
  }

  // Helper to clean up EGL resources, callable from close() or initialization failure
  private fun closeEgl() {
    // Assumes called within resourceLock
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(
        eglDisplay,
        EGL14.EGL_NO_SURFACE,
        EGL14.EGL_NO_SURFACE,
        EGL14.EGL_NO_CONTEXT,
      )
      // outputEglSurface is destroyed in releaseSizeDependentResources
      if (dummyPbufferSurface != EGL14.EGL_NO_SURFACE) {
        if (!EGL14.eglDestroySurface(eglDisplay, dummyPbufferSurface)) {
          checkEglError("eglDestroySurface (dummy)")
        }
        dummyPbufferSurface = EGL14.EGL_NO_SURFACE
      }
      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        if (!EGL14.eglDestroyContext(eglDisplay, eglContext)) {
          checkEglError("eglDestroyContext")
        }
        eglContext = EGL14.EGL_NO_CONTEXT
      }
      if (!EGL14.eglTerminate(eglDisplay)) {
        checkEglError("eglTerminate")
      }
      eglDisplay = EGL14.EGL_NO_DISPLAY
    }
    eglConfig = null // Clear config
    HazeLogger.d(TAG) { "EGL resources released." }
  }
}
