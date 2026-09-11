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
    assertThat(resolveSampleLaunch(emptyMap(), CommonSamples)).isEqualTo(SampleLaunchRequest.Normal)
    assertThat(resolveSampleLaunch(mapOf("embed" to "false"), CommonSamples))
      .isEqualTo(SampleLaunchRequest.Normal)
  }

  @Test
  fun resolver_selectionWithoutEmbedding_retainsNavigationAndTheme() {
    val selection = mapOf("sample" to "glass-product", "effect" to "glass", "theme" to "dark")
    listOf(selection, selection + ("embed" to "false")).forEach { query ->
      assertThat(resolveSampleLaunch(query, CommonSamples)).isEqualTo(
        SampleLaunchRequest.Selected(Sample.GlassProduct, SampleEffect.Glass, SampleEmbedTheme.Dark, embedded = false),
      )
    }
  }

  @Test
  fun resolver_invalidNavigationSelection_showsAnError() {
    listOf(
      mapOf("sample" to "glass-product"),
      mapOf("effect" to "glass"),
      mapOf("sample" to "missing", "effect" to "glass"),
      mapOf("sample" to "glass-product", "effect" to "blur"),
      mapOf("sample" to "glass-product", "effect" to "glass", "theme" to "blue"),
    ).forEach { query ->
      listOf(query, query + ("embed" to "false")).forEach {
        assertThat(resolveSampleLaunch(it, CommonSamples)).isInstanceOf<SampleLaunchRequest.Invalid>()
      }
    }
  }

  @Test
  fun resolver_validSelection_selectsTheRequestedSampleEffectAndTheme() {
    assertThat(
      resolveSampleLaunch(
        mapOf("embed" to "true", "sample" to "scaffold", "effect" to "glass", "theme" to "dark"),
        CommonSamples,
      ),
    ).isEqualTo(
      SampleLaunchRequest.Selected(Sample.Scaffold, SampleEffect.Glass, SampleEmbedTheme.Dark),
    )
  }

  @Test
  fun resolver_acceptsEverySupportedCatalogPair() {
    CommonSamples.forEach { sample ->
      sample.effects.forEach { effect ->
        assertThat(
          resolveSampleLaunch(
            mapOf("embed" to "true", "sample" to sample.route, "effect" to effect.name.lowercase()),
            CommonSamples,
          ),
        ).isEqualTo(SampleLaunchRequest.Selected(sample, effect, SampleEmbedTheme.System))
      }
    }
  }

  @Test
  fun resolver_rejectsMissingUnknownAndUnsupportedSelections() {
    val blurOnly = Sample(route = "blur", title = "Blur") { _, _ -> }
    listOf(
      mapOf("embed" to "true"),
      mapOf("embed" to "true", "sample" to "blur"),
      mapOf("embed" to "true", "sample" to "missing", "effect" to "blur"),
      mapOf("embed" to "true", "sample" to "blur", "effect" to "missing"),
      mapOf("embed" to "true", "sample" to "blur", "effect" to "glass"),
      mapOf("embed" to "invalid", "sample" to "blur", "effect" to "blur"),
    ).forEach { query ->
      assertThat(resolveSampleLaunch(query, listOf(blurOnly))).isInstanceOf<SampleLaunchRequest.Invalid>()
    }
  }

  @Test
  fun resolver_usesSystemThemeByDefaultAndAcceptsEveryTheme() {
    val query = mapOf("embed" to "true", "sample" to "scaffold", "effect" to "blur")
    assertThat(resolveSampleLaunch(query, CommonSamples))
      .isEqualTo(SampleLaunchRequest.Selected(Sample.Scaffold, SampleEffect.Blur, SampleEmbedTheme.System))

    mapOf("system" to SampleEmbedTheme.System, "light" to SampleEmbedTheme.Light, "dark" to SampleEmbedTheme.Dark)
      .forEach { (theme, expected) ->
        assertThat(resolveSampleLaunch(query + ("theme" to theme), CommonSamples))
          .isEqualTo(SampleLaunchRequest.Selected(Sample.Scaffold, SampleEffect.Blur, expected))
      }

    assertThat(resolveSampleLaunch(query + ("theme" to "blue"), CommonSamples))
      .isInstanceOf<SampleLaunchRequest.Invalid>()
  }
}
