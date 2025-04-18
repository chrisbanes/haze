// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build

/**
 * By default we only enable blurring on SDK Level 31 and above, at the moment. You can override
 * this via [HazeEffectScope.blurEnabled] if required.
 */
internal actual fun isBlurEnabledByDefault(): Boolean = Build.VERSION.SDK_INT >= 31
