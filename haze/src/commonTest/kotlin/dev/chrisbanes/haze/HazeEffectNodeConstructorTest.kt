// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalHazeApi::class)
class HazeEffectNodeConstructorTest {

  @Test
  fun constructor_supportsStateOnlyCallSite() {
    val state = HazeState()
    val node = HazeEffectNode(state = state)

    assertThat(node.state).isEqualTo(state)
    assertThat(node.block).isEqualTo(null)
  }

  @Test
  fun constructor_supportsBlockOnlyCallSite() {
    val block: HazeEffectScope.() -> Unit = {}
    val node = HazeEffectNode(block = block)

    assertThat(node.state).isEqualTo(null)
    assertThat(node.block).isEqualTo(block)
  }

  @Test
  fun attachVisualEffect_usesInstanceIdentity_notEquals() {
    val node1 = HazeEffectNode()
    val node2 = HazeEffectNode()
    val effect1 = EqualByTypeVisualEffect("one")
    val effect2 = EqualByTypeVisualEffect("two")

    node1.attachVisualEffect(effect1)
    try {
      // Different instances should not conflict even if equals() returns true
      node2.attachVisualEffect(effect2)
    } finally {
      node1.detachVisualEffect(effect1)
      node2.detachVisualEffect(effect2)
    }
  }

  @Test
  fun attachVisualEffect_cleansRegistryWhenAttachThrows() {
    val node1 = HazeEffectNode()
    val node2 = HazeEffectNode()
    val effect = ThrowOnFirstAttachVisualEffect()

    assertFailsWith<IllegalStateException> {
      node1.attachVisualEffect(effect)
    }

    // Should succeed after failure on another node if attach bookkeeping is rolled back
    node2.attachVisualEffect(effect)
    node2.detachVisualEffect(effect)
  }

  @Test
  fun attachVisualEffect_noopWhenAlreadyAttachedToSameNode() {
    val node = HazeEffectNode()
    val effect = CountingAttachVisualEffect()

    node.attachVisualEffect(effect)
    assertThat(effect.attachCount).isEqualTo(1)

    // Re-attaching the same effect to the same node should be a no-op
    node.attachVisualEffect(effect)
    assertThat(effect.attachCount).isEqualTo(1)

    node.detachVisualEffect(effect)
  }
}

private class EqualByTypeVisualEffect(
  private val id: String,
) : VisualEffect {
  override fun DrawScope.draw(context: VisualEffectContext) = Unit

  override fun equals(other: Any?): Boolean = other is EqualByTypeVisualEffect

  override fun hashCode(): Int = 0

  override fun toString(): String = "EqualByTypeVisualEffect($id)"
}

private class ThrowOnFirstAttachVisualEffect : VisualEffect {
  private var shouldThrow = true

  override fun DrawScope.draw(context: VisualEffectContext) = Unit

  override fun attach(context: VisualEffectContext) {
    if (shouldThrow) {
      shouldThrow = false
      throw IllegalStateException("boom")
    }
  }
}

private class CountingAttachVisualEffect : VisualEffect {
  var attachCount = 0

  override fun DrawScope.draw(context: VisualEffectContext) = Unit

  override fun attach(context: VisualEffectContext) {
    attachCount++
  }
}
