// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class SampleEmbedTest : ContextTest() {
  @Test
  fun resolver_withoutEmbedFlag_startsTheNormalApp() {
    assertThat(resolveSampleEmbed(emptyMap(), CommonSamples)).isEqualTo(SampleEmbedRequest.Normal)
  }

  @Test
  fun resolver_validSelection_selectsTheRequestedSampleEffectAndTheme() {
    assertThat(
      resolveSampleEmbed(
        mapOf("embed" to "true", "sample" to "scaffold", "effect" to "glass", "theme" to "dark"),
        CommonSamples,
      ),
    ).isEqualTo(
      SampleEmbedRequest.Embedded(Sample.Scaffold, SampleEffect.Glass, SampleEmbedTheme.Dark),
    )
  }

  @Test
  fun resolver_acceptsEverySupportedCatalogPair() {
    CommonSamples.forEach { sample ->
      sample.effects.forEach { effect ->
        assertThat(
          resolveSampleEmbed(
            mapOf("embed" to "true", "sample" to sample.route, "effect" to effect.name.lowercase()),
            CommonSamples,
          ),
        ).isEqualTo(SampleEmbedRequest.Embedded(sample, effect, SampleEmbedTheme.System))
      }
    }
  }

  @Test
  fun resolver_rejectsMissingUnknownAndUnsupportedSelections() {
    val blurOnly = Sample(route = "blur", title = "Blur") { _, _ -> }
    listOf(
      mapOf("embed" to "true"),
      mapOf("embed" to "true", "sample" to "missing", "effect" to "blur"),
      mapOf("embed" to "true", "sample" to "blur", "effect" to "missing"),
      mapOf("embed" to "true", "sample" to "blur", "effect" to "glass"),
      mapOf("embed" to "false", "sample" to "blur", "effect" to "blur"),
    ).forEach { query ->
      assertThat(resolveSampleEmbed(query, listOf(blurOnly))).isInstanceOf<SampleEmbedRequest.Invalid>()
    }
  }

  @Test
  fun resolver_usesSystemThemeByDefaultAndAcceptsEveryTheme() {
    val query = mapOf("embed" to "true", "sample" to "scaffold", "effect" to "blur")
    assertThat(resolveSampleEmbed(query, CommonSamples))
      .isEqualTo(SampleEmbedRequest.Embedded(Sample.Scaffold, SampleEffect.Blur, SampleEmbedTheme.System))

    mapOf("system" to SampleEmbedTheme.System, "light" to SampleEmbedTheme.Light, "dark" to SampleEmbedTheme.Dark)
      .forEach { (theme, expected) ->
        assertThat(resolveSampleEmbed(query + ("theme" to theme), CommonSamples))
          .isEqualTo(SampleEmbedRequest.Embedded(Sample.Scaffold, SampleEffect.Blur, expected))
      }

    assertThat(resolveSampleEmbed(query + ("theme" to "blue"), CommonSamples))
      .isInstanceOf<SampleEmbedRequest.Invalid>()
  }
}
