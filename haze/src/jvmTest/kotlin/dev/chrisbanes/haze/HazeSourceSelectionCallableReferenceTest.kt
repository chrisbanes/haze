// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class HazeSourceSelectionCallableReferenceTest {

  @Test
  fun sourceSelection_whereWithEqualFunctionReferencesIsEqual() {
    val selection = HazeSourceSelection.Behind.where(::isKeyA)

    // Separate references to the same function compare equal on the JVM.
    assertThat(selection).isEqualTo(HazeSourceSelection.Behind.where(::isKeyA))
    assertThat(selection.hashCode()).isEqualTo(HazeSourceSelection.Behind.where(::isKeyA).hashCode())
    assertThat(selection).isNotEqualTo(HazeSourceSelection.Behind.where(::isKeyB))
  }
}

private fun isKeyA(metadata: HazeSourceMetadata): Boolean = metadata.key == "a"

private fun isKeyB(metadata: HazeSourceMetadata): Boolean = metadata.key == "b"
