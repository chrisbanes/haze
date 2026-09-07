// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderScriptScrollRegressionTest {
  @Test
  fun imagesList_survivesRepeatedScrollAndRelaunch() {
    assumeTrue(Build.VERSION.SDK_INT == 30)
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    repeat(5) {
      device.executeShellCommand("am force-stop dev.chrisbanes.haze.sample.android")
      device.executeShellCommand(
        "am start -W -n dev.chrisbanes.haze.sample.android/.MainActivity " +
          "--ez $FORCE_BLUR_EXTRA true",
      )
      device.navigateToImagesList()
      device.repeatedScrolls("lazy_column", repetitions = 12)
      device.waitForIdle()
      device.waitForObject(By.res("lazy_column"))
    }
  }
}
