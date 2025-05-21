// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  listOf(
    macosX64(),
    macosArm64()
  ).forEach { macosTarget ->
    macosTarget.binaries.executable {
      entryPoint = "dev.chrisbanes.haze.sample.main"
      freeCompilerArgs += listOf(
        "-linker-option", "-framework", "-linker-option", "Metal"
      )
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
    targets(
      kotlin.targets.getByName("macosArm64"),
      kotlin.targets.getByName("macosX64")
    )
  }
}
