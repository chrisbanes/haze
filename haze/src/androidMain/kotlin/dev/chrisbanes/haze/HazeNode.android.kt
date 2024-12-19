// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build

/**
 * By default we only enable blurring on SDK Level 32 and above, at the moment. You can override
 * this via [HazeEffectScope.blurEnabled] if required.
 *
 * See https://github.com/chrisbanes/haze/issues/77 for more details.
 */
internal actual fun isBlurEnabledByDefault(): Boolean = Build.VERSION.SDK_INT >= 32
