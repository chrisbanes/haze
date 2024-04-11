// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  `kotlin-dsl`
  alias(libs.plugins.spotless)
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint()
    licenseHeaderFile(rootProject.file("../../spotless/copyright.txt"))
  }

  kotlinGradle {
    target("*.kts")
    ktlint()
    licenseHeaderFile(rootProject.file("../../spotless/copyright.txt"), "(^(?![\\/ ]\\**).*$)")
  }
}

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.spotless.gradlePlugin)
  compileOnly(libs.compose.gradlePlugin)
  compileOnly(libs.metalava.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("kotlinMultiplatform") {
      id = "dev.chrisbanes.kotlin.multiplatform"
      implementationClass = "dev.chrisbanes.gradle.KotlinMultiplatformConventionPlugin"
    }

    register("root") {
      id = "dev.chrisbanes.root"
      implementationClass = "dev.chrisbanes.gradle.RootConventionPlugin"
    }

    register("kotlinAndroid") {
      id = "dev.chrisbanes.kotlin.android"
      implementationClass = "dev.chrisbanes.gradle.KotlinAndroidConventionPlugin"
    }

    register("androidApplication") {
      id = "dev.chrisbanes.android.application"
      implementationClass = "dev.chrisbanes.gradle.AndroidApplicationConventionPlugin"
    }

    register("androidLibrary") {
      id = "dev.chrisbanes.android.library"
      implementationClass = "dev.chrisbanes.gradle.AndroidLibraryConventionPlugin"
    }

    register("androidTest") {
      id = "dev.chrisbanes.android.test"
      implementationClass = "dev.chrisbanes.gradle.AndroidTestConventionPlugin"
    }

    register("compose") {
      id = "dev.chrisbanes.compose"
      implementationClass = "dev.chrisbanes.gradle.ComposeMultiplatformConventionPlugin"
    }

    register("metalava") {
      id = "dev.chrisbanes.metalava"
      implementationClass = "dev.chrisbanes.gradle.MetalavaConventionPlugin"
    }
  }
}
