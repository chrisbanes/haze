# Glass direct rim drawing — 2026-09-08

The rim shader is procedural and does not sample its content input. Drawing it as a
retained shader brush avoids an offscreen RenderEffect while preserving the existing foreground
layer, alpha, coordinates and light response. Android software canvases and optional brush failures
keep the original RenderEffect path.

Skiko uses the same direct drawing path, retaining a RuntimeShaderBuilder and creating a shader
snapshot when the rim's uniforms change. The expected benefit is less intermediate rendering and
memory traffic; Skiko performance has not been measured. The Android numbers below do not establish
the size of any Skiko improvement.

## Device and method

- Pixel 6, Android 37.1, 1080 × 2400.
- Original source: `7d1dafdd60abe28261a47470cf1c06f41ed51966`.
- Release benchmark APK, `CompilationMode.Full`, warm startup, eight timing iterations per workload.
- Fixed 60 Hz and Android fixed-performance mode. Original device settings restored after each run.
- Existing Gallery content, styles and journeys preserved. The source-update Adaptive scenario is
  a diagnostic control.
- Battery was 100%. Original run temperature: 25.5–28.9°C; final run: 31.1–33.0°C.
  Thermal status was 0 in each run's before/after checks.

## Results

CPU frame time is `frameDurationCpuMs` P90. GPU memory is the median reported by
`memoryGpuMaxKb`.

| Workload | Original CPU P90 | Direct rim CPU P90 | Original GPU memory | Direct rim GPU memory |
| --- | ---: | ---: | ---: | ---: |
| Playground timeline | 12.3265 ms | 11.2398 ms | 81,188 KB | 73,838 KB |
| Product pager | 7.9573 ms | 7.6141 ms | 113,916 KB | 101,840 KB |
| Source-update Adaptive | 4.0291 ms | 4.1228 ms | 100,150 KB | 91,296 KB |

Playground's observed CPU-P90 reduction is 8.8%, with 9.1% lower GPU memory. Product's observed
CPU-P90 reduction is 4.3%, with 10.6% lower GPU memory. A later original Product control measured
7.7915 ms; an earlier direct-rim screening measured 7.7425 ms and 106,398 KB. Product's small
timing difference should be treated cautiously; its memory benefit appeared in both candidate runs.

In the first Playground traces, actual-frame P90 decreased from 16.252 to 15.251 ms. Mean
`QueueSubmit` duration decreased from 1.746 to 1.423 ms, and mean `flush layers` duration from
2.874 to 2.424 ms. These nested CPU slices support reduced driver work; they are not direct GPU
execution measurements. Product's first-trace actual-frame P90 was 9.370 ms versus 9.229 ms, so
its small CPU-P90 difference does not establish an end-to-end latency improvement.

No final reversed-order Playground control was run. These measurements describe the observed
runs and do not guarantee a percentage improvement. They were collected before the subsequent
cleanup of exception wrapping; the successful hardware drawing path is unchanged by that cleanup.

## Validation and evidence

The measured implementation passed 26 Android lifecycle tests, 31 Android pixel-invariant tests,
31 Skiko integration tests and two Gallery screenshot tests. Existing screenshot baselines were
unchanged. Lifecycle coverage includes retained brush reuse, light-position changes, release and
recreation. A fresh read-only review found no correctness or contract issue.

The subsequent `spotlessApply check --no-scan` run failed on the existing Desktop Product portrait
and landscape screenshot baselines. Both failures reproduced with the original production sources
from `7d1dafdd`; their actual and comparison images were byte-for-byte identical to the candidate's.
Those unrelated baselines were not regenerated in this change.

For the Skiko extension, 31 runtime integration tests and two focused rim tests passed. The latter
compare direct and image-filter pixels across geometry, colour and lighting changes (within one
8-bit channel step), and verify that later uniform updates preserve earlier shader snapshots.
Integration assertions cover direct-layer use, provider reuse and release.

The extended implementation passes `:haze-glass:check` and compilation for JVM, JS, Wasm, iOS and
macOS. Six of eight Desktop Gallery screenshots pass; Product portrait and landscape still fail
their existing baselines. Compared with the saved original captures, direct Skiko drawing changes
about 1.2% of pixels, concentrated around the rims (mean absolute channel difference below
0.02/255; maximum 72/255). Visual inspection shows smoother edge sampling. Removing the intermediate
image means transformed rims are not pixel-identical, despite matching at the tested native size.
The existing screenshot baselines remain unchanged.

Local evidence is retained under the ignored `internal/benchmark/build/glass-p90/` directory:
`baseline-b`, `baseline-product-c`, `direct-rim-product-a`, `direct-rim-final-a`, and
`screening-results.json`. Raw traces and APKs are not checked into the repository.

- Original APK SHA-256: `6334cb1ae65b20963c16594ddf0c7eab960d0420e3dcd3a21536f30f501d08d4`.
- Measured direct-rim APK SHA-256: `3afe4d41456ad3962861daf9fdba9d52565e789c5957c33f474eab713068dc55`.
