// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.ImageComparator.ComparisonResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HazeRoborazziDefaultsTest {

  @Test
  fun resultValidator_doesNotLogPassingDiffs() {
    val messages = mutableListOf<String>()
    val validator = createRoborazziResultValidator(log = messages::add)

    val result = validator(
      ComparisonResult(
        pixelDifferences = 80,
        pixelCount = 10_000,
        width = 100,
        height = 100,
      ),
    )

    assertTrue(result)
    assertEquals(emptyList(), messages)
  }

  @Test
  fun resultValidator_logsFailingDiffPercentage() {
    val messages = mutableListOf<String>()
    val validator = createRoborazziResultValidator(log = messages::add)

    val result = validator(
      ComparisonResult(
        pixelDifferences = 81,
        pixelCount = 10_000,
        width = 100,
        height = 100,
      ),
    )

    assertFalse(result)
    assertEquals(
      listOf(
        "Roborazzi image diff: 0.81% unmatched " +
          "(81/10000 pixels, threshold 0.80%, maxDistance 0.02, hShift 2, vShift 2)",
      ),
      messages,
    )
  }

  @Test
  fun roborazziOptions_acceptsCustomThreshold() {
    val options = HazeRoborazziDefaults.roborazziOptions(
      unmatchedPixelThreshold = 0.014f,
    )

    assertEquals(0.7, options.recordOptions.resizeScale)
    assertTrue(
      options.compareOptions.resultValidator(
        ComparisonResult(
          pixelDifferences = 13_100,
          pixelCount = 1_000_000,
          width = 1_000,
          height = 1_000,
        ),
      ),
    )
  }
}
