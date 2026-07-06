// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.ImageComparator.ComparisonResult
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import kotlin.math.roundToInt

private const val CHANGE_THRESHOLD = 0.01f

object HazeRoborazziDefaults {
  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      resultValidator = createRoborazziResultValidator(),
      imageComparator = SimpleImageComparator(maxDistance = 0.01f, hShift = 2, vShift = 2),
    ),
  )
}

internal fun createRoborazziResultValidator(
  log: (String) -> Unit = ::println,
): (ComparisonResult) -> Boolean = { result ->
  val changedPixelsPercentage = result.changedPixelsPercentage()
  log(
    "Roborazzi image diff: ${changedPixelsPercentage.formatPercentage()}% changed " +
      "(${result.pixelDifferences}/${result.pixelCount} pixels, " +
      "threshold ${(CHANGE_THRESHOLD * 100).formatPercentage()}%)",
  )

  changedPixelsPercentage <= CHANGE_THRESHOLD * 100
}

private fun ComparisonResult.changedPixelsPercentage(): Float {
  if (pixelCount == 0) return 0f
  return pixelDifferences.toFloat() / pixelCount * 100
}

private fun Float.formatPercentage(): String {
  val rounded = (this * 100).roundToInt()
  return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')}"
}
