// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Pins the standalone shader sources after sharing the rectangle evaluators with merged optics.
 * Pixel and profile regressions cover their rendering behavior.
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
      "fused" to "daa2760f4d9da8695e2bc0f6b2adb0ca0470af4c76bb3edd903e915e6648a359",
      "fused-no-detail" to "a2caa552cc269dbbe54d1f9822cbd639d64dae4fdfa12007a84625b7ed2e3643",
      "fused-interaction" to "fc93d21908ade9b2a19a29f88a6f0e721551a4f571041f67dddd916181c4bfa2",
      "fused-interaction-no-detail" to "b9c9337dc5164c646a900f402236584c880528d835e411cf82ad178d1f62092a",
      "optical" to "09b4cd75128bcda4e93a1f25b4080860a600f0461d4586955af3e646c844ebfd",
      "optical-interactive" to "ebcc26c4572bf144e98abe3897ac1b233bf92b0b4dab21df77e6c8f72bb8d24f",
      "detail" to "c608bf4734727c99432fb016ad5be44c23099060d2e50748d73d78faadcca091",
      "detail-coverage" to "4d835b4c6039dff868a024c3bccc9edb4ac28135830b757dac690e942b1fb950",
      "detail-interactive" to "16052b1e27e9494c1d7c3596074682e5f16cc4a1fc743151e804d8dedebd4972",
      "detail-interactive-coverage" to "03d2bde609e92cf53438759b90a99ecfa4597df91610be182e81c7a77d0b9d35",
      "rim" to "a9421e44d2b009b121cd7172715e3bbe1268b4d6ad381612043f03be7390021a",
    )
  }
}
