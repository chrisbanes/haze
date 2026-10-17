// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import android.content.Intent
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.sample.SampleEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class BenchmarkLaunchRequestTest {
  @Test
  fun intent_withValidBenchmarkExtras_resolvesSelectionAndOptions() {
    val request = Intent()
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE", "blur-profiling")
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT", "blur")
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SCENARIO", "stable_quality")
      .putExtra("dev.chrisbanes.haze.sample.android.FORCE_BLUR", true)
      .toBenchmarkLaunchRequest()

    assertThat(request.initialSelection?.sample?.route).isEqualTo("blur-profiling")
    assertThat(request.initialSelection?.effect).isEqualTo(SampleEffect.Blur)
    assertThat(request.initialProfilingScenarioId).isEqualTo("stable_quality")
    assertThat(request.forceBlur).isTrue()
  }

  @Test
  fun intent_withoutOptionalExtras_usesNormalLaunchDefaults() {
    val request = Intent().toBenchmarkLaunchRequest()

    assertThat(request.initialSelection).isNull()
    assertThat(request.initialProfilingScenarioId).isNull()
    assertThat(request.forceBlur).isFalse()
  }

  @Test
  fun intent_withUnknownScenario_preservesItForTheProfilingSampleToReject() {
    val request = Intent()
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE", "blur-profiling")
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT", "blur")
      .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SCENARIO", "unknown_scenario")
      .toBenchmarkLaunchRequest()

    assertThat(request.initialProfilingScenarioId).isEqualTo("unknown_scenario")
  }

  @Test
  fun intent_withRouteButNoEffect_failsBeforeRendering() {
    assertFailure {
      Intent()
        .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE", "blur-profiling")
        .toBenchmarkLaunchRequest()
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun intent_withUnknownRoute_failsBeforeRendering() {
    assertFailure {
      Intent()
        .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE", "missing-sample")
        .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT", "blur")
        .toBenchmarkLaunchRequest()
    }.isInstanceOf<IllegalStateException>()
  }

  @Test
  fun intent_withUnknownEffect_failsBeforeRendering() {
    assertFailure {
      Intent()
        .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE", "blur-profiling")
        .putExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT", "glow")
        .toBenchmarkLaunchRequest()
    }.isInstanceOf<IllegalStateException>()
  }
}
