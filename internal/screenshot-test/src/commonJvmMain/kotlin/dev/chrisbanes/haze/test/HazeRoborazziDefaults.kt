package dev.chrisbanes.haze.test

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions

@OptIn(ExperimentalRoborazziApi::class)
object HazeRoborazziDefaults {
  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      changeThreshold = 0.01f,
      imageComparator = SimpleImageComparator(hShift = 1, vShift = 1),
    ),
  )
}

expect val HazeRoborazziDefaults.outputDirectoryName: String
