// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Pins the circular shader sources, which must stay identical to those before continuous corners,
 * apart from the rim sources, which gained the rim highlight afterwards.
 * Pixel and profile regressions cover their rendering behavior.
 */
class GlassShadersGoldenTest {
  @Test
  fun singleSurfaceSources_matchRecordedDigests() {
    val sources = mapOf(
      "fused" to GlassShaders.buildFused(continuous = false),
      "fused-no-detail" to GlassShaders.buildFused(sharpDetail = false, continuous = false),
      "fused-interaction" to GlassShaders.buildFused(interactionOptics = true, continuous = false),
      "fused-interaction-no-detail" to GlassShaders.buildFused(interactionOptics = true, sharpDetail = false, continuous = false),
      "optical" to GlassShaders.buildOptical(continuous = false),
      "optical-interactive" to GlassShaders.buildOptical(interactive = true, continuous = false),
      "detail" to GlassShaders.buildRefractionDetail(continuous = false),
      "detail-coverage" to GlassShaders.buildRefractionDetail(coverageOnly = true, continuous = false),
      "detail-interactive" to GlassShaders.buildRefractionDetail(interactive = true, continuous = false),
      "detail-interactive-coverage" to GlassShaders.buildRefractionDetail(interactive = true, coverageOnly = true, continuous = false),
      "rim" to GlassShaders.buildRim(continuous = false),
    )
    assertThat(sources.mapValues { (_, source) -> source.sha256() }).isEqualTo(RECORDED)
  }

  @Test
  fun additionalSources_matchRecordedDigests() {
    val sources = mapOf(
      "fused-merged" to GlassShaders.buildFused(merged = true, continuous = false),
      "fused-merged-no-detail" to GlassShaders.buildFused(sharpDetail = false, merged = true, continuous = false),
      "optical-merged" to GlassShaders.buildOptical(merged = true, continuous = false),
      "detail-merged" to GlassShaders.buildRefractionDetail(merged = true, continuous = false),
      "detail-merged-coverage" to GlassShaders.buildRefractionDetail(coverageOnly = true, merged = true, continuous = false),
      "rim-merged" to GlassShaders.buildRim(merged = true, continuous = false),
      "lighting" to GlassShaders.buildInteractionLighting(continuous = false),
      "lighting-merged" to GlassShaders.buildInteractionLighting(merged = true, continuous = false),
      "merged-background" to GlassShaders.buildMergedBackground(continuous = false),
    )
    assertThat(sources.mapValues { (_, source) -> source.sha256() }).isEqualTo(ADDITIONAL_RECORDED)
  }

  private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

  private companion object {
    // Recorded from the circular sources of main, before continuous corners were added. The rim
    // sources were re-recorded when the rim highlight was added.
    val ADDITIONAL_RECORDED = mapOf(
      "fused-merged" to "242cdc692d980cff473a2e089370a302bd8575d3ae0e59de9aef725f8424e5ed",
      "fused-merged-no-detail" to "4fd26f10692853d21bc133453785ff04b6447bd3780b83d6f73aa2fc0a894dc6",
      "optical-merged" to "5f9e5024fc247622700378821355046d30a5da534a48261fcb5e382b945f836f",
      "detail-merged" to "21f315ab287af09897300a94ff5866e8a3a9c61fe45b1ac20ab623989f701d60",
      "detail-merged-coverage" to "dd5b0f4913bdded15398addd1b083026a3fb45e8afb15b24f759d17e9604143b",
      "rim-merged" to "b5d1fac3f1f311c02b40291120fc9003a51265b56e0651b32a4b37d098407920",
      "lighting" to "d956b1c8f406d634b73f8d3db694422582afe99427ae71a354073dcb06e53e24",
      "lighting-merged" to "d1320d53329be3a08f838438c6d663bf0d0cfcb463ee76149bafbf8f723e750b",
      "merged-background" to "541da6d9a6b17f07e4b97cf3018d196acf23963184848a9eca213d2732325221",
    )

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
      "rim" to "78f5adb261ed6ccf79beba3bba0c3afab286db6342b1a0626a4ba6be6df8a1d2",
    )
  }
}
