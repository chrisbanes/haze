// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

/** SDK 28 has no RuntimeShader, so every cluster draws through the fallback renderer. */
@Config(sdk = [28], qualifiers = "w393dp-h698dp-440dpi")
class GlassContainerFallbackAndroidScreenshotTest : ScreenshotTest() {
  @Test fun overlappingMembers() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Overlap, fallback = true) }

  @Test fun separateGap20() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Separate, fallback = true) }

  @Test fun mixedTintAndBackground() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.MixedColours, fallback = true) }

  @Test fun groupedMembersDrawOneShape() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Grouped, fallback = true) }
}
