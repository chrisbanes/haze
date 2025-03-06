# Strip all HazeLogger calls. They shouldn't be used in release builds
-assumenosideeffects class dev.chrisbanes.haze.HazeLogger { *; }
