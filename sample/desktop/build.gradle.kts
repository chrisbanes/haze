// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  jvm()

  sourceSets {
    jvmMain {
      dependencies {
        implementation(projects.sample.shared)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "dev.chrisbanes.haze.sample.desktop.MainKt"
  }
}

// Forward -Phaze.embed, -Phaze.sample, -Phaze.effect and -Phaze.theme to the app, which then opens
// that sample in a 1280x720 window. For example, to record the Glass container showcase:
// ./gradlew :sample:desktop:run -Phaze.embed=true -Phaze.sample=glass-container-showcase -Phaze.effect=glass
tasks.withType<JavaExec>().configureEach {
  listOf("embed", "sample", "effect", "theme").forEach { name ->
    providers.gradleProperty("haze.$name").orNull?.let { systemProperty("haze.$name", it) }
  }
}
