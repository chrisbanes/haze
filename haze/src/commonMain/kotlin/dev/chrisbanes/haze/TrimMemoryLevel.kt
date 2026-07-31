// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/**
 * Relative severity of a request to release cached rendering resources.
 *
 * @property severity Comparable severity used to order increasingly urgent requests.
 */
public enum class TrimMemoryLevel(public val severity: Int) {
  /** The UI is no longer visible, allowing UI-only cached resources to be released. */
  UI_HIDDEN(severity = 10),

  /** The application is in the background or equivalent memory pressure has been reported. */
  BACKGROUND(severity = 20),

  /** Moderate memory pressure warrants releasing reusable rendering resources. */
  MODERATE(severity = 40),

  /** Critical memory pressure warrants releasing every disposable rendering resource. */
  COMPLETE(severity = 80),
}
