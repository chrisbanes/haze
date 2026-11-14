// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType

class AndroidManagedDevicesConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("dev.chrisbanes.android.library")

    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
      kotlin {
        targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
          withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            @Suppress("UnstableApiUsage")
            managedDevices {
              localDevices {
                for (api in 30..34) {
                  val pixel = if (api == 30) 5 else 6
                  create("pixel${pixel}Api$api") {
                    device = "Pixel $pixel"
                    sdkVersion = api
                    // PixelCopy and shader tests require rendering; ATD images disable it.
                    systemImageSource = "aosp"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
