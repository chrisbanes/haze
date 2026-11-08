# Windows progressive-blur regression

Issue [#1260](https://github.com/chrisbanes/haze/issues/1260) reproduced on the free
`windows-2025` GitHub runner with Direct3D and Microsoft Basic Render Driver. A physical GPU is
not required to reproduce this first-draw stall. Requesting OpenGL on that runner fell back to
`SOFTWARE_FAST`; do not report it as an OpenGL measurement.

## Run

Use Java 21 and an interactive Windows desktop. From the repository root in PowerShell:

```powershell
$env:SKIKO_RENDER_API = 'DIRECT3D'
$env:HAZE_PROBE_WIDTH = '640'
./gradlew.bat :sample:desktop:run '-Phaze.disableAppleTargets' '-Phaze.windowsBlurProbe=true' --no-scan
python scripts/check_windows_blur_probe.py sample/desktop/build/outputs/windows-blur-probe
```

Agents use the managed Gradle wrapper when available. The workflow
[`windows-blur-probe.yml`](../../.github/workflows/windows-blur-probe.yml) exercises both 64×48 and
640×480 content in two separate native windows. Windows may enforce a larger minimum window width.
Each job starts a fresh process; the second window retains process-level runtime-effect caches.
The workflow is manually dispatched once available on the default branch.

The diagnostic entry point calls the production progressive-blur factory at a 24px radius with a
vertical alpha gradient and striped content. It times construction separately from Skiko's
`renderImmediately()` call, which covers recording and the draw/flush/present path. These are CPU
wall times, not isolated GPU execution or compiler timings. Screenshots must visibly differ from
the unfiltered control. The checker requires both windows to use Direct3D, complete timing/image
records, and first progressive draws below a generous five-second stall ceiling. This ceiling is
not a frame-rate target.

## Diagnosis

- [Original reproduction](https://github.com/chrisbanes/haze/actions/runs/42506304613): first draws
  took 26.82s and 26.93s; subsequent Direct3D draws took about 99ms. Effect construction took 21ms
  initially and 0.15ms in the second window.
- [Minimized, failing regression](https://github.com/chrisbanes/haze/actions/runs/42507313548):
  reducing content to 64×48 still produced 39.40s and 40.06s stalls. This rules out pixel volume
  as a necessary trigger. Runner-to-runner variation prevents treating the larger times as a
  regression caused by the smaller image.
- [Controlled shader experiments](https://github.com/chrisbanes/haze/actions/runs/42507870623):
  changing only the static loop bound to 24 reduced first draws to 578ms and 536ms. Keeping the
  full bound but replacing the early `break` with an independently guarded body reduced them to
  148ms and 115ms. Both candidates produced exactly the same screenshot pixels as the minimized
  baseline in both windows.
- [Production fix](https://github.com/chrisbanes/haze/actions/runs/42508432171): both sizes passed
  the stall gate. First draws at 640×480 took 418ms and 374ms; at 64×48 they took 270ms and 189ms.
  All four progressive screenshots exactly matched the original content pixels (cropping the
  older full-size capture to the common 640×480 content rectangle).

Warm full-size draws had medians of 192ms and 205ms after the fix, versus 99ms and 98ms originally.
These runs used different shared runner instances, so they are not a controlled steady-state
performance comparison. The guarded loop may trade compilation cost for execution cost on the
software renderer; physical-GPU measurements are needed before claiming steady-state improvement.

The early-exit control flow is the isolated trigger. Expensive deferred Direct3D compilation is
the supported explanation, but no native compiler profile was captured. The production fix keeps
the full 150px support, Gaussian weights, paired sampling, odd-radius tail, and crop behavior;
it changes only the loop's control flow. Negating the original comparison also preserves its
behavior for unordered floating-point values.

Independent JVM sampling tests cover odd/even radii, the 149/150/151px boundary, gradients,
premultiplied output, and fractional crops. Android host tests cover crop coordinates. These checks
do not establish physical Android or Windows GPU performance. The diagnostic workflow and entry
point are retained as a regression harness; the one-variable source-mutation experiments were
removed.
