// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.blur

import android.content.Context
import android.renderscript.RenderScript

/**
 * One [RenderScript] context shared by every RenderScript Blur node.
 *
 * Creating a context is expensive and happens on the main thread as a node first draws, so nodes
 * take a reference and the context lives until the last reference is released. Each node still owns
 * its own allocations and script.
 */
internal object SharedRenderScript {
  private val lock = Any()
  private var renderScript: RenderScript? = null
  private var references = 0

  /** Takes a reference, creating the context if no node holds one. Pair with [release]. */
  fun acquire(context: Context): RenderScript = synchronized(lock) {
    val rs = renderScript ?: RenderScript.create(context.applicationContext).also { renderScript = it }
    references++
    rs
  }

  /** Drops a reference taken with [acquire], destroying the context when it was the last. */
  fun release() = synchronized(lock) {
    check(references > 0) { "No RenderScript reference to release" }
    if (--references == 0) {
      val rs = renderScript
      renderScript = null
      runCatching { rs?.destroy() }
    }
  }

  internal val referenceCountForTest: Int get() = synchronized(lock) { references }
  internal val currentForTest: RenderScript? get() = synchronized(lock) { renderScript }
}
