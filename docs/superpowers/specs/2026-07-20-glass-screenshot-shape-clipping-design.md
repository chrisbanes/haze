# Glass Screenshot Shape Clipping Design

## Goal

Make every visible pixel in the Glass screenshot fixtures respect the configured surface shape, and keep the screenshots capable of detecting future Glass clipping regressions.

## Root cause

`GlassInteractionScene` configures a rounded `GlassVisualEffect`, then draws a translucent rectangular `background` over the entire node. The rectangle remains visible in the idle capture, proving that it is fixture content rather than an interactive lighting leak. Removing it is preferable to shaping it because the Glass tint already supplies the intended surface wash and the extra layer obscures which pixels come from Glass.

The runtime interaction shader already applies the rounded-rectangle signed-distance mask. The fallback interaction highlight clips to the generated shape path. The fix therefore belongs in the screenshot fixture, not in another renderer clipping pass.

## Screenshot audit boundary

- Remove the rectangular wash from `GlassInteractionScene`.
- Keep the interaction scene free of an external `Modifier.clip`; its screenshots should expose any future leak from the Glass tint, optics, rim, interaction lighting, or material transform.
- Keep explicit surface clipping in production-style fixtures such as `CreditCardSample` and Gallery `GlassSurface`, where child content is part of the shaped component.
- Keep the existing `GlassBlurRadiusSample(clipShape = false)` coverage, which deliberately exercises the Glass renderer without an external clip.
- Keep Playground surfaces free of unrelated overlays; their Glass effect shape remains the only source of material pixels.
- Do not add a library-level clip solely to hide fixture content that the library does not own.

## Regression strategy

1. Remove the wash and run the interaction screenshot test before recording. The expected baseline mismatch is the red signal that the fixture changed.
2. Record every affected Glass interaction baseline on Desktop and Android, including Android API variants produced by the test task.
3. Inspect representative idle, lighting, optics, material-only, material-and-content, center-pivot, and reduced-motion outputs. Rounded corners must reveal the underlying scene with no rectangular fill.
4. Run the complete `haze-screenshot-tests` JVM and Android-host suites so the audit covers the other Glass fixtures, not only interaction tests.
5. Run the Gallery screenshot suite to confirm Product, Playground, and Lab retain their intended clipping. Do not absorb unrelated pre-existing Product baseline differences.
6. Run Spotless and `git diff --check`, and retain only interaction baselines changed by removal of the wash.

## Expected outcome

The interaction screenshots show only Glass-generated surface pixels and shaped child content. Their transparent rounded corners make any future unmasked tint, optics, rim, highlight, or transform output visually obvious.
