// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import android.content.ComponentCallbacks2
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class TrimMemoryLevelAndroidTest {
  @Test
  fun toTrimMemoryLevel_backgroundOnApi34_releasesRetainedResources() {
    assertThat(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND.toTrimMemoryLevel(sdkInt = 34))
      .isEqualTo(TrimMemoryLevel.MODERATE)
  }

  @Test
  fun toTrimMemoryLevel_backgroundBeforeApi34_staysBackground() {
    assertThat(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND.toTrimMemoryLevel(sdkInt = 33))
      .isEqualTo(TrimMemoryLevel.BACKGROUND)
  }

  @Test
  fun toTrimMemoryLevel_uiHidden_staysUiHidden() {
    assertThat(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN.toTrimMemoryLevel(sdkInt = 34))
      .isEqualTo(TrimMemoryLevel.UI_HIDDEN)
  }
}
