// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun HazeDialog(
  hazeState: HazeState,
  onDismissRequest: () -> Unit,
  properties: DialogProperties,
  content: @Composable () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = properties,
    content = content,
  )
}
