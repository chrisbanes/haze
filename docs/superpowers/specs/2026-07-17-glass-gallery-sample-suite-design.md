# Glass Gallery Sample Suite Design

## Goal

Replace the existing Glass credit-card and debug samples with a cohesive three-sample suite that
shows Glass as a useful product material, a cinematic visual effect, and an inspectable technical
API. The suite is the canonical source for recorded Glass demonstrations and must work well in
portrait and landscape layouts.

## Context

The current Glass sample reuses the shared stacked-credit-card scene over a static gradient and
text field. It proves that the effect renders, but it does not provide a strong product story,
varied surface geometry, repeatable motion, or recording-oriented controls. The debug sample
exposes rendering differences, but it is intentionally utilitarian rather than presentable.

Glass now provides geometry-aware `GlassOptics.Adaptive` optics plus complete literal
`GlassOptics.Absolute` configurations. The showcase should lead with Adaptive as the real-product
default, then use literal configurations where comparison and spectacle are the purpose. It must
not imply support for interaction-driven deformation or morphing.

## Scope

Replace the two existing sample-list entries with:

- **Glass — Product**
- **Glass — Playground**
- **Glass — Lab**

Remove `GlassCreditCardSample` and `GlassDebugSample`. Keep `CreditCardScene` because the separate
Blur credit-card sample still uses it.

The suite includes shared deterministic artwork, shared recording chrome, responsive portrait and
landscape layouts, manual interactions, repeatable Playground autoplay, semantic UI coverage, and
Android and Desktop screenshot coverage.

## Non-Goals

- Do not change Glass rendering, public APIs, defaults, delegates, or fallback behavior.
- Do not add network-fetched demo content or runtime asset generation.
- Do not reproduce a named platform's glass design language.
- Do not animate a Glass surface's shape as if Glass supported deformation or morphing.
- Do not turn the Product sample into a general-purpose application architecture.
- Do not expose every internal rendering value in the Lab.

## Shared Visual World

The suite is called **Glass Gallery**. It uses a small bundled collection of high-colour poster
artworks containing gradients, contrasting colour boundaries, oversized typography, fine lines,
and small geometric details. Those features keep refraction, depth blur, edge response, and
chromatic separation visible as content moves behind a Glass surface.

Artwork is deterministic and local. It may be drawn from immutable poster descriptions in Compose
or stored as bundled resources, but it must not rely on Coil, network access, current time, random
values, or platform-specific content. Each poster has a stable identifier, title, subtitle, colour
palette, and accessible description. The same artwork collection appears in all three samples so
the suite has one recognizable Haze identity.

The visual hierarchy is deliberately restrained around the effect:

- Full-bleed artwork owns the background.
- Glass surfaces use readable monochrome content and modest tints.
- Labels explain interactions outside recording mode without covering the optical boundary.
- The effect remains visible when runtime shaders fall back to the existing approximate renderer.

## Sample 1: Glass — Product

### Purpose

Present Adaptive Glass as a premium, useful application material rather than a shader experiment.

### Scene

The Product sample is a gallery viewer with one full-bleed poster at a time. It places three fixed
Glass surfaces above the artwork:

- a compact top bar containing back navigation, the gallery name, and the poster count;
- a metadata card containing the current poster's title and subtitle;
- a bottom action dock containing previous, favorite, information, and next actions.

Swiping horizontally changes posters. A short vertical details region lets artwork, typography, and
fine lines move beneath the fixed metadata surface. Previous and next actions perform the same
selection change as a swipe. Favorite is local ephemeral UI state and has no persistence or
business-layer dependency.

The Product sample uses `GlassOptics.Adaptive`. It may apply restrained light/dark tint variants and
surface-specific rounded shapes, but it does not replace Adaptive optics with literal values. This
keeps the sample honest about the built-in material.

### Responsive Layout

Portrait places the metadata card above a wide bottom dock. Landscape keeps the artwork full-bleed,
moves metadata toward the leading side, and turns the action dock into a compact trailing cluster.
The three surfaces retain bounded sizes; they do not stretch to fill every available pixel.

## Sample 2: Glass — Playground

### Purpose

Create the most visually striking recording surface while demonstrating how content, geometry,
lighting, and optical configuration affect Glass.

### Scene

The Playground renders a slowly moving wall of Gallery posters beneath four independently bounded
Glass surfaces:

