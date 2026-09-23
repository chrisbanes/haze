// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider

/** Returns a Window only when it owns the attached view's actual root. */
internal fun verifiedGlassWindow(view: View): Window? {
  if (view.windowToken == null) return null
  var ancestor: View? = view
  while (ancestor != null) {
    if (ancestor is DialogWindowProvider) {
      return ancestor.window.takeIf { it.decorView === view.rootView }
    }
    ancestor = ancestor.parent as? View
  }

  var context = view.context
  while (context is ContextWrapper) {
    if (context is Activity) {
      return context.window?.takeIf { it.decorView === view.rootView }
    }
    context = context.baseContext
  }
  return null
}
