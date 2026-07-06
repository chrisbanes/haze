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
  fun resultValidator_logsDiffPercentage() {
    val messages = mutableListOf<String>()
    val validator = createRoborazziResultValidator(log = messages::add)

    val result = validator(
      ComparisonResult(
        pixelDifferences = 100,
        pixelCount = 10_000,
        width = 100,
        height = 100,
      ),
    )

    assertTrue(result)
    assertEquals(
      listOf(
        "Roborazzi image diff: 1.00% unmatched " +
          "(100/10000 pixels, threshold 1.00%, maxDistance 0.02, hShift 2, vShift 2)",
      ),
      messages,
    )
  }

  @Test
  fun resultValidator_rejectsDiffsOverThreshold() {
    val validator = createRoborazziResultValidator(log = {})

    val result = validator(
      ComparisonResult(
        pixelDifferences = 101,
        pixelCount = 10_000,
        width = 100,
        height = 100,
      ),
    )

    assertFalse(result)
  }
}
