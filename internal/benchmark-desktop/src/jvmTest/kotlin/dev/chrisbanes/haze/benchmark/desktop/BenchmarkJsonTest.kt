// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test

class BenchmarkJsonTest {
  @Test
  fun blockRoundTrips() {
    val block = benchmarkBlockFixture()
    assertThat(
      BenchmarkJson.decodeFromString<BenchmarkBlockResult>(
        BenchmarkJson.encodeToString(block),
      ),
    ).isEqualTo(block)
  }

  @Test
  fun artifactOverFiveMiB_isRejected() {
    val oversized = artifactFixture(diagnostic = "").copy(
      diagnostic = null,
      headSha = "b".repeat(5 * 1024 * 1024),
    )
    assertFailure { encodeArtifact(oversized) }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun artifactDiagnosticOver2048Bytes_isRejected() {
    val oversized = artifactFixture(diagnostic = "x".repeat(2049))
    assertFailure { encodeArtifact(oversized) }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun diagnosticOver2048Bytes_isTruncatedAtUtf8Boundary() {
    assertThat(boundedDiagnostic("é".repeat(2048)).encodeToByteArray().size)
      .isLessThanOrEqualTo(2048)
  }
}
