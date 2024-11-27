// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import coil3.PlatformContext
import okio.Path

actual fun PlatformContext.cacheDirPath(): Path? = null
