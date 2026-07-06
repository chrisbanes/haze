// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.ImageComparator.ComparisonResult
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import kotlin.math.roundToInt

private const val UNMATCHED_PIXEL_THRESHOLD = 0.008f
private const val MAX_DISTANCE = 0.02f
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
  val changedPixelsRatio = result.changedPixelsRatio()
  val valid = changedPixelsRatio <= UNMATCHED_PIXEL_THRESHOLD
  if (!valid) {
    log(
      "Roborazzi image diff: ${changedPixelsRatio.formatAsPercentage()}% unmatched " +
        "(${result.pixelDifferences}/${result.pixelCount} pixels, " +
        "threshold ${UNMATCHED_PIXEL_THRESHOLD.formatAsPercentage()}%, " +
        "maxDistance ${MAX_DISTANCE.formatTwoDecimals()}, hShift $H_SHIFT, vShift $V_SHIFT)",
    )
  }

  valid
}

private fun ComparisonResult.changedPixelsRatio(): Float {
  if (pixelCount == 0) return 0f
  return pixelDifferences.toFloat() / pixelCount
}

private fun Float.formatAsPercentage(): String = (this * 100).formatTwoDecimals()

private fun Float.formatTwoDecimals(): String {
  val rounded = (this * 100).roundToInt()
  return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')}"
}
