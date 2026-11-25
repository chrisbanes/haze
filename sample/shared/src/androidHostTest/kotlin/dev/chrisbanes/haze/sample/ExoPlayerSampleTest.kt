// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.prop
import dev.chrisbanes.haze.test.ContextTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import org.robolectric.annotation.Config

// Keeps the test off the network without changing the sample.
@Config(
  shadows = [ShadowDefaultHttpDataSource::class],
  instrumentedPackages = ["androidx.media3.datasource"],
)
@OptIn(ExperimentalTestApi::class)
class ExoPlayerSampleTest : ContextTest() {
  @Test
  fun exoPlayerSample_replacesAndReleasesPlayersWithTheViewLifecycle() = runComposeUiTest {
    val showSample = mutableStateOf(true)
    val contextOverride = mutableStateOf<Context?>(null)
    var rootView: View? = null
    val releases = mutableListOf<ExoPlayer>()
    ShadowDefaultHttpDataSource.requests.clear()

    fun playerView(view: View?): PlayerView? = when (view) {
      is PlayerView -> view
      is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { playerView(view.getChildAt(it)) }
      else -> null
    }

    fun attachedPlayer(): ExoPlayer? = playerView(rootView)?.player as? ExoPlayer

    setContent {
      rootView = LocalView.current
      val base = LocalContext.current
      CompositionLocalProvider(LocalContext provides (contextOverride.value ?: base)) {
        if (showSample.value) {
          ExoPlayerSample(effect = SampleEffect.Blur, onBack = {})
        }
      }
    }
    waitForIdle()

    val first = checkNotNull(attachedPlayer())
    first.addAnalyticsListener(
      object : AnalyticsListener {
        override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
          releases += first
        }
      },
    )
    assertThat(first.mediaItemCount).isEqualTo(1)
    // The load reached the intercepted data source, so no connection was opened.
    assertThat(ShadowDefaultHttpDataSource.requests.poll(10, TimeUnit.SECONDS))
      .isNotNull()
      .prop("host") { it.host }
      .isEqualTo("commondatastorage.googleapis.com")

    // A different Context instance yields a new player through the sample's remember key.
    contextOverride.value = ContextWrapper(rootView!!.context)
    waitForIdle()

    val second = checkNotNull(attachedPlayer())
    assertThat(second).isNotSameInstanceAs(first)
    assertThat(releases).isEqualTo(listOf(first))
    assertThat(second.mediaItemCount).isEqualTo(1)
    second.addAnalyticsListener(
      object : AnalyticsListener {
        override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
          releases += second
        }
      },
    )

    val view = checkNotNull(playerView(rootView))
    showSample.value = false
    waitForIdle()

    assertThat(view.player).isNull()
    assertThat(releases).isEqualTo(listOf(first, second))
  }
}
