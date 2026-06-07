# V2 API Cleanup Design

## Context

The API changes on `main` since `v1` split blur behavior out of the core `haze`
module and introduced the `VisualEffect` extension point. The current API carries
some partial v1 migration helpers, but the project is allowed to make a hard
source break for v2. The cleanup should therefore optimize for a coherent v2 API
rather than preserving old call sites.

## Goals

- Make the public v2 API use only the new blur names and packages.
- Remove partial migration helpers that imply broader source compatibility than
  the library intends to provide.
- Preserve ergonomic `HazeBlurStyle` construction while making its immutable
  contract defensible.
- Allow callers to explicitly clear inherited blur color effects.
- Reshape `LiquidGlassStyle` while `haze-liquidglass` is still experimental.
- Update migration docs and tests so future changes do not reintroduce the same
  ambiguity.

## Non-Goals

- Do not provide full source compatibility with v1.
- Do not redesign the `VisualEffect` lifecycle API.
- Do not add a new public collection dependency to the stable blur API.

## API Cleanup

Remove the partial v1 migration surface from `haze-blur`:

- Root-package type aliases for `HazeStyle`, `HazeTint`, and `HazeProgressive`.
- Root-package `LocalHazeStyle` forwarding property.
- Deprecated `Modifier.hazeEffect(state, style, block)` forwarding overload.
- Deprecated blur-package aliases or factory functions whose only purpose is to
  preserve old names, unless a specific alias is still needed by current source.

The only supported public names for v2 blur APIs should be:

- `dev.chrisbanes.haze.blur.HazeBlurStyle`
- `dev.chrisbanes.haze.blur.HazeColorEffect`
- `dev.chrisbanes.haze.blur.HazeProgressive`
- `dev.chrisbanes.haze.blur.LocalHazeBlurStyle`
- `dev.chrisbanes.haze.blur.blurEffect`
- `dev.chrisbanes.haze.blur.materials.*`

This makes the hard break explicit and prevents new v2 code from being written
against deprecated compatibility names.

## Blur Style Semantics

`HazeBlurStyle` should continue to accept normal Kotlin lists at construction
boundaries for ergonomic call sites. Internally, it should take an immutable
snapshot of the supplied list so later mutations to a caller-owned mutable list
cannot change the observable style object. This keeps the existing `@Immutable`
annotation defensible without adding a public immutable-collections dependency.

`colorEffects` needs a distinct unspecified state. Today an empty list is treated
as unspecified, so callers cannot intentionally override an inherited
`LocalHazeBlurStyle` or `style` value with no color effects. The v2 hard break
should make this explicit:

- `null` means unspecified and allows fallback to the next precedence tier.
- `emptyList()` means specified empty and disables inherited color effects.
- Non-empty lists mean specified effects.

The direct `BlurVisualEffect.colorEffects` property should follow the same
precedence rule. Its setter should defensively snapshot the supplied list, and
its getter should return the resolved value used for drawing. If an unresolved
authoring value is needed internally, keep that as private state.

## Liquid Glass Style Shape

`haze-liquidglass` remains experimental, so this cleanup should also tidy the
wide `LiquidGlassStyle` API before users build against the flat 21-property
constructor. The goal is to keep rendering behavior and tuned defaults the same
while making the API easier to scan, document, and evolve.

Replace the flat style with grouped immutable value types:

- `LiquidGlassStyle`
  - `tint: Color`
  - `shape: RoundedCornerShape?`
  - `optics: LiquidGlassOptics`
  - `lighting: LiquidGlassLighting`
  - `color: LiquidGlassColor`
  - `rendering: LiquidGlassRendering`
- `LiquidGlassOptics`
  - `refractionStrength`
  - `refractionHeight`
  - `refractionScale`
  - `depth`
  - `blurRadius`
- `LiquidGlassLighting`
  - `specularIntensity`
  - `specularExponent`
  - `fresnelExponent`
  - `ambientResponse`
  - `lightPosition`
- `LiquidGlassColor`
  - `alpha`
  - `contrast`
  - `whitePoint`
  - `chromaMultiplier`
- `LiquidGlassRendering`
  - `edgeSoftness`
  - `contentNormalBlend`
  - `surfaceProfile`
  - `chromaticAberrationStrength`
  - `chromaticAberrationMode`

Each group should use the same unspecified-value convention as the current flat
style: `Float.NaN` for unresolved numeric floats, `Dp.Unspecified` for unresolved
distances, `Offset.Unspecified` for unresolved light position, and nullable enum
or shape values where `null` means inherit. `LiquidGlassStyle.Unspecified` should
compose the unspecified group values, and `LiquidGlassDefaults.style` should
compose the fully resolved default group values.

`LiquidGlassVisualEffect` may keep its direct mutable properties for convenient
per-effect overrides. Internally, style resolution should read from the grouped
style objects using the same precedence as today:

1. Direct property value, if specified.
2. Matching value in `LiquidGlassVisualEffect.style`, if specified.
3. Matching value in `LocalLiquidGlassStyle`, if specified.
4. `LiquidGlassDefaults`.

Rename only where it reduces ambiguity. The initial grouped names above preserve
the current property names, so migration is mostly moving values into the right
group. More subjective renames should wait unless implementation reveals a name
that is clearly misleading.

## Documentation

Update `docs/migrating-2.0.md` so it accurately describes v2 as a hard source
break:

- Remove claims that core modifier signatures remain unchanged where style
  overloads were removed.
- Show the required dependency and import changes for `haze-blur`.
- Show direct replacements for v1 style usage:
  `Modifier.hazeEffect(state, style = style)` becomes
  `Modifier.hazeEffect(state) { blurEffect { this.style = style } }`.
- Mention the package move from `dev.chrisbanes.haze.materials` to
  `dev.chrisbanes.haze.blur.materials`.
- Mention that old root-package blur names are intentionally removed rather than
  deprecated for the v2 hard break.
- Add a liquid glass section showing how flat `LiquidGlassStyle` construction
  moves into grouped `optics`, `lighting`, `color`, and `rendering` values.

## Tests

Add focused tests covering the new contract:

- `HazeBlurStyle` snapshots a mutable `colorEffects` input.
- `BlurVisualEffect.colorEffects = emptyList()` explicitly clears inherited
  style and composition-local effects.
- `HazeBlurStyle(colorEffects = emptyList())` explicitly clears inherited local
  effects when used as the effect style.
- `LiquidGlassStyle` group defaults resolve to the same effective values as the
  current flat defaults.
- A partially specified `LiquidGlassStyle` group inherits unspecified values from
  `LocalLiquidGlassStyle` and `LiquidGlassDefaults` with the same precedence as
  the current flat style.
- API snapshots no longer include the removed migration aliases or forwarding
  overloads, and show the grouped liquid glass style API instead of the flat
  21-property constructor.

These tests should be narrow and local to the affected modules. Existing visual
or screenshot tests should only be updated if behavior intentionally changes.

## Risks

The main risk is migration friction for users moving directly from v1 to v2.
This is acceptable for v2, but the migration guide must be precise and
actionable. The second risk is accidental behavioral change in default blur
styles; defensive list copying should not affect defaults, and explicit empty
semantics should only affect call sites that intentionally use an empty list.
The liquid glass grouping carries a higher implementation risk because every
flat style field must be mapped into a group without changing resolved rendering
values. Focused default-resolution tests should catch this.
