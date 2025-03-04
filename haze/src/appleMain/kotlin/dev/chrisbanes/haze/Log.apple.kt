// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import platform.Foundation.NSLog

internal actual inline fun platformLog(tag: String, message: String) {
  NSLog("[%s] %s", tag, message)
}
