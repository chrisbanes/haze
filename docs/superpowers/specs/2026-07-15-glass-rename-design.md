# Glass Rename Design

## Goal

Rename Haze's unpublished, experimental Liquid Glass feature to the generic name Glass without changing rendering behavior, defaults, calibration, or golden pixels.

## Naming

Use Glass terminology throughout the Haze-owned implementation:

- Rename the Gradle project from `:haze-liquidglass` to `:haze-glass`.
- Rename the package and Android namespace from `dev.chrisbanes.haze.liquidglass` to `dev.chrisbanes.haze.glass`.
- Rename public symbols to `GlassVisualEffect`, `GlassStyle`, `GlassOptics`, `GlassLighting`, `GlassColor`, `GlassRendering`, `GlassDefaults`, `LocalGlassStyle`, and `glassEffect`.
- Keep already-generic names such as `SurfaceProfile` and `ChromaticAberrationMode`, moving them into the new package.
- Rename Haze-owned internal symbols, source files, tests, samples, log tags, screenshot basenames, documentation paths, and resource paths from `LiquidGlass`/`liquidGlass`/`liquid-glass` to `Glass`/`glass`.
- Rename `docs/effects/liquid-glass.md` to `docs/effects/glass.md` and present the feature as Glass.

The old module, package, and symbols will be removed without aliases or deprecation shims. This is acceptable because the module is unpublished, experimental, and documented as unavailable from Maven Central.

## Apple Terminology

Retain the phrase "Liquid Glass" only when explicitly identifying Apple's iOS material or reference target. Preserve Apple-owned API spellings, including SwiftUI's `glassEffect`, unchanged.

## iOS Reference Pipeline

Keep the complete iOS reference pipeline and rename its Haze-owned terminology:

- Rename `internal/ios-liquid-glass-reference-capture` to `internal/ios-glass-reference-capture`.
- Rename its Xcode project, schemes, products, Swift types and tests, scripts, dedicated simulator name, and documentation where those names are owned by Haze.
- Move committed fixtures from `haze-screenshot-tests/src/commonTest/resources/liquid-glass/ios26` to `haze-screenshot-tests/src/commonTest/resources/glass/ios26`.
- Update and rename JVM resource loading, metric types, constants, and tests to Glass terminology.
- Keep references to Apple's iOS Liquid Glass material where they describe the provenance or fidelity target.

The four PNG fixtures and their manifest remain accepted immutable calibration inputs. The rename must not regenerate or alter their bytes.

## Behavior And Compatibility

This is a source-breaking naming change with no compatibility layer. Rendering algorithms, shaders, defaults, style precedence, lifecycle behavior, calibration bands, test thresholds, and platform support remain unchanged.

## Verification

- Regenerate and verify the `haze-glass` API dump.
- Run the renamed module's unit and JVM tests.
- Run Android and desktop screenshot tests against the renamed, byte-identical goldens.
- Run the iOS capture script self-test without performing a native reference capture.
- Run Spotless checks and confirm no Haze-owned `LiquidGlass`, `liquidGlass`, or `liquid-glass` names remain outside explicit Apple-reference prose.

A native iOS capture is not required because it depends on pinned Xcode and simulator versions and the feature output is unchanged.
