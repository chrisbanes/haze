// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import org.jetbrains.skiko.KotlinBackend
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.jetbrains.skiko.kotlinBackend

internal actual val avoidProgressiveBlurLoopBreak: Boolean =
  kotlinBackend == KotlinBackend.JVM && hostOs == OS.Windows
