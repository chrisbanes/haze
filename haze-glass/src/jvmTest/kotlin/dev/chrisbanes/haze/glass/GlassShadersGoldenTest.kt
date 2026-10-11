// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Pins every single-surface Glass shader source to its SHA-256 at
 * 14a9e2a3c65702afd50773aac712751f520a27c5, so merged variants cannot change them.
 */
class GlassShadersGoldenTest {
  @Test
  fun singleSurfaceSources_matchRecordedDigests() {
    val sources = mapOf(
      "fused" to GlassShaders.buildFused(),
      "fused-no-detail" to GlassShaders.buildFused(sharpDetail = false),
      "fused-interaction" to GlassShaders.buildFused(interactionOptics = true),
      "fused-interaction-no-detail" to GlassShaders.buildFused(interactionOptics = true, sharpDetail = false),
      "optical" to GlassShaders.buildOptical(),
      "optical-interactive" to GlassShaders.buildOptical(interactive = true),
      "detail" to GlassShaders.buildRefractionDetail(),
      "detail-coverage" to GlassShaders.buildRefractionDetail(coverageOnly = true),
      "detail-interactive" to GlassShaders.buildRefractionDetail(interactive = true),
      "detail-interactive-coverage" to GlassShaders.buildRefractionDetail(interactive = true, coverageOnly = true),
      "rim" to GlassShaders.buildRim(),
    )
    assertThat(sources.mapValues { (_, source) -> source.sha256() }).isEqualTo(RECORDED)
  }

  private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

  private companion object {
    val RECORDED = mapOf(
      "fused" to "5feff21a663ac9c6127a9c86d76ba87a7182cafe7a0feee95b9e43d85bb4db0d",
      "fused-no-detail" to "dd61bd0e5ad7375b5b26688978f4fcde2c273cab77f01102cb077d13218459e1",
      "fused-interaction" to "96799e8ad2a465540c24969ebb9e9f06eef3a74b1c449e1729eee469dcd0e526",
      "fused-interaction-no-detail" to "ffddf6bd5112d45db3733b6bb7b9f830fb2b1a4e74bd498a82073deff9d29b8e",
      "optical" to "5022d8a48ee641c9144cd8745de5708f5db8d2d920fce8cad1d69890bceebafe",
      "optical-interactive" to "a2f1460b138a8c7109d44edb697b453dfa0af7d60a5a9c4e84174e2f65a5d677",
      "detail" to "87eca539d14a7fcbade877c39fd09bc582e03e4174590a2fdb86895e02e35ca9",
      "detail-coverage" to "805de8593e6a08b24c4fab112fe7cf5e72aee17e30f3f206a9b05d5e63f57280",
      "detail-interactive" to "0ba867f6107d237f47cd90376cca948d2568f9dd7c3c173e882610825172e8dd",
      "detail-interactive-coverage" to "c966d9c248c9af77dd240abb4f6e4586be2fedcddabb2298f64c6f4e6a728ca5",
      "rim" to "a9421e44d2b009b121cd7172715e3bbe1268b4d6ad381612043f03be7390021a",
    )
  }
}
