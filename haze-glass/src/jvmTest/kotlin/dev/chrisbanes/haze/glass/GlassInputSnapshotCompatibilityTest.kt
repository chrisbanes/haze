// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.InternalHazeApi
import org.junit.Test

@OptIn(InternalHazeApi::class)
class GlassInputSnapshotCompatibilityTest {
  @Test fun unknownSnapshots_cannotCoalesceFreshness() {
    val unknown = object : HazeEffectInputSnapshot {}
    val source = GlassRuntimeSourceSnapshot(1f, Size(391f, 391f), Offset.Zero, unknown)
    assertThat(source.hasSamePresentationGeometry(source)).isFalse()
  }

  @Test fun copiedCropScaleOffsetAndBackground_requireExactCompatibility() {
    val input = object : HazeEffectInputSnapshot {
      override fun hasSameSourceGeometry(other: HazeEffectInputSnapshot): Boolean = other === this
    }
    fun source(
      scale: Float = 1f,
      size: Size = Size(391f, 391f),
      offset: Offset = Offset.Zero,
      background: Color = Color.Transparent,
    ) = GlassRuntimeSourceSnapshot(scale, size, offset, input, background)
    val original = source()
    assertThat(original.hasSamePresentationGeometry(source())).isTrue()
    assertThat(original.hasSamePresentationGeometry(source(scale = .5f))).isFalse()
    assertThat(original.hasSamePresentationGeometry(source(size = Size(688f, 688f)))).isFalse()
    assertThat(original.hasSamePresentationGeometry(source(offset = Offset(1f, 0f)))).isFalse()
    assertThat(original.hasSamePresentationGeometry(source(background = Color.Red))).isFalse()
  }
}
