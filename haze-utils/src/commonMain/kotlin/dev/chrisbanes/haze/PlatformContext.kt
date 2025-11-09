// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

@InternalHazeApi
public expect abstract class PlatformContext

@InternalHazeApi
public expect fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext
