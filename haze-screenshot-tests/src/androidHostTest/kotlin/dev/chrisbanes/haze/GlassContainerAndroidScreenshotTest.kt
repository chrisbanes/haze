// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassContainerAndroidScreenshotTest : ScreenshotTest() {
  @Test fun mergedGap19() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Merged) }

  @Test fun separateGap20() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Separate) }

  @Test fun mixedTintAndBackground() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.MixedColours) }

  @Test fun regularAndClearRemainSeparate() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.MixedKeys) }

  @Test fun cappedOverflowRemainsSingleton() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Overflow) }

  @Test fun nestedContainersOwnNearestMembers() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Nested) }

  @Test fun interactiveClusterRest() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.InteractiveRest) }

  @Test fun interactiveClusterPressed() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.InteractivePressed) }

  @Test fun groupedMembersDrawOneShape() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.Grouped) }

  @Test fun insetLazyRowPartlyScrolledClipsGlassToList() = runScreenshotTest { assertGlassContainerScene(GlassContainerScene.InsetLazyRow) }

  @Test fun removalRestoresOutsideContainerPixels() = runScreenshotTest { assertGlassContainerRemovalRestoresOutsidePixels() }

  @Test fun memberGrowsOutOfNeighbour() = runScreenshotTest { assertGlassContainerTransitionSequence(grow = true) }

  @Test fun memberRetractsIntoNeighbour() = runScreenshotTest { assertGlassContainerTransitionSequence(grow = false) }
}
