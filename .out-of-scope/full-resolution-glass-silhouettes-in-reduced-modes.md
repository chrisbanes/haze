# Full-resolution Glass silhouettes in reduced-quality modes

Haze does not render Glass's rounded silhouette at full output resolution when
`HazePerformanceMode.Balanced` or `HazePerformanceMode.Performance` is in use.
In those modes the whole Glass result, including its rounded-shape coverage, is
rendered into reduced-resolution retained layers and enlarged. Corners are
therefore sampled on the reduced grid and can look coarser than in `Quality`.

## Why this is out of scope

Reduced modes exist to trade visual fidelity for GPU memory and frame time. A
coarser edge is part of that trade, and the only measured way to remove it
costs more than the modes are meant to save.

The approach that was tried assembled the reduced optical stages into an
output-sized `GroupComposite` and applied one rounded SDF coverage pass in
output coordinates, with rim and interaction lighting evaluated at full
resolution. It fixed the visual defect and passed host and screenshot
validation. On a controlled Pixel 8a comparison, however, Balanced and
Performance then used roughly 9–15% more peak GPU memory than `Quality` and
showed stable CPU frame-time regressions. Cheaper alternatives that were
assessed did not satisfy both the visual and memory requirements. Changing the
upscale filter alone does not recover the missing coverage samples.

Callers who need exact rounded corners should use `HazePerformanceMode.Quality`.

## When to revisit

Reconsider this if Compose or Skiko gain a safe way to sample a GPU layer from
a shader (a layer-to-shader capability). That would allow output-resolution
coverage over reduced optical work without an extra full-size retained layer.
The prototype branches, benchmark artifacts, and traces from the original
attempt are referenced from the issue and PR below.

## Prior requests

- [#1339](https://github.com/chrisbanes/haze/issues/1339): "Render rounded Glass silhouettes at full resolution in reduced-quality modes"
  (attempted in [#1342](https://github.com/chrisbanes/haze/pull/1342), closed unmerged)
