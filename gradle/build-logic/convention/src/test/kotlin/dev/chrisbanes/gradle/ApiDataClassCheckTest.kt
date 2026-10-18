// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ApiDataClassCheckTest {
  @Test
  fun findComponentClasses_returnsNestedClassName() {
    val signature = """
      package a.b {
        public static final class Outer.Inner {
          method public int component1();
        }
      }
    """.trimIndent()

    assertThat(findComponentClasses(signature)).containsOnly("a.b.Outer.Inner")
  }

  @Test
  fun findComponentClasses_stripsTypeParametersAndCollapsesKotlinOnlyDuplicates() {
    val signature = """
      package a.b {
        @androidx.compose.runtime.Stable public final class Point<T> implements a.b.Base {
          method @KotlinOnly public operator X component1();
          method public T component2();
        }
      }
    """.trimIndent()

    assertThat(findComponentClasses(signature)).containsOnly("a.b.Point")
  }

  @Test
  fun findComponentClasses_qualifiesEachClassWithItsOwnPackage() {
    val signature = """
      package a {
        public final class One {
          method public int component1();
        }
      }
      package b {
        @kotlin.jvm.JvmInline public final value class Two {
          method public int component1();
        }
      }
    """.trimIndent()

    assertThat(findComponentClasses(signature)).containsOnly("a.One", "b.Two")
  }

  @Test
  fun findComponentClasses_ignoresLookalikeMethods() {
    val signature = """
      package a {
        public final class One {
          method public int componentCount();
          method public int component1(int);
        }
      }
    """.trimIndent()

    assertThat(findComponentClasses(signature)).isEmpty()
  }

  @Test
  fun allowlist_leavesOnlyUnlistedClasses() {
    val signature = """
      package dev.chrisbanes.haze {
        public static final class HazeInput.Sources {
          method public int component1();
        }
        public final class Unlisted {
          method public int component1();
        }
      }
    """.trimIndent()

    assertThat(findComponentClasses(signature) - API_DATA_CLASS_ALLOWLIST)
      .containsOnly("dev.chrisbanes.haze.Unlisted")
  }

  @Test
  fun findClasses_reportsDeclaredClassesWithoutComponents() {
    // A converted allowlist entry stays declared but loses componentN(), which is how the check finds stale entries.
    val signature = """
      package dev.chrisbanes.haze {
        public static final class HazeInput.Sources {
          method public dev.chrisbanes.haze.HazeInput.Sources copy();
        }
        public final class Point {
          method public int component1();
        }
      }
    """.trimIndent()

    assertThat(findClasses(signature)).isEqualTo(
      mapOf("dev.chrisbanes.haze.HazeInput.Sources" to false, "dev.chrisbanes.haze.Point" to true),
    )
  }

  @Test
  fun staleAllowlistEntries_reportsDeclaredEntryWithoutComponents() {
    val classes = mapOf("a.Converted" to false, "a.Kept" to true)

    assertThat(staleAllowlistEntries(classes, setOf("a.Converted", "a.Kept"))).containsExactly("a.Converted")
  }

  @Test
  fun staleAllowlistEntries_ignoresEntryFromAnotherModule() {
    val classes = mapOf("a.Kept" to true)

    assertThat(staleAllowlistEntries(classes, setOf("a.Kept", "b.Elsewhere"))).isEmpty()
  }
}
