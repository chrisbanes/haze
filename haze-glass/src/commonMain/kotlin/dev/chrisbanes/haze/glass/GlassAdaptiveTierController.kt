// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import kotlin.math.sqrt

/** A host-level frame sample. The controller does not interpret missing reports as missed frames. */
internal data class GlassFrameHealthSample(
  val sequence: Long,
  val timestampNanos: Long,
  val durationNanos: Long,
  val budgetNanos: Long,
  val isValid: Boolean = true,
  val allowsUpwardProbe: Boolean = true,
)

/** The three renderer scales shared by timing-driven and workload-driven Adaptive policies. */
internal enum class GlassAdaptiveTier(val scale: Float) {
  FULL_RESOLUTION(1f),
  BALANCED(sqrt(0.5f)),
  AGGRESSIVE(0.5f),
}

/** Pure hysteresis policy. Callers own host sampling, visibility, and fallback selection. */
internal class GlassAdaptiveTierController {
  var tier: GlassAdaptiveTier = GlassAdaptiveTier.BALANCED
    private set

  private var phase = Phase.WARMING_UP
  private var phaseAfterSettling = Phase.MONITORING
  private var phaseStartedAtNanos: Long? = null
  private var phaseSamples = 0
  private var missedFrames = 0
  private var previousSequence: Long? = null
  private var previousTimestampNanos: Long? = null
  private var retryAfterNanos = 0L
  private var failedProbes = 0

