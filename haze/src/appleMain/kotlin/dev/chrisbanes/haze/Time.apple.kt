// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun epochTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
