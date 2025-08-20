// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope

expect interface ScreenshotTest

expect fun TestScope.runScreenshotTest(block: ScreenshotUiTest.() -> Unit)

interface ScreenshotUiTest {
  fun setContent(content: @Composable () -> Unit)
  fun captureRoot(nameSuffix: String? = null)
  fun waitForIdle()
}

internal fun TestCase.generateFilename(suffix: String? = null): String = buildString {
  val spec = spec::class.simpleName
  if (!spec.isNullOrEmpty()) {
    append(spec)
    append('.')
  }
  append(name.name)
  if (!suffix.isNullOrEmpty()) {
    append('_')
    append(suffix)
  }
  append(".png")
}
