// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.sample.CreditCardSample

@Composable
@Preview
fun PreviewCardSample() {
  CreditCardSample(rememberNavController())
}
