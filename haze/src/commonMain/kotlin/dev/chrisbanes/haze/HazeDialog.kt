// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Deprecated(
  "HazeDialog is no longer necessary, you can use Dialog directly",
  ReplaceWith("Dialog(onDismissRequest, properties, content)", "androidx.compose.ui.window.Dialog"),
)
@Composable
public fun HazeDialog(
  hazeState: HazeState,
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = properties,
    content = content,
  )
}
