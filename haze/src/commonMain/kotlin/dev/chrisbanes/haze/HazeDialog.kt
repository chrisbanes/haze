// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * Opens a dialog with the given content.
 *
 * @param hazeState The [HazeState] which will be used in [content].
 * @param onDismissRequest Executes when the user tries to dismiss the dialog.
 * @param properties [DialogProperties] for further customization of this dialog's behavior.
 * @param content The content to be displayed inside the dialog.
 */
@Composable
expect fun HazeDialog(
  hazeState: HazeState,
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
)
