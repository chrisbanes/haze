// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal actual fun epochTimeMillis(): Long = dateNow().toLong()

private fun dateNow(): Double = js("Date.now()")
