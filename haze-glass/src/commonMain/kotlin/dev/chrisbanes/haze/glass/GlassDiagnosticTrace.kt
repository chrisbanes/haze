// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

/** Emits only when the Android CB-10 diagnostic fixture opts in. */
internal expect fun recordGlassDiagnosticAppliedScale(scale: Float)
