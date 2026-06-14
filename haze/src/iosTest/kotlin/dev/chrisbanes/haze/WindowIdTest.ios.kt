// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import platform.UIKit.UIView

class WindowIdTest {

  @Test
  fun uikitWindowId_usesHostViewIdentity() {
    val view1 = UIView()
    val view2 = UIView()

    assertThat(uikitWindowId(view1)).isSameInstanceAs(view1)
    assertThat(uikitWindowId(view2)).isSameInstanceAs(view2)
  }
}
