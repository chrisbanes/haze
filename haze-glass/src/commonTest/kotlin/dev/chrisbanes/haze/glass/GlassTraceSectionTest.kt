// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test

class GlassTraceSectionTest {
  @Test
  fun names_areStableUniqueAndWithinAndroidLimit() {
    assertThat(GlassTraceSection.all).isEqualTo(
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
        "HazeGlass.fallbackDraw",
        "HazeGlass.fallbackForeground",
      ),
    )
    assertThat(GlassTraceSection.all.toSet().size).isEqualTo(GlassTraceSection.all.size)
    GlassTraceSection.all.forEach { name ->
      assertThat(name.length, name = name).isLessThanOrEqualTo(127)
    }
  }
}
