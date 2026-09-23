// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Debug
import android.os.Trace

private const val CB10_ALLOCATION_DIAGNOSTIC_PROPERTY = "dev.chrisbanes.haze.cb10AllocationDiagnostic"

@PublishedApi
internal object Cb10GlassAllocationTrace {
  fun isEnabled(sectionName: String): Boolean =
    (sectionName == "HazeGlass.prepare" || sectionName == "HazeGlass.runtimeDraw") &&
      System.getProperty(CB10_ALLOCATION_DIAGNOSTIC_PROPERTY) == "true"

  fun record(sectionName: String, allocatedObjects: Long) {
    if (allocatedObjects < 0) return
    Trace.setCounter(
      if (sectionName == "HazeGlass.prepare") {
        "CB10GlassPrepareJavaObjectsPerCall"
      } else {
        "CB10GlassDrawJavaObjectsPerCall"
      },
      allocatedObjects,
    )
  }
}

/** Runs [block] inside a synchronous Android trace section named [sectionName]. */
@InternalHazeApi
@Suppress("DEPRECATION") // Android exposes no replacement for a same-thread Java object count.
public actual inline fun <R> trace(sectionName: String, block: () -> R): R {
  if (!Cb10GlassAllocationTrace.isEnabled(sectionName)) {
    return androidx.tracing.trace(sectionName, block)
  }
  val before = Debug.getThreadAllocCount().toLong()
  return try {
    androidx.tracing.trace(sectionName, block)
  } finally {
    Cb10GlassAllocationTrace.record(
      sectionName = sectionName,
      allocatedObjects = Debug.getThreadAllocCount().toLong() - before,
    )
  }
}

/** Runs [block] inside an asynchronous Android trace section identified by [cookie]. */
@InternalHazeApi
public actual suspend inline fun <R> traceAsync(
  sectionName: String,
  cookie: Int,
  crossinline block: suspend () -> R,
): R = androidx.tracing.traceAsync(sectionName, cookie, block)
