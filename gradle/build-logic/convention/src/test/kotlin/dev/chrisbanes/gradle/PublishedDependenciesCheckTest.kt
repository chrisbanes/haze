// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class PublishedDependenciesCheckTest {
  @Test
  fun publishedDependencies_listsSortedVariantDependencies() {
    val metadata = """
      {"variants": [
        {"name": "apiElements", "dependencies": [{"group": "g", "module": "a", "version": {"requires": "1"}}]},
        {"name": "runtimeElements", "dependencies": [
          {"group": "g", "module": "z", "version": {"requires": "1"}},
          {"group": "g", "module": "b", "version": {"requires": "1"}}
        ]},
        {"name": "sourcesElements", "files": []}
      ]}
    """.trimIndent()

    assertThat(publishedDependencies(metadata)).containsExactly(
      "apiElements g:a",
      "runtimeElements g:b",
      "runtimeElements g:z",
    )
  }

  @Test
  fun publishedDependencies_ignoresVersions() {
    fun metadata(version: String) = """
      {"variants": [{"name": "v", "dependencies": [{"group": "g", "module": "a", "version": {"requires": "$version"}}]}]}
    """.trimIndent()

    assertThat(publishedDependencies(metadata("1"))).containsExactly(*publishedDependencies(metadata("2")).toTypedArray())
  }
}
