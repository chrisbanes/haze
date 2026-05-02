# Custom VisualEffect Background Layers Design

## Goal

Refocus the custom effect sample and docs so they clearly demonstrate Haze's core value: applying effects to transformed background source layers (`hazeSource`) via `hazeEffect(state = hazeState)`, with a lightweight but more expressive sparkle enhancement.

## User Intent

- Background source layering should be the primary story.
- Keep the enhancement simple, not a particle system.
- Rename the effect from `TintVisualEffect` to `SparkVisualEffect`.
- Keep this within the existing dedicated custom sample screen.

## Current Issues

1. The sample currently uses a single static fullscreen source image, which under-emphasizes background-layer composition.
2. The effect itself is a plain tint, which is correct but visually understated for a showcase sample.
3. Docs explain custom effect APIs well but do not lead with transformed background source layers as the primary usage story.

## Proposed Design

### 1) Sample architecture (single-screen evolution)

Keep `CustomVisualEffectSample` as one screen, but make two zones explicit:

- **Background source zone**: multiple transformed `hazeSource` layers.
- **Effect target zone**: centered rounded node with `hazeEffect(state = hazeState)` using the custom effect.

This preserves continuity and avoids adding another sample entry while making the source/effect pipeline obvious.

### 2) Background source layers

In `CustomVisualEffectSample`:

- Layer A: fullscreen image source with subtle animated transform (`graphicsLayer` scale + translation).
- Layer B: optional secondary source layer (image or shape) with independent rotation/translation.
- Optional Layer C: low-alpha gradient or text source to show compositing depth.

All layers are marked with `hazeSource(state = hazeState)` and are visibly transformed so users can observe how Haze samples background content.

### 3) Custom effect naming and behavior

Rename the effect type and builder usage:

- `TintVisualEffect` -> `SparkVisualEffect`
- `tintEffect(...)` -> `sparkEffect(...)`

`SparkVisualEffect` behavior:

- Keep existing tint fill behavior (configurable color + alpha).
- Add a simple **prismatic edge shimmer** (sparkle) drawn in `draw(context)`:
  - One animated phase value from Compose local state.
  - A low-alpha, diagonal shimmering band near edges.
  - No particles, no additional resource-heavy systems.

This keeps implementation small while making the sample feel more alive.

### 4) Controls

Keep existing controls and add minimal new toggles:

- Existing: tint color selection, tint alpha adjustment, `drawContentBehind`.
- New: `Animate background layers`, `Show secondary source layer`, `Sparkle enabled`.

The controls should make it easy to compare:

- Static vs transformed sources
- Plain tint vs shimmer-enhanced effect

### 5) Documentation direction

Update `docs/custom-effects.md` to foreground background mode usage:

- Lead with `hazeSource` + `hazeEffect(state = hazeState)` pattern.
- Add a "Background source layers" section with transformed source examples (`graphicsLayer`).
- Keep foreground mode as secondary context.
- Keep lifecycle API details, but tie examples back to background-layer rendering.
- Keep sample link and ensure it references the renamed `SparkVisualEffect` sample implementation.

## Data Flow

- `rememberHazeState()` is shared between all source layers and the effect node.
- Source-layer controls mutate Compose state affecting transforms/visibility only.
- Effect controls feed locals consumed by `SparkVisualEffect.update(context)`.
- `SparkVisualEffect` requests redraw when relevant local values change.

This separation keeps source dynamics and effect dynamics understandable independently.

## Error Handling / Stability

- Keep draw logic allocation-light and branch-light.
- Clamp any animated intensity/alpha values to safe ranges.
- Ensure shimmer drawing is skipped when disabled.
- Keep effect behavior deterministic across platforms (no platform-only APIs in common sample logic).

## Testing Strategy

### Manual behavior checks

- Confirm transformed background layers are visibly affecting the effect node output.
- Confirm toggles (`Animate background layers`, `Show secondary source layer`, `Sparkle enabled`) change behavior as expected.
- Confirm `drawContentBehind` toggle still functions correctly.

### Build/format verification

- `./gradlew :sample:shared:compileKotlinJvm`
- `./gradlew :sample:shared:spotlessCheck`

## Non-goals

- Particle systems or complex procedural sparkle engines.
- Adding additional sample screens for this change.
- Broad docs rewrite outside `docs/custom-effects.md`.

## Expected Outcome

Users looking at the custom effect sample and docs immediately see that Haze is about shaping and sampling transformed background source layers, while also getting a more exciting but still lightweight custom effect example via `SparkVisualEffect`.
