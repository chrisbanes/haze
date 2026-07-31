// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/** Runs [block] inside a Compose trace section named [sectionName]. */
@InternalHazeApi
public actual inline fun <R> trace(sectionName: String, block: () -> R): R {
  return androidx.compose.ui.util.trace(sectionName, block)
}

/** Runs [block]; Skiko does not currently emit an asynchronous platform trace section. */
@InternalHazeApi
public actual suspend inline fun <R> traceAsync(
  sectionName: String,
  cookie: Int,
  crossinline block: suspend () -> R,
): R = block()
