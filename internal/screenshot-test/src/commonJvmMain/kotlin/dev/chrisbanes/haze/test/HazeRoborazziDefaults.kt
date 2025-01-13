// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions

object HazeRoborazziDefaults {
  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      changeThreshold = 0.01f,
      imageComparator = SimpleImageComparator(maxDistance = 0.075f, hShift = 1, vShift = 1),
    ),
  )
}

expect val HazeRoborazziDefaults.outputDirectoryName: String
