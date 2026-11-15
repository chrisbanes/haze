// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Pins the circular shader sources, which must stay identical to those before continuous corners,
 * apart from the rim sources, which gained the rim highlight and edge outline afterwards, the
 * fused and optical sources, which skip the flat interior lighting gradient (#1766), and the
 * merged sources, whose field skips far members (#1766).
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
    // sources were re-recorded when the rim highlight and edge outline were added, the fused and
    // optical sources when the flat interior lighting gradient was skipped (#1766), and the merged
    // sources when the field began to skip far members (#1766).
    val ADDITIONAL_RECORDED = mapOf(
      "fused-merged" to "954a4f172ef97b5c3ad9c3f043afe0c81b8caf83fe051d5675f77c7e7496f715",
      "fused-merged-no-detail" to "739f78671a686db9f801da7e97f88dd737ae664d149da8f982411e55bec946e6",
      "optical-merged" to "a40dd9d5a3bbcf108f164d0063edcec26fd0be0668066a78b2b47fd88d344cff",
      "detail-merged" to "cfb63dc5bbe3775548bab65eb082dc2f3b2eee3e3e254503e135a006f43a6c44",
      "detail-merged-coverage" to "df2ab0fda95fe2d1e0347d97941959237d6918d7b3a12de9c939f22d7962bf02",
      "rim-merged" to "546522a52bc0949946aa1c2c1162131fc986349ef438b1446305f20c43ffe7fc",
      "lighting" to "d956b1c8f406d634b73f8d3db694422582afe99427ae71a354073dcb06e53e24",
      "lighting-merged" to "fb4070698512ad824e538a4b657eb5661e58e853f959f10ee397d83e5e8a06ec",
      "merged-background" to "b55ffc3933f7106cf7cdcc47a50cfd41dc07d2c86e207baa979b0e28255d8ee5",
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
