// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.asComposeRenderEffect

/**
 * One interaction stage's shader, the key and uniforms it was last built from, and the layer
 * it was last attached to.
 *
 * [clear] drops the built effect but keeps the shader; [clearLayer] forgets the attached layer;
 * [releaseShader] drops the shader itself. They are separate because callers need each alone.
 */
internal class InteractionEffectSlot<K : Any>(
  private val createShader: () -> MutableRuntimeShaderRenderEffect,
) {
  var shader: MutableRuntimeShaderRenderEffect? = null
    private set
  var key: K? = null
    private set
  var uniforms: GlassInteractionUniforms? = null
    private set
  var platformEffect: PlatformRenderEffect? = null
    private set
  var composeEffect: RenderEffect? = null
    private set
  var layer: GraphicsLayer? = null
    private set

  /** How many times [bind] rebuilt the effect, so tests can count builds per frame. */
  var buildCount: Int = 0
    private set

  /**
   * Rebuilds the effect when [key] or [uniforms] changed and attaches it to [layer] when it
   * changed or [layer] is new. This is inline so a cache hit allocates no uniform lambda.
   *
   * With [attach] false the effect is built but left off [layer], which another effect replaces.
   */
  inline fun bind(
    layer: GraphicsLayer,
    key: K,
    uniforms: GlassInteractionUniforms,
    attach: Boolean = true,
    crossinline setUniforms: RuntimeShaderUniformProvider.() -> Unit,
  ) {
    val currentShader = shader ?: createShader().also { shader = it }
    val needsUpdate = key != this.key || uniforms != this.uniforms || composeEffect == null
    if (needsUpdate) {
      val effect = currentShader.updateUniforms { setUniforms() }
      platformEffect = effect
      composeEffect = effect.asComposeRenderEffect()
      this.key = key
      this.uniforms = uniforms
      buildCount++
    }
    if (!attach) {
      this.layer = null
    } else if (needsUpdate || layer !== this.layer) {
      layer.renderEffect = checkNotNull(composeEffect)
      this.layer = layer
    }
  }

  fun clear() {
    key = null
    uniforms = null
    platformEffect = null
    composeEffect = null
  }

  fun clearLayer() {
    layer = null
  }

  fun releaseShader() {
    shader = null
  }
}

/** The layer, input and key an interaction stage last recorded, so unchanged input is not re-recorded. */
internal class RecordedInteractionLayer<K : Any> {
  var layer: GraphicsLayer? = null
    private set
  var input: GraphicsLayer? = null
    private set
  var key: K? = null
    private set

  fun needsRecord(
    layer: GraphicsLayer,
    input: GraphicsLayer,
    key: K,
    inputContentChanged: Boolean,
  ): Boolean = layer !== this.layer ||
    input !== this.input ||
    key != this.key ||
    inputContentChanged

  fun update(layer: GraphicsLayer, input: GraphicsLayer, key: K) {
    this.layer = layer
    this.input = input
    this.key = key
  }

  fun clear() {
    layer = null
    input = null
    key = null
  }
}
