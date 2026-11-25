// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import java.util.concurrent.LinkedBlockingQueue
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Records each requested URI and fails the load without opening a connection, keeping
 * [ExoPlayerSampleTest] off the network.
 */
@Implements(DefaultHttpDataSource::class)
class ShadowDefaultHttpDataSource {
  @Implementation
  fun open(dataSpec: DataSpec): Long {
    requests.add(dataSpec.uri)
    throw HttpDataSourceException(
      dataSpec,
      PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
      HttpDataSourceException.TYPE_OPEN,
    )
  }

  companion object {
    val requests = LinkedBlockingQueue<Uri>()
  }
}