- a small circular lens using Adaptive optics;
- a wide pill using Adaptive optics;
- a larger rounded card using a deeper literal treatment;
- a restrained prismatic surface using a literal treatment with chromatic aberration.

The shapes are fixed for the lifetime of the scene. Their positions and lighting change, but the
choreography does not morph or deform their material boundaries.

### Choreography

One normalized `0f..1f` progress value defines a seamless 12-second loop:

1. The poster wall begins a slow lateral pan.
2. The circular lens crosses oversized typography and fine detail.
3. The pill crosses two strong colour boundaries.
4. The large card moves across the composition to reveal depth blur and highlights.
5. The prismatic surface crosses a high-contrast region for a restrained chromatic final beat.
6. Artwork, surfaces, and light positions meet their opening values at the loop boundary.

All animated properties derived from the same beat use one coordinated transition or timeline so
they cannot drift. Fast-changing values are read in layout or draw phases rather than passed as
composition-time scalar parameters through the tree.

### Manual Interaction

Each surface is draggable. Starting a drag pauses the complete timeline at its current progress and
gives the pointer direct ownership of that surface. Releasing the surface springs it back to its
choreographed position at the paused progress. Autoplay resumes only after the return animation
finishes. Cancel behaves like release.

Play/pause and reset controls are available outside recording mode. Reset selects the first poster,
sets progress to `0f`, clears any drag override, and resumes autoplay. Paused surfaces remain
draggable.

## Sample 3: Glass — Lab

### Purpose

Expose meaningful Glass configurations without reproducing the former debug grid or overwhelming
the user with every low-level value at once.

### Scene

One large specimen sits over a selectable diagnostic backdrop. Available backdrops include the
Gallery poster, a high-contrast grid, large typography, colour bands, and a uniform field. The
uniform field makes tint, edge, and lighting behavior easier to inspect; the other backgrounds make
refraction, blur, and chromatic separation obvious.

The control surface starts with five semantic presets:

- **Adaptive** — untouched `GlassOptics.Adaptive` with the default grouped properties.
- **Clear** — high refraction, minimal depth and blur, and no chromatic aberration.
- **Frosted** — dominant depth blur, a modest tint, and reduced background chroma.
- **Deep** — stronger displacement and depth with balanced lighting.
- **Prism** — moderate depth and full-mode, restrained chromatic aberration.

Preset definitions are immutable values with stable identifiers. Exact numeric values are an
implementation-level visual calibration: implementation starts from the semantic targets above,
records the chosen constants in one preset table, and freezes them with mapping tests. Subsequent
numeric changes are intentional screenshot changes, not runtime tuning.

An Advanced disclosure groups editable controls by the public API structure:

- Optics
- Lighting
- Colour
- Rendering

Selecting a preset replaces the complete editable configuration. Editing any advanced control
changes the selected label to **Custom**. Selecting Adaptive again discards custom literal optics
and restores `GlassOptics.Adaptive`. Reset restores Adaptive, the Gallery backdrop, and the default
specimen shape.

Portrait stacks the specimen above a scrolling control pane. Landscape fixes the specimen on the
leading side and gives the trailing side to controls. Controls never overlay the specimen's optical
boundary.

## Shared Components and Ownership

Keep shared code narrowly focused:

- `GalleryArtwork` holds immutable artwork identity, copy, and palette data.
- `GalleryBackdrop` renders a selected poster or diagnostic background.
- `GlassSurface` applies a supplied `GlassStyle` or effect configuration to a caller-provided shape
  and content slot. Placement and size remain the caller's responsibility.
- `DemoChrome` provides the visually consistent back, record, reset, and play/pause controls a
  screen requests. It does not own navigation or screen state.
- The Lab preset table maps semantic preset identifiers to complete immutable Glass configuration.
- A pure Playground timeline maps normalized progress and layout bounds to a complete frame of
  artwork, surface, and lighting positions.

Each screen has a small state-owning composable and a plain UI composable. The state-owning layer
wires navigation and coordinated UI state; the UI layer accepts immutable state and explicit event
callbacks. No ViewModel, repository, Flow, or dependency injection is needed for these local demo
interactions.

Animation, drag, scroll, and interaction-source state remain UI-local. Coordinated Playground state
may use a remembered plain state holder because pause, drag ownership, return animation, reset, and
autoplay form one interaction unit. Child composables receive provider lambdas when a frame-rate
value must cross a composable boundary.

## Recording Mode

