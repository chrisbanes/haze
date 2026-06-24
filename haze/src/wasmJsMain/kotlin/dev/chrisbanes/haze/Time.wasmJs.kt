// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.js.ExperimentalWasmJsInterop

internal actual fun epochTimeMillis(): Long = dateNow().toLong()

@OptIn(ExperimentalWasmJsInterop::class)
private fun dateNow(): Double = js("Date.now()")
