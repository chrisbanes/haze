// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/** Generates value-based `equals`, `hashCode`, and `toString` implementations for the class. */
@InternalHazeApi
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class Poko
