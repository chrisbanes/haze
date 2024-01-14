// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

@Stable
expect fun Modifier.testTagsAsResourceId(enable: Boolean): Modifier
