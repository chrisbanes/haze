// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.drawscope.DrawScope
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassDelegateContractTest {

  @Test
  fun fallbackDelegate_drawsContentBehind() {
    val effect = GlassRuntimeEffect()
    val fallback = FallbackGlassDelegate(effect)

    assertThat(fallback.drawsContentBehind).isTrue()
    assertThat(fallback.usesRuntimeShader).isFalse()
    assertThat(effect.shouldDrawContentBehind()).isTrue()
  }

  @Test
  fun plainDelegate_defaultsAreNoOps() {
    val delegate = PlainGlassDelegate()

    assertThat(delegate.drawsContentBehind).isFalse()
    assertThat(delegate.usesRuntimeShader).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isFalse()
    assertThat(delegate.shouldDrawRetainedOutput()).isFalse()
    delegate.clearRetainedOutput()
    delegate.invalidateRetainedOutput()
    delegate.releaseObsoleteInteractionOutput(NoInteractionTopology)
  }

  @Test
  fun effect_forwardsContentBehindToAnyDelegate() {
    val effect = GlassRuntimeEffect()

    effect.delegate = PlainGlassDelegate(drawsContentBehind = true)
    assertThat(effect.shouldDrawContentBehind()).isTrue()

    effect.delegate = PlainGlassDelegate()
    assertThat(effect.shouldDrawContentBehind()).isFalse()
  }

  @Test
  fun effect_forwardsRetainedOutputToTheDelegate() {
    val effect = GlassRuntimeEffect()
    val delegate = PlainGlassDelegate(retainedOutputAvailable = true)
    effect.delegate = delegate

    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(effect.shouldDrawRetainedOutput()).isTrue()
    effect.invalidateRetainedOutput()
    effect.clearRetainedOutput()
    assertThat(delegate.invalidateCalls).isEqualTo(1)
    assertThat(delegate.clearCalls).isEqualTo(1)

    effect.delegate = PlainGlassDelegate()
    assertThat(effect.canDrawRetainedOutput()).isFalse()
    assertThat(effect.shouldDrawRetainedOutput()).isFalse()
    effect.invalidateRetainedOutput()
    effect.clearRetainedOutput()
  }
}

private val NoInteractionTopology = GlassInteractionTopology(
  hasOptics = false,
  hasLighting = false,
  maxRefractionMultiplier = 1f,
)

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
private class PlainGlassDelegate(
  override val drawsContentBehind: Boolean = false,
  private val retainedOutputAvailable: Boolean = false,
) : GlassRuntimeEffect.Delegate {
  var invalidateCalls = 0
    private set
  var clearCalls = 0
    private set

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) = Unit

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun invalidateRetainedOutput() {
    invalidateCalls++
  }

  override fun clearRetainedOutput() {
    clearCalls++
  }
}
