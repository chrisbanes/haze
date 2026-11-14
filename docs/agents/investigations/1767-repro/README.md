# Direct3D first-draw reproducer

Frozen Skiko reproduction for [Haze #1767](https://github.com/chrisbanes/haze/issues/1767).
This is investigation code, not a production shader workaround. It uses Kotlin 2.4.21,
Skiko 0.150.2 and Java 21, without Haze or Compose dependencies.

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

The script forces `DIRECT3D` and runs three fresh JVMs per case. Each creates an empty SkiaLayer,
warms its empty surface three times, then measures the first shader draw and six warm draws.
The viewport is 64×48; fixture geometry remains 240×160 with two merged members. It saves renderer
identity, source, phase, timings and a nonblank screenshot under `build/results/<id>`.
A 60-second watchdog terminates only the child it owns and marks the run invalid.

Exit codes:

- `1`: all three retained trials have a first draw above one second and above twenty times
  `max(1 ms, warm median)`, while all paired controls avoid that signal.
- `0`: no retained trial has that signal. This alone does not qualify a production fix.
- `2`: invalid setup, process/render failure, timeout, failed control or mixed retained results.

These are diagnostic thresholds, not device performance guarantees. A fresh JVM/context does not
prove an empty disk/driver cache. The measured adapter was **Microsoft Basic Render Driver (WARP)**;
physical-GPU behavior is unmeasured. Record `renderer.txt` when comparing another machine.

## Fixture provenance

`retained.sksl`, `control.sksl`, `uniforms.txt` and `content.sksl` are captured verbatim from
[run 42534101240](https://github.com/chrisbanes/haze/actions/runs/42534101240), head
`1bf6fdce790c1de76e34864cfbe04c196d59a861`. The paired control changes only the refraction bypass.
The retained case uses these feature cuts:

```text
ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,
signed-distance,optical-height,rect-gradient,compact-main,member-weights,standalone-refraction
```

This is a feature-reduced case, not a claim of the lexically smallest shader. Unused generated
helpers remain to preserve exact measured source. Haze's `WindowsGlassProbeTest` checks both frozen
sources and the uniforms against that generator. The separate native ZIP run checks that removing
the Haze/Compose dependencies preserves the stall.

The [exported ZIP qualification](https://github.com/chrisbanes/haze/actions/runs/42536087987) at
`78ff8d6f399df500a0409784bc4cb0cb68b3bbb7` built this project in runner temporary storage, outside
the checkout. All three retained trials reproduce the cold stall and all three paired controls
pass on Microsoft Basic Render Driver. A forced watchdog test returns INVALID and preserves both
process streams. The run's `windows-glass-first-draw` artifact contains the qualified source ZIP,
standalone timings, renderer identities, screenshots and partial watchdog outputs.
