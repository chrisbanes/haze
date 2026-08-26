// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun Project.configureAndroid() {
  android {
    compileSdk = Versions.COMPILE_SDK
    defaultConfig.minSdk = Versions.MIN_SDK
    compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    compileOptions.targetCompatibility = JavaVersion.VERSION_11
  }

  extensions.findByType(ApplicationExtension::class.java)?.defaultConfig?.targetSdk = Versions.TARGET_SDK
  extensions.findByType(LibraryExtension::class.java)?.testOptions?.targetSdk = Versions.TARGET_SDK
  extensions.findByType(TestExtension::class.java)?.defaultConfig?.targetSdk = Versions.TARGET_SDK

  androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
      (variantBuilder as? HasUnitTestBuilder)?.apply {
        enableUnitTest = false
      }
    }
  }
}

private fun Project.android(action: CommonExtension.() -> Unit) = extensions.configure(CommonExtension::class.java, action)

private fun Project.androidComponents(action: AndroidComponentsExtension<*, *, *>.() -> Unit) {
  extensions.configure(AndroidComponentsExtension::class.java, action)
}
