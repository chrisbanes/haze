// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

@RequiresOptIn(
  message = "This is an internal Haze API and should not be used outside of the library. " +
    "It may be changed or removed without notice.",
)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalHazeApi
