// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

compose {
  experimental {
    web.application {}
  }
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "sample.js"
      }
    }

    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.sample.shared)
      }
    }
  }
}
