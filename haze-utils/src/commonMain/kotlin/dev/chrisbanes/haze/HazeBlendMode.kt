// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.BlendMode

/**
 * Use Compose [BlendMode] directly.
 */
@Deprecated(
  message = "Use Compose BlendMode directly.",
  replaceWith = ReplaceWith(
    expression = "BlendMode",
    imports = ["androidx.compose.ui.graphics.BlendMode"],
  ),
)
@InternalHazeApi
public typealias HazeBlendMode = BlendMode
