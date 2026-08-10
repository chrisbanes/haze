// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  macosArm64 {
    binaries.executable {
      entryPoint = "dev.chrisbanes.haze.sample.main"
    }
  }

  sourceSets {
    macosMain {
      dependencies {
        implementation(projects.sample.shared)
      }
    }
  }
}

compose.desktop {
  nativeApplication {
    targets(kotlin.macosArm64())

    distributions {
      packageName = "HazeSample"
      packageVersion = "1.0.0"
    }
  }
}
