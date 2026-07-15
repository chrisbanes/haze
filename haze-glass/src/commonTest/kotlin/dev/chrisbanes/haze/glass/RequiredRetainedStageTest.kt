// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class RequiredRetainedStageTest {

  @Test
  fun requireRetainedStage_missingStageInvalidatesRetainedOutput() {
    var retainedOutputAvailable = true

    val result = requireRetainedStage(value = null as String?) {
      retainedOutputAvailable = false
    }

    assertThat(result).isNull()
    assertThat(retainedOutputAvailable).isFalse()
  }

  @Test
  fun requireDrawableMaterialSize_zeroWidthInvalidatesRetainedOutput() {
    var retainedOutputAvailable = true

    val result = requireDrawableMaterialSize(Size(width = 0f, height = 10f)) {
      retainedOutputAvailable = false
    }

    assertThat(result).isNull()
    assertThat(retainedOutputAvailable).isFalse()
  }

  @Test
  fun requireDrawableMaterialSize_zeroHeightInvalidatesRetainedOutput() {
    var retainedOutputAvailable = true

    val result = requireDrawableMaterialSize(Size(width = 10f, height = 0f)) {
      retainedOutputAvailable = false
    }

    assertThat(result).isNull()
    assertThat(retainedOutputAvailable).isFalse()
  }

  @Test
  fun requireDrawableMaterialSize_nonFiniteDimensionInvalidatesRetainedOutput() {
    listOf(
      Size(width = Float.NaN, height = 10f),
      Size(width = 10f, height = Float.NaN),
      Size(width = Float.POSITIVE_INFINITY, height = 10f),
      Size(width = 10f, height = Float.NEGATIVE_INFINITY),
    ).forEach { size ->
      var retainedOutputAvailable = true

      val result = requireDrawableMaterialSize(size) {
        retainedOutputAvailable = false
      }

      assertThat(result).isNull()
      assertThat(retainedOutputAvailable).isFalse()
    }
  }

  @Test
  fun requireDrawableMaterialSize_negativeDimensionInvalidatesRetainedOutput() {
    listOf(Size(width = -1f, height = 10f), Size(width = 10f, height = -1f)).forEach { size ->
      var retainedOutputAvailable = true

      val result = requireDrawableMaterialSize(size) {
        retainedOutputAvailable = false
      }

      assertThat(result).isNull()
      assertThat(retainedOutputAvailable).isFalse()
    }
  }

  @Test
  fun requireDrawableMaterialSize_positiveDimensionsRemainDrawable() {
    var retainedOutputAvailable = true
    listOf(Size(width = 0.5f, height = 10f), Size(width = 10f, height = 0.5f)).forEach { size ->
      val result = requireDrawableMaterialSize(size) {
        retainedOutputAvailable = false
      }

      assertThat(result).isEqualTo(size)
    }

    assertThat(retainedOutputAvailable).isTrue()
  }
}
