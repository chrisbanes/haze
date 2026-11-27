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
 * merged sources, whose field skips far members (#1766). The fused and optical sources were
 * re-recorded when their Fresnel term clamped its dot product (#1916).
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

  @Test
  fun disjointContinuousSources_matchPreOverlapDigests() {
    val sources = mapOf(
      "fused" to GlassShaders.buildFused(continuous = true),
      "fused-merged" to GlassShaders.buildFused(merged = true, continuous = true),
      "fused-merged-circular-optics" to GlassShaders.buildFusedWithCircularOptics(merged = true),
      "optical-merged" to GlassShaders.buildOptical(merged = true, continuous = true),
      "rim-merged" to GlassShaders.buildRim(merged = true, continuous = true),
      "merged-background" to GlassShaders.buildMergedBackground(continuous = true),
    )
    // Recorded from cac1ddea5, before overlapping corners changed the continuous programs. The fused
    // and optical sources were re-recorded when their Fresnel term clamped its dot product (#1916).
    assertThat(sources.mapValues { (_, source) -> source.sha256() }).isEqualTo(
      mapOf(
        "fused" to "a9776aa7fd021d3f757b274a9cdf0def06293fe512c499438b0cb44e2ab43587",
        "fused-merged" to "d043ed0113c0e841fed1ada342ed6b31debe19f4eb1a5f7411ec2bf6c6c56cac",
        "fused-merged-circular-optics" to "d4e9b05ad255bc7dea6e453ce87f7e8679c0773042dd7e43c80f85746a5e8ab8",
        "optical-merged" to "47c93aaeda450cc805a1b1bffde02628e103417daccdcb54d4fd964e425b01be",
        "rim-merged" to "149e1f487deb93098b2fa02dd4865b5858bda31dd83678154c4a7819eb20c2f5",
        "merged-background" to "2e0d41190f67ca6edfdcae389fd0a591f4c7ae487d03c9eb3aa1bb9b1096083d",
      ),
    )
  }

  private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

  private companion object {
    // Recorded from the circular sources of main, before continuous corners were added. The rim
    // sources were re-recorded when the rim highlight and edge outline were added, the fused and
    // optical sources when the flat interior lighting gradient was skipped (#1766), and the merged
    // sources when the field began to skip far members (#1766). The rim sources were re-recorded
    // when they gained node-bound edge coverage (#1798), and the standalone rim was re-recorded
    // when its straight outlines gained centered outer-edge coverage. Both rim sources were
    // re-recorded when the outline gained topAndBottomStrength (#1755). The fused and optical sources
    // were re-recorded when their Fresnel term clamped its dot product (#1916).
    val ADDITIONAL_RECORDED = mapOf(
      "fused-merged" to "e49200ea52330a1f1ddb502e918f2f5031e7cf707b8fd1ec207d4bf2783005d7",
      "fused-merged-no-detail" to "4cb50af7436e5a6a16d2b81293dd5bae2140262ecc41f465e979c1232a861672",
      "optical-merged" to "fda1424e43135ce8b97ff334c6b885f4e1385b782148af851ea940df3908b528",
      "detail-merged" to "cfb63dc5bbe3775548bab65eb082dc2f3b2eee3e3e254503e135a006f43a6c44",
      "detail-merged-coverage" to "df2ab0fda95fe2d1e0347d97941959237d6918d7b3a12de9c939f22d7962bf02",
      "rim-merged" to "13a6ae7c129589ac14af9ef17599881c7c21c4cca9c87c40439b061d88de0174",
      "lighting" to "d956b1c8f406d634b73f8d3db694422582afe99427ae71a354073dcb06e53e24",
      "lighting-merged" to "fb4070698512ad824e538a4b657eb5661e58e853f959f10ee397d83e5e8a06ec",
      "merged-background" to "b55ffc3933f7106cf7cdcc47a50cfd41dc07d2c86e207baa979b0e28255d8ee5",
    )

    val RECORDED = mapOf(
      "fused" to "cc7345e430be7c14027d8568c6f4efb3571c036490901a993f7911df16044da8",
      "fused-no-detail" to "ea3dbbdc883a302013f8e8a607c017c93903ee18e5fd40a59e78f8879f911431",
      "fused-interaction" to "f61dc1327ec7c7d67c3a502b09778b8c51f538a0e05109127138a4675401f6f9",
      "fused-interaction-no-detail" to "914fd95338c6cc12a1f37908e8a5fcd263d9d6502a52080cdb5ebe53d244d0a5",
      "optical" to "4c821b24b92176adbd4f28013815809eaa26b4807b57042b6d0734718e2810d0",
      "optical-interactive" to "ae55d635792f7f8463f05d5631e22cb7d515552382992fce03f1d1f733c30507",
      "detail" to "c608bf4734727c99432fb016ad5be44c23099060d2e50748d73d78faadcca091",
      "detail-coverage" to "4d835b4c6039dff868a024c3bccc9edb4ac28135830b757dac690e942b1fb950",
      "detail-interactive" to "16052b1e27e9494c1d7c3596074682e5f16cc4a1fc743151e804d8dedebd4972",
      "detail-interactive-coverage" to "03d2bde609e92cf53438759b90a99ecfa4597df91610be182e81c7a77d0b9d35",
      "rim" to "758691b6ead4918e9c7b5fb24f483aadff0e9c73dfa2d5a5a18804adcfbbe623",
    )
  }
}
