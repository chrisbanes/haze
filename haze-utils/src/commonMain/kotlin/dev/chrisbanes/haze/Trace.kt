// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/** Runs [block] inside a synchronous platform trace section named [sectionName]. */
@InternalHazeApi
public expect inline fun <R> trace(sectionName: String, block: () -> R): R

/** Runs [block] inside an asynchronous platform trace section identified by [cookie]. */
@InternalHazeApi
public expect suspend inline fun <R> traceAsync(
  sectionName: String,
  cookie: Int,
  crossinline block: suspend () -> R,
): R
