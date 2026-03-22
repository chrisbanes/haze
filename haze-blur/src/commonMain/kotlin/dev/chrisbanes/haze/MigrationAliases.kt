// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import androidx.compose.runtime.ProvidableCompositionLocal
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeTint
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle

/**
 * Migration typealias. Use [HazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeBlurStyle", "dev.chrisbanes.haze.blur.HazeBlurStyle")
)
public typealias HazeStyle = HazeBlurStyle

/**
 * Migration typealias. Use [HazeTint] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeTint", "dev.chrisbanes.haze.blur.HazeTint")
)
public typealias HazeTint = dev.chrisbanes.haze.blur.HazeTint

/**
 * Migration typealias. Use [HazeProgressive] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeProgressive", "dev.chrisbanes.haze.blur.HazeProgressive")
)
public typealias HazeProgressive = dev.chrisbanes.haze.blur.HazeProgressive

/**
 * Migration helper. Use [LocalHazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("LocalHazeBlurStyle", "dev.chrisbanes.haze.blur.LocalHazeBlurStyle")
)
public val LocalHazeStyle: ProvidableCompositionLocal<HazeBlurStyle>
    get() = LocalHazeBlurStyle
