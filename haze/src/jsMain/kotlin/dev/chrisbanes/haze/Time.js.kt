// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.js.Date

internal actual fun epochTimeMillis(): Long = Date.now().toLong()
