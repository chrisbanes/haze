# T5 acceptance rule, fixed before candidate measurements were read

Recorded 2026-11-12 before any T5 candidate result was inspected.

- Candidate: commit 1c0bdb323 (skip the flat interior lighting gradient for circular Glass).
  Baseline: fd85578d9 (frozen T4 APKs, SHA-256 ec906484... / 21f96888...; frozen jvm classes).
- Android (fused path, Pixel 6): primary per-iteration value is that iteration's P50
  `frameOverrunMs` (end-to-end frame deadline, not GPU time; T2 found no attributable
  `gpu_slice`). P90 is reported alongside. Rows: `sourceUpdateContainer4` (primary),
  `sourceUpdateContainer2`, `sourceUpdateBalanced`; control `sourceUpdateContainer4Continuous`
  (source unchanged by the candidate).
- macOS (split path, M1 Max Metal): primary per-launch value is GPU busy ms per frame over the
  5 s window. Scenes `s4m-circular` (primary), `s2m-circular`, `s1-circular`, `s2i-circular`;
  control `s4m-continuous`. Three launches each, alternating B C / C B / B C.
- Improvement on a row: candidate median beats baseline median by more than the larger of the two
  IQRs, in both Android passes (forward and reverse separately), or across the three alternating
  macOS rounds.
- Land only if the primary row improves on both affected paths (Android `sourceUpdateContainer4`
  in both passes and macOS `s4m-circular`), no affected row regresses by more than that margin,
  and the CPU placement of the compared Android iterations is comparable (otherwise repeat once,
  then inconclusive). Otherwise revert `GlassShaders.kt` and the golden digests to base.
