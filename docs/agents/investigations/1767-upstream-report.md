# Draft: Direct3D first draw spends seconds in WARP shader compilation

Not filed. Proposed starting point: Skiko, because the attached source reproduces through its native
SkiaLayer API without Compose or Haze. The traces also identify Microsoft backend compiler work;
ownership and a suitable compiler-level input still need upstream triage.

## Symptom

A RuntimeEffect compiles to a Skia shader successfully, then its first native Direct3D draw stalls.
The original continuous-corner shader does not complete that draw within a 30-second capture.
A feature-reduced shader completes after seconds, while later draws take milliseconds. Bypassing
its refraction displacement removes the cold signal. This reproduces on the Windows software
adapter; no claim is made about physical GPUs or other backends.

## Reproduction

Use the [standalone source project](1767-repro/README.md) or the `windows-glass-repro.zip` artifact
from the investigation workflow, with Java 21, PowerShell 7 and a visible Windows x64 desktop:

```powershell
./gradlew.bat installDist --no-scan
pwsh -File run.ps1
```

The ZIP's native qualification is pending at this draft revision. Do not file this draft as a
validated standalone reproduction until that run has completed successfully.

The application pins Skiko 0.150.2 and Kotlin 2.4.21, forces Direct3D and renders a 64×48 rectangle
using the frozen SKSL and explicit uniforms. There is one procedural child shader, two merged
members and no Compose/Haze runtime. Each trial creates a fresh JVM/context. The paired control
has identical inputs with only refraction displacement bypassed. The script preserves six warm
samples and rejects blank output, wrong backend, process failures and mixed results.

## Evidence

[Run 42534101240](https://github.com/chrisbanes/haze/actions/runs/42534101240), head
`1bf6fdce790c1de76e34864cfbe04c196d59a861`, used `windows-2025`, Java 21,
Skiko 0.150.2, `DIRECT3D` and Microsoft Basic Render Driver. Full resolved dependency filenames,
renderer identity, exact generated SKSL, uniforms, timings, ETLs, trace statistics and decoded
target-process stacks are in its `windows-glass-first-draw` artifact.

- Three unprofiled reduced trials: cold 2,552.08 / 2,523.85 / 2,450.37 ms;
  warm medians 15.18 / 2.09 / 14.95 ms.
- Profiled reduced case: 2,717.47 ms cold, 14.44 ms warm median.
- Profiled refraction-free control: 64.40 ms cold, 14.97 ms warm median.
- Profiled original unmodified continuous case: first draw still running at the 30-second deadline.
  This is a censored measurement, not a completed compiler duration.

The xperf exports are scoped to each first-draw interval using the trace's UTC start and the
application's phase timestamp (`first-draw-range-us.json`). All three traces report zero lost
events/buffers. Exclusive target CPU samples:

| Case | Total | D3D10Warp.dll | d3dcompiler_47.dll |
| --- | ---: | ---: | ---: |
| Original continuous | 29,943 | 23,469 | 5,434 |
| Reduced | 2,701 | 1,735 | 777 |
| Refraction-free control | 54 | 1 | 25 |

Both failing cases resolve this WARP compilation path:

```text
D3D10Warp!Task_CompileShader
  PixelJITProcessor::InitializeJIT
    CProgram::Compile
      ComputeUsage / Optimize / BuildDependencies / SeparateVariables
```

D3DCompiler also resolves `CProgram::ReadWriteInfo`. The evidence points at backend compilation,
including WARP's pixel-shader JIT, rather than steady-state software rasterization or presentation
waits. These are sampled CPU counts, not wall-time shares; the control sample is small. JVM/Skiko
private-symbol gaps remain. It does not isolate a specific compiler algorithm defect.

## Attempts and limits

Guarding member-loop bodies instead of `break` does not fix the original stall. Adding test-only
`noinline` to the shared `shapeSd` helper preserves raster pixels but leaves all three reduced native
trials with the same seconds-long cold signal. Neither is proposed as a production workaround.
Reducing draw area also leaves the original stall intact.

The frozen source is feature-reduced, with unused generated helpers retained for source fidelity.
Physical GPUs, newer Skiko versions and a standalone D3D bytecode/HLSL reproduction remain untested.
The Haze issue remains open: [#1767](https://github.com/chrisbanes/haze/issues/1767).

## Requested triage

Can this runtime-effect input be used to isolate the generated backend program and determine whether
Skia lowering, D3DCompiler or WARP needs to change? A safe equivalent shader workaround would also
help; the existing evidence does not justify a production shader rewrite.
