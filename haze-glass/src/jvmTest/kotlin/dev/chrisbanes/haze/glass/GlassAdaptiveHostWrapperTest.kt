// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassAdaptiveHostWrapperTest {

  @Test
  fun wrapperSharesTokenWithinRootAndDisposesItWithRoot() = runComposeUiTest {
    val visible = mutableStateOf(true)
    var first: GlassAdaptiveHostToken? = null
    var second: GlassAdaptiveHostToken? = null

    setContent {
      if (visible.value) {
        GlassAdaptiveHost {
          first = LocalGlassAdaptiveHostToken.current
          second = LocalGlassAdaptiveHostToken.current
        }
      }
    }
    waitForIdle()

    val token = checkNotNull(first)
    assertThat(second).isSameInstanceAs(token)
    assertThat(token.isDisposed).isFalse()

    visible.value = false
    waitForIdle()
    assertThat(token.isDisposed).isTrue()
  }
}