  fun observe(sample: GlassFrameHealthSample, nowNanos: Long): GlassAdaptiveTier {
    val priorTimestamp = previousTimestampNanos
    val priorSequence = previousSequence
    val outOfOrder = priorTimestamp != null && sample.timestampNanos <= priorTimestamp
    val sequenceLost = priorSequence != null && sample.sequence != priorSequence + 1
    val stale =
      sample.timestampNanos > nowNanos || nowNanos - sample.timestampNanos > MAX_SAMPLE_AGE_NANOS
    val suspended = priorTimestamp != null && sample.timestampNanos - priorTimestamp > SUSPENSION_NANOS

    previousSequence = sample.sequence
    previousTimestampNanos = sample.timestampNanos

    if (
      outOfOrder || sequenceLost || stale || suspended || !sample.isValid ||
      sample.durationNanos <= 0L || sample.budgetNanos <= 0L
    ) {
      resetEvidence(Phase.WARMING_UP)
      return tier
    }

    if (phaseStartedAtNanos == null) phaseStartedAtNanos = sample.timestampNanos

    if (phase == Phase.SETTLING) {
      val settlingStartedAt = phaseStartedAtNanos ?: sample.timestampNanos
      if (sample.timestampNanos - settlingStartedAt < SETTLING_NANOS) return tier
      resetEvidence(phaseAfterSettling)
      phaseAfterSettling = Phase.MONITORING
      phaseStartedAtNanos = sample.timestampNanos
      return tier
    }

    if (
      phase != Phase.WARMING_UP &&
      sample.timestampNanos <= (phaseStartedAtNanos ?: Long.MIN_VALUE)
    ) {
      return tier
    }

    if (phase == Phase.WARMING_UP) {
      phaseSamples++
      if (
        phaseSamples >= WARM_UP_MIN_SAMPLES &&
        sample.timestampNanos - (phaseStartedAtNanos ?: sample.timestampNanos) >= WARM_UP_NANOS
      ) {
        resetEvidence(Phase.MONITORING)
        phaseStartedAtNanos = sample.timestampNanos
      }
      return tier
    }

    phaseSamples++
    if (sample.durationNanos > sample.budgetNanos) missedFrames++
    val elapsed = sample.timestampNanos - (phaseStartedAtNanos ?: sample.timestampNanos)
    val missRatio = missedFrames.toFloat() / phaseSamples

    when (phase) {
      Phase.WARMING_UP, Phase.SETTLING -> Unit
      Phase.MONITORING -> {
        if (
          phaseSamples >= DOWNGRADE_MIN_SAMPLES && elapsed >= DOWNGRADE_WINDOW_NANOS &&
          missRatio >= BAD_FRAME_RATIO
        ) {
          changeTier(tier.oneStepDown(), sample.timestampNanos)
        } else if (
          tier != GlassAdaptiveTier.FULL_RESOLUTION && phaseSamples >= PROBE_MIN_SAMPLES &&
          elapsed >= PROBE_STABLE_WINDOW_NANOS && missRatio <= HEALTHY_FRAME_RATIO &&
          sample.timestampNanos >= retryAfterNanos && sample.allowsUpwardProbe
        ) {
          changeTier(tier.oneStepUp(), sample.timestampNanos, Phase.PROBING)
        }
      }
      Phase.PROBING -> {
        if (phaseSamples >= PROBE_VALIDATION_MIN_SAMPLES && elapsed >= PROBE_WINDOW_NANOS) {
          if (missRatio >= BAD_FRAME_RATIO) {
            changeTier(tier.oneStepDown(), sample.timestampNanos)
            failedProbes++
            val backoffMultiplier = 1L shl (failedProbes - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
            retryAfterNanos = sample.timestampNanos + (PROBE_RETRY_NANOS * backoffMultiplier)
          } else {
            failedProbes = 0
            resetEvidence(Phase.MONITORING)
            phaseStartedAtNanos = sample.timestampNanos
          }
        }
      }
    }
    if (phase == Phase.MONITORING && phaseSamples >= MAX_EVIDENCE_SAMPLES) {
      // Bound the ratio's history without losing the stable-duration clock. At high refresh
      // rates, 300 samples can arrive before the three-second upward-probe window completes.
      phaseSamples /= 2
      missedFrames /= 2
    }
    return tier
  }

  fun reset() {
    tier = GlassAdaptiveTier.BALANCED
    phase = Phase.WARMING_UP
    phaseAfterSettling = Phase.MONITORING
    phaseStartedAtNanos = null
    phaseSamples = 0
    missedFrames = 0
    previousSequence = null
    previousTimestampNanos = null
    retryAfterNanos = 0L
    failedProbes = 0
  }

  /** Forget timing across idle or background gaps without changing the visible tier. */
  fun suspendEvidence() {
    resetEvidence(Phase.WARMING_UP)
    previousSequence = null
    previousTimestampNanos = null
  }

  private fun changeTier(
    newTier: GlassAdaptiveTier,
    timestampNanos: Long,
    afterSettling: Phase = Phase.MONITORING,
  ) {
    if (tier != newTier) {
      tier = newTier
      phaseAfterSettling = afterSettling
      resetEvidence(Phase.SETTLING)
      phaseStartedAtNanos = timestampNanos
    }
  }

  private fun resetEvidence(newPhase: Phase) {
    phase = newPhase
    phaseStartedAtNanos = null
    phaseSamples = 0
    missedFrames = 0
  }

  private fun GlassAdaptiveTier.oneStepDown(): GlassAdaptiveTier = when (this) {
    GlassAdaptiveTier.FULL_RESOLUTION -> GlassAdaptiveTier.BALANCED
    GlassAdaptiveTier.BALANCED -> GlassAdaptiveTier.AGGRESSIVE
    GlassAdaptiveTier.AGGRESSIVE -> GlassAdaptiveTier.AGGRESSIVE
  }

  private fun GlassAdaptiveTier.oneStepUp(): GlassAdaptiveTier = when (this) {
    GlassAdaptiveTier.FULL_RESOLUTION -> GlassAdaptiveTier.FULL_RESOLUTION
    GlassAdaptiveTier.BALANCED -> GlassAdaptiveTier.FULL_RESOLUTION
    GlassAdaptiveTier.AGGRESSIVE -> GlassAdaptiveTier.BALANCED
  }

  private enum class Phase { WARMING_UP, SETTLING, MONITORING, PROBING }

  private companion object {
    // Starting policy values; calibrate against paired platform benchmarks before changing them.
    const val BAD_FRAME_RATIO = 0.2f
    const val HEALTHY_FRAME_RATIO = 0.05f
    const val WARM_UP_MIN_SAMPLES = 30
    const val WARM_UP_NANOS = 500_000_000L
    const val SETTLING_NANOS = 500_000_000L
    const val DOWNGRADE_MIN_SAMPLES = 30
    const val DOWNGRADE_WINDOW_NANOS = 1_000_000_000L
    const val PROBE_MIN_SAMPLES = 90
    const val PROBE_VALIDATION_MIN_SAMPLES = 20
    const val MAX_EVIDENCE_SAMPLES = 300
    const val PROBE_STABLE_WINDOW_NANOS = 3_000_000_000L
    const val PROBE_WINDOW_NANOS = 500_000_000L
    const val PROBE_RETRY_NANOS = 2_000_000_000L
    const val MAX_BACKOFF_SHIFT = 4
    const val MAX_SAMPLE_AGE_NANOS = 250_000_000L
    const val SUSPENSION_NANOS = 1_000_000_000L
  }
}
