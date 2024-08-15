// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal actual fun log(tag: String, message: () -> String) {
  if (LOG_ENABLED) {
    println("[$tag] ${message()}")
  }
}
