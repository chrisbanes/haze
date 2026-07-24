// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.dropbox.differ.ImageComparator.ComparisonResult
import kotlin.test.Test

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

    assertThat(result).isTrue()
    assertThat(messages).isEqualTo(emptyList())
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

    assertThat(result).isFalse()
    assertThat(messages).isEqualTo(
      listOf(
        "Roborazzi image diff: 0.81% unmatched " +
          "(81/10000 pixels, threshold 0.80%, maxDistance 0.02, hShift 2, vShift 2)",
      ),
    )
  }

  @Test
  fun roborazziOptions_acceptsCustomThreshold() {
    val options = HazeRoborazziDefaults.roborazziOptions(
      unmatchedPixelThreshold = 0.014f,
    )

    assertThat(options.recordOptions.resizeScale).isEqualTo(0.7)
    assertThat(
      options.compareOptions.resultValidator(
        ComparisonResult(
          pixelDifferences = 13_100,
          pixelCount = 1_000_000,
          width = 1_000,
          height = 1_000,
        ),
      ),
    ).isTrue()
  }
}
