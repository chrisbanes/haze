// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build

/**
 * By default we only enable blurring on SDK Level 31 and above, at the moment. You can override
 * this via [BlurVisualEffect.blurEnabled] if required.
 */
internal actual fun isBlurEnabledByDefault(): Boolean = Build.VERSION.SDK_INT >= 31
