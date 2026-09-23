// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazeEffectLifecycleScope

/** Reads the appearance supplied by the current platform host during the observed node update. */
internal expect fun readPlatformGlassSystemAppearance(scope: HazeEffectLifecycleScope): GlassSystemAppearance
