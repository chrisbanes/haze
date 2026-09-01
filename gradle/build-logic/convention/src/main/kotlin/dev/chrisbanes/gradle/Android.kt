// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureAndroidApplication() {
  extensions.configure<ApplicationExtension> {
    compileSdk { configureHazeCompileSdk() }

    defaultConfig {
      minSdk = Versions.MIN_SDK
      targetSdk = Versions.TARGET_SDK
    }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }

  configureAndroidComponents()
}

fun Project.configureAndroidLibrary() {
  extensions.configure<LibraryExtension> {
    compileSdk { configureHazeCompileSdk() }

    defaultConfig {
      minSdk = Versions.MIN_SDK
    }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }

  configureAndroidComponents()
}

fun Project.configureKotlinMultiplatformAndroidLibrary() {
  kotlin {
    targets.configureEach {
      if (this is KotlinMultiplatformAndroidLibraryTarget) {
        compileSdk { configureHazeCompileSdk() }
        minSdk = Versions.MIN_SDK
      }
    }
  }

  extensions.configure(KotlinMultiplatformAndroidComponentsExtension::class.java) {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
      (variantBuilder as? HasUnitTestBuilder)?.apply {
        enableUnitTest = false
      }
    }
  }
}

fun Project.configureAndroidTest() {
  extensions.configure<TestExtension> {
    compileSdk { configureHazeCompileSdk() }

    defaultConfig {
      minSdk = Versions.MIN_SDK
      targetSdk = Versions.TARGET_SDK
    }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }

  configureAndroidComponents()
}

private fun CompileSdkSpec.configureHazeCompileSdk() {
  version = release(Versions.COMPILE_SDK) {
    minorApiLevel = Versions.COMPILE_SDK_MINOR
  }
}

private fun Project.configureAndroidComponents() {
  extensions.configure(AndroidComponentsExtension::class.java) {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
      (variantBuilder as? HasUnitTestBuilder)?.apply {
        enableUnitTest = false
      }
    }
  }
}
