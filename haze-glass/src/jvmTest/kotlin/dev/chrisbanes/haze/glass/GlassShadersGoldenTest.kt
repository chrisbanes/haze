// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Pins the circular shader sources, which must stay identical to those before continuous corners,
 * apart from the rim sources, which gained the rim highlight and edge outline afterwards, and the
 * fused and optical sources, which skip the flat interior lighting gradient (#1766).
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
    // sources were re-recorded when the rim highlight and edge outline were added, and the fused
    // and optical sources when the flat interior lighting gradient was skipped (#1766).
    val ADDITIONAL_RECORDED = mapOf(
      "fused-merged" to "c47d0bb94393486f3e3b177004e8ad14a07cca8c8a01f85ec37bd78730a22d5f",
      "fused-merged-no-detail" to "8e2f6af88c35beaa743aa56f62e30f2e61b93af7900f0b8bde12c8009f347601",
      "optical-merged" to "218068a00872d1c825d84084d0ec77188468a4e9325304c1e3d31e01e82aaa6e",
      "detail-merged" to "21f315ab287af09897300a94ff5866e8a3a9c61fe45b1ac20ab623989f701d60",
      "detail-merged-coverage" to "dd5b0f4913bdded15398addd1b083026a3fb45e8afb15b24f759d17e9604143b",
      "rim-merged" to "ea0eda097197102c35f7a84c1c33c511613adae5db6cc92b08b3b613c9b12e90",
      "lighting" to "d956b1c8f406d634b73f8d3db694422582afe99427ae71a354073dcb06e53e24",
      "lighting-merged" to "d1320d53329be3a08f838438c6d663bf0d0cfcb463ee76149bafbf8f723e750b",
      "merged-background" to "541da6d9a6b17f07e4b97cf3018d196acf23963184848a9eca213d2732325221",
    )

    val RECORDED = mapOf(
      "fused" to "bdf71f6f93e48bd77b03a4bc38bb5dbb4af7e743c3e5667ff8b1694b3c8c7fbc",
      "fused-no-detail" to "7bb8f78e7db3e8dbea98b6f31ee77801459f2d794673b100c217111f6b03815d",
      "fused-interaction" to "9ef44b4ee400e8b77b02cbbca7ff66f1a67d32f642b7eb83f9038bf6ea1ef981",
      "fused-interaction-no-detail" to "e6d78484d294cdf7da1743add8a60a3c23ff6c444c45661773cf3c293293f6e3",
      "optical" to "60d3da6d53a34abd0a71cd2d30376437e9ca3abdb0c7975aedef3d45da3cb431",
      "optical-interactive" to "c65e5f2a413abf52ff2d20495cbe54d6735704c50420395838c2dd58c7a8b52d",
      "detail" to "c608bf4734727c99432fb016ad5be44c23099060d2e50748d73d78faadcca091",
      "detail-coverage" to "4d835b4c6039dff868a024c3bccc9edb4ac28135830b757dac690e942b1fb950",
      "detail-interactive" to "16052b1e27e9494c1d7c3596074682e5f16cc4a1fc743151e804d8dedebd4972",
      "detail-interactive-coverage" to "03d2bde609e92cf53438759b90a99ecfa4597df91610be182e81c7a77d0b9d35",
      "rim" to "fbbbc7106f46b7cc5d00ad4e6ea679ac2a90c8dc7028037eae88b4fdf3ace395",
    )
  }
}