Product and Playground expose a recording-mode toggle. Recording mode hides explanatory text,
reset/playback controls, and other nonessential chrome while leaving product UI that belongs to the
scene. Platform back navigation continues to work. Tapping an unobstructed backdrop area reveals
the hidden chrome; tapping or dragging a Glass surface retains its normal interaction.

Lab does not hide its controls by default because they are the subject of that sample, but it may
hide explanatory copy while retaining the specimen and parameter controls.

Entering recording mode does not change artwork, playback progress, or selection. Reset always
returns to the same complete recording state. No control hides itself on an elapsed timer.

## Fallbacks and Accessibility

- Existing Glass fallback rendering remains responsible for devices without the runtime shader
  path. Background contrast and surface tint must keep content legible on that path.
- When animation is disabled, Playground renders the progress-`0f` hero frame and remains manually
  draggable. Product and Lab retain all nonanimated interactions.
- Every action uses a stable content description or visible label. Glass surfaces that are purely
  decorative are excluded from accessibility semantics; draggable surfaces expose a concise role
  and interaction description.
- Small windows preserve the primary specimen and make secondary controls scroll rather than clip.
- The suite supports light and dark system themes, but poster artwork itself remains stable so theme
  changes do not invalidate the optical comparison.

## Testing

### Pure Tests

- Assert every Lab preset maps to the intended optics kind and complete grouped Glass properties.
- Assert editing a preset produces Custom and selecting Adaptive removes literal optics.
- Assert Playground progress values `0f` and `1f` produce equivalent loop-boundary frames.
- Assert key progress values place each surface within normalized scene bounds.
- Assert reset produces the documented complete initial state.

### Compose UI Tests

- Assert the sample list contains Product, Playground, and Lab and no longer contains the previous
  Glass and Glass Debug entries.
- Product: assert previous, next, swipe, and favorite actions update the plain UI contract.
- Playground: assert play/pause, reset, recording mode, and drag callbacks update the intended
  state. Drive interaction state directly where possible rather than depending on real-time pointer
  timing.
- Lab: assert preset selection, Advanced disclosure, Custom transition, backdrop selection, and
  reset behavior.
- Prefer visible semantics; use test tags only for visually identical surfaces without stable text.

### Screenshot Tests

Use fixed artwork and an injected/fixed Playground progress value. Never wait for the production
clock in a screenshot test.

Glass Gallery screenshot tests and baselines belong to a dedicated `:sample:screenshot-tests`
module. That module targets Android host tests and JVM/Desktop only, depends on `:sample:shared` for
the UI under test, and reuses `:internal:screenshot-test` for the shared Roborazzi harness. Baselines
live under `sample/screenshot-tests/screenshots/android` and
`sample/screenshot-tests/screenshots/desktop`.

Do not add Roborazzi or screenshot-test dependencies directly to `:sample:shared`; it remains the
production multiplatform sample UI module for Android, Desktop, Apple, JS, and Wasm. Keep
`:haze-screenshot-tests` focused on library-level visual contracts rather than importing runnable
sample screens. The sample screenshot module owns its record, verify, and aggregate test tasks so
CI and local commands clearly identify which visual surface failed.

- Product: portrait and landscape hero states.
- Playground: opening, typography-crossing, depth-card, and prismatic beats, plus one manually
  displaced surface.
- Lab: Adaptive, Frosted, and Prism over representative diagnostic backgrounds.
- Record Android and Desktop baselines because their Glass rendering paths differ.

## Acceptance Criteria

- The old Glass credit-card and debug sample entries are replaced by the three approved entries.
- All three samples use one recognizable, deterministic Gallery visual system and no network data.
- Product exclusively demonstrates Adaptive optics and reads as a believable application surface.
- Playground loops seamlessly in 12 seconds, supports pause/reset, and allows every Glass surface
  to be dragged without losing its deterministic choreography.
- Playground never morphs or deforms a Glass shape.
- Lab demonstrates the five semantic presets, diagnostic backgrounds, grouped advanced controls,
  Custom behavior, and reliable reset.
- Portrait and landscape layouts preserve the intended bounded surface proportions.
- Recording mode produces clean, repeatable frames without timer-driven chrome changes.
- Glass Gallery screenshot code and baselines are owned by `:sample:screenshot-tests`, while
  `:sample:shared` remains free of Roborazzi wiring and `:haze-screenshot-tests` remains focused on
  library-level visual contracts.
- Pure, UI, Android screenshot, and Desktop screenshot coverage pass with fixed data and fixed
  animation progress.
