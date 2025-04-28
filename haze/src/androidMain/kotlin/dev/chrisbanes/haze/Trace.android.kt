// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

actual inline fun <R> trace(sectionName: String, block: () -> R): R {
  return androidx.tracing.trace(sectionName, block)
}

actual suspend inline fun <R> traceAsync(
  sectionName: String,
  cookie: Int,
  crossinline block: suspend () -> R,
): R = androidx.tracing.traceAsync(sectionName, cookie, block)
