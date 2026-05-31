// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun Project.configureAndroid() {
  android {
    compileSdk = Versions.COMPILE_SDK

    defaultConfig {
      minSdk = Versions.MIN_SDK
    }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }

  androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
      (variantBuilder as? HasUnitTestBuilder)?.apply {
        enableUnitTest = false
      }
    }
  }
}

@Suppress("UNCHECKED_CAST")
private fun Project.android(action: com.android.build.api.dsl.LibraryExtension.() -> Unit) {
  val ext = extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension
  ext.action()
}

private fun Project.androidComponents(action: AndroidComponentsExtension<*, *, *>.() -> Unit) {
  extensions.configure(AndroidComponentsExtension::class.java, action)
}
