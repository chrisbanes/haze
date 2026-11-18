# Direct3D first-draw reproducer

Frozen Skiko reproduction for [Haze #1767](https://github.com/chrisbanes/haze/issues/1767).
This is investigation code, not a production shader workaround. It uses Kotlin 2.4.21,
Skiko 0.150.2 by default and Java 21, without Haze or Compose dependencies. Select another Skiko
release with `-PskikoVersion=<version>`; `run.ps1 -ExpectedSkiko <version>` then rejects any trial
whose runtime `org.jetbrains.skiko.Version.skiko` differs.

## Run on Windows

Use Java 21, PowerShell 7 and a visible Windows x64 desktop. From an extracted
`windows-glass-repro.zip`:

```powershell
./gradlew.bat installDist --no-scan
pwsh -File run.ps1
```

Build the ZIP from the Haze repository with:

```powershell
./gradlew.bat :haze-glass:packageWindowsGlassRepro '-Phaze.disableAppleTargets' --no-scan
```

It is written to `haze-glass/build/outputs/windows-glass-repro.zip`, with source,
fixture resources, license and Gradle wrapper. Build setup needs Maven Central and Gradle network
access; each subsequent draw trial launches Java directly from the installed distribution.

The script forces `DIRECT3D` and runs three fresh JVMs per case. Each times shader construction
(`RuntimeEffect.makeForShader` plus `RuntimeShaderBuilder.makeShader`), creates an empty SkiaLayer,
warms its empty surface three times, then measures the first shader draw and six warm draws.
The `retained` and `control` viewport is 64×48; `original` is drawn at its measured 240×160.
Fixture geometry remains 240×160 with two merged members. It saves `identity.txt` (Skiko and Skia
versions, case, source SHA-256, viewport, Java, OS), renderer identity, source, phase, timings and a
nonblank screenshot under `build/results/<id>`, plus `results.json` rows with construction, first-draw
and warm-median times and a per-trial verdict. A 60-second watchdog terminates only the child it
owns. Expiry marks the run invalid, except for `original`, which is recorded as
`INVALID (censored >= 60 s)` with both process streams, and the remaining cases continue. A
censored trial is a lower bound, never a duration or a pass.

Parameters, with defaults matching the original qualification run:

```powershell
pwsh -File run.ps1 -Install build/install/windows-glass-repro -ExpectedSkiko 0.150.2 `
  -Cases control,retained -Trials 3 -Output build/results/<guid>
```

`-Cases` also accepts `original`. Original trials over 5 s are reported as `over-5s`.

Exit codes:

- `1`: all requested retained trials have a first draw above one second and above twenty times
  `max(1 ms, warm median)`, while all paired controls avoid that signal.
- `0`: no retained trial has that signal, or no retained case was requested. This alone does not
  qualify a production fix.
- `2`: invalid setup, process/render failure, timeout, failed control or mixed retained results.

These are diagnostic thresholds, not device performance guarantees. A fresh JVM/context does not
prove an empty disk/driver cache. The measured adapter was **Microsoft Basic Render Driver (WARP)**;
physical-GPU behavior is unmeasured. Record `renderer.txt` when comparing another machine.

## Fixture provenance

`original.sksl` is the unreduced continuous source of the profiled 240×160 original case in
[run 42534101240](https://github.com/chrisbanes/haze/actions/runs/42534101240), head
`1bf6fdce790c1de76e34864cfbe04c196d59a861` (SHA-256
`80410be10e8eb678519f41d6cc5c6bbf3c22b23aa5bff2213bc36ea1b33a10ab`). That case's `uniforms.txt`
(SHA-256 `249f598793605102cc7741614a4f9a9236d0bdf63df0a120fe9c82d496d286aa`) is byte-identical to
the shared `uniforms.txt`, so every case reads that file. These frozen bytes, not whatever Haze
production generates later, are the measured fixture.

`retained.sksl`, `control.sksl`, `uniforms.txt` and `content.sksl` are captured verbatim from
[run 42534101240](https://github.com/chrisbanes/haze/actions/runs/42534101240), head
`1bf6fdce790c1de76e34864cfbe04c196d59a861`. The paired control changes only the refraction bypass.
The retained case uses these feature cuts:

```text
ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,
signed-distance,optical-height,rect-gradient,compact-main,member-weights,standalone-refraction
```

This is a feature-reduced case, not a claim of the lexically smallest shader. Unused generated
helpers remain to preserve exact measured source. Haze's `WindowsGlassProbeTest` checks the fixture
hashes, that the retained and control cuts of `original.sksl` reproduce `retained.sksl` and
`control.sksl`, and the uniforms. The separate native ZIP run checks that removing
the Haze/Compose dependencies preserves the stall.

The [exported ZIP qualification](https://github.com/chrisbanes/haze/actions/runs/42536087987) at
`78ff8d6f399df500a0409784bc4cb0cb68b3bbb7` built this project in runner temporary storage, outside
the checkout. All three retained trials reproduce the cold stall and all three paired controls
pass on Microsoft Basic Render Driver. A forced watchdog test returns INVALID and preserves both
process streams. The run's `windows-glass-first-draw` artifact contains the qualified source ZIP,
standalone timings, renderer identities, screenshots and partial watchdog outputs.

## D3DCompile ownership probe

`d3dcompile.py profile <root> <output> <version>...` runs each case once per version under
[Frida](https://frida.re) 17 (`pip install frida==17.24.3`) with `d3dcompile-hook.js`, which records
every `d3dcompiler_47!D3DCompile`/`D3DCompile2` call: HLSL, entry point, target, flags and in-process
duration. It then compiles each captured HLSL with the same entry point, target and flags through
`D3DCompile` in three fresh Python processes. Hooked runs are diagnostic only and never count toward
the comparison timings. A call still running when the 90-second capture watchdog fires keeps its
HLSL but has no duration; a direct compile over 120 seconds is censored. HLSL diffs pair calls by
their order within a case.
