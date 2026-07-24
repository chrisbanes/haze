// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassTraceSectionTest {
  @Test
  fun names_areStableUniqueAndWithinAndroidLimit() {
    assertEquals(
      listOf(
        "HazeGlass.prepare",
        "HazeGlass.prepareBudget",
        "HazeGlass.selectDelegate",
        "HazeGlass.delegatePrepare",
        "HazeGlass.prepareEffects",
        "HazeGlass.prepareLayers",
        "HazeGlass.createRenderEffect",
        "HazeGlass.runtimeDraw",
        "HazeGlass.source",
        "HazeGlass.blur",
        "HazeGlass.depth",
        "HazeGlass.optical",
        "HazeGlass.detail",
        "HazeGlass.rim",
        "HazeGlass.interactionOptical",
        "HazeGlass.interactionDetail",
        "HazeGlass.interactionLighting",
        "HazeGlass.groupAlpha",
        "HazeGlass.compose",
      ),
      GlassTraceSection.all,
    )
    assertEquals(GlassTraceSection.all.size, GlassTraceSection.all.toSet().size)
    assertTrue(GlassTraceSection.all.all { it.length <= 127 })
  }
}
