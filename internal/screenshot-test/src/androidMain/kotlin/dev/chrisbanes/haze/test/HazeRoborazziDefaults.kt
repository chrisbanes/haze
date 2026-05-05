// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions

object HazeRoborazziDefaults {
  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      changeThreshold = 0.01f, // 1%
      imageComparator = SimpleImageComparator(maxDistance = 0.01f, hShift = 2, vShift = 2),
    ),
  )

  val relaxedRoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      changeThreshold = 0.03f, // 3%
      imageComparator = SimpleImageComparator(maxDistance = 0.03f, hShift = 2, vShift = 2),
    ),
  )
}
