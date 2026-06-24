// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "sample.js"
      }
    }

    binaries.executable()
  }

  js {
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

    jsMain {
      dependencies {
        // Coil fix
        implementation(npm("node-polyfill-webpack-plugin", "^4.0.0"))
        implementation(npm("os-browserify", "^0.3.0"))
        implementation(npm("path-browserify", "^1.0.1"))
      }
    }

    named("wasmJsMain") {
      dependencies {
        // Coil fix
        implementation(npm("node-polyfill-webpack-plugin", "^4.0.0"))
        implementation(npm("os-browserify", "^0.3.0"))
        implementation(npm("path-browserify", "^1.0.1"))
      }
    }
  }
}
