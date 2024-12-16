// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal const val LOG_ENABLED = true

internal expect fun log(tag: String, message: () -> String)
