// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.test.Test

class NavDisplaySourceJvmScreenshotTest : NavDisplaySourceScreenshotTestBase() {

  @Test
  fun blur_navigationSuiteSibling_midTransition_matchesDirectSibling() =
    runNavigationSuiteSiblingMidTransitionTest()
}
