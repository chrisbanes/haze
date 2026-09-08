// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class GlassRimFailureTest {
  @Test
  fun runtimeException_isAvailableForFallback() {
    val failure = IllegalArgumentException("Unsupported rim")
    assertThat(runCatchingGlassRim { throw failure }.exceptionOrNull()).isSameInstanceAs(failure)
  }

  @Test
  fun error_propagatesInsteadOfFallingBack() {
    val failure = Error("Fatal rim failure")
    assertFailure { runCatchingGlassRim { throw failure } }.isSameInstanceAs(failure)
  }

  @Test
  fun nonRuntimeException_propagatesInsteadOfFallingBack() {
    val failure = Exception("Unexpected rim failure")
    assertFailure { runCatchingGlassRim { throw failure } }.isSameInstanceAs(failure)
  }
}
