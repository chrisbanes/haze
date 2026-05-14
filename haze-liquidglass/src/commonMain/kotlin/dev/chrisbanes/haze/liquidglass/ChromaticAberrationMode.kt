// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

/**
 * Controls the quality and appearance of chromatic aberration (color dispersion)
 * at the refraction edges.
 */
public enum class ChromaticAberrationMode {
  /**
   * Three-sample split: red shifted forward, green centered, blue shifted backward.
   * Fast and suitable for most use cases.
   */
  Simple,

  /**
   * Seven-sample spectral decomposition with position-dependent intensity.
   * More physically realistic but significantly more expensive.
   */
  Full,
}
