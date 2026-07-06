// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.ImageComparator.ComparisonResult
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import kotlin.math.roundToInt

private const val CHANGE_THRESHOLD = 0.01f
private const val MAX_DISTANCE = 0.01f
private const val H_SHIFT = 2
private const val V_SHIFT = 2

object HazeRoborazziDefaults {
  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      resultValidator = createRoborazziResultValidator(),
      imageComparator = SimpleImageComparator(
        maxDistance = MAX_DISTANCE,
        hShift = H_SHIFT,
        vShift = V_SHIFT,
      ),
    ),
  )
}

internal fun createRoborazziResultValidator(
  log: (String) -> Unit = ::println,
): (ComparisonResult) -> Boolean = { result ->
  val changedPixelsPercentage = result.changedPixelsPercentage()
  log(
    "Roborazzi image diff: ${changedPixelsPercentage.formatPercentage()}% unmatched " +
      "(${result.pixelDifferences}/${result.pixelCount} pixels, " +
      "threshold ${(CHANGE_THRESHOLD * 100).formatPercentage()}%, " +
      "maxDistance ${MAX_DISTANCE.formatDecimal()}, hShift $H_SHIFT, vShift $V_SHIFT)",
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

private fun Float.formatDecimal(): String {
  val rounded = (this * 100).roundToInt()
  return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')}"
}
