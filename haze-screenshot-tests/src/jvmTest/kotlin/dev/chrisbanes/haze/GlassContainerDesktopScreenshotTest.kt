// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassContainerDesktopScreenshotTest : ScreenshotTest() {
  @Test fun mergedGap19() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.Merged) }

  @Test fun separateGap20() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.Separate) }

  @Test fun mixedTintAndBackground() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.MixedColours) }

  @Test fun regularAndClearRemainSeparate() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.MixedKeys) }

  @Test fun cappedOverflowRemainsSingleton() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.Overflow) }

  @Test fun nestedContainersOwnNearestMembers() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.Nested) }

  @Test fun interactiveClusterRest() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.InteractiveRest) }

  @Test fun interactiveClusterPressed() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.InteractivePressed) }

  @Test fun groupedMembersDrawOneShape() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerScene(GlassContainerScene.Grouped) }

  @Test fun removalRestoresOutsideContainerPixels() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerRemovalRestoresOutsidePixels() }

  @Test fun memberGrowsOutOfNeighbour() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerTransitionSequence(grow = true) }

  @Test fun memberRetractsIntoNeighbour() = runScreenshotTest(Size(360f, 240f)) { assertGlassContainerTransitionSequence(grow = false) }
}
