# Liquid Glass Corner Seam Pass 1 Design

## Summary

Apply a minimal, local smoothing change to the Liquid Glass refraction direction field to reduce the remaining corner seam without changing the rectangular silhouette. The pass only changes the inside-corner branch of `gradSdRoundedRect(...)` and leaves the shape mask, displacement magnitude, outer arc behavior, and sampled specular normal untouched.

## Problem

The current direction-only refraction experiment improved the visible corner seam, but a faint seam remains. Root-cause inspection indicates that `gradSdRoundedRect(...)` still switches direction piecewise inside the rounded-corner region before the field reaches the diagonal arc. That hard axis pick can leave a weakened bisector kink in the refraction field.

## Goals

- Further reduce the visible corner seam.
- Preserve the current rectangular silhouette.
- Keep straight-edge behavior unchanged.
- Limit risk by changing only the refraction direction logic.

## Non-Goals

- Reworking the displacement magnitude path.
- Reworking the sampled-height specular normal path.
- Changing the edge mask or shape distance logic.
- Solving all remaining seam artifacts in this pass.

## Approach Options

### Option 1: Minimal local smoothing inside the corner branch

Replace the hard `step(cornerCoord.y, cornerCoord.x)` axis selection in the inside-corner branch of `gradSdRoundedRect(...)` with a narrow smooth blend between horizontal and vertical directions. The blend only activates when `cornerCoord.x` and `cornerCoord.y` are close enough that the current branch would otherwise snap across the bisector.

Pros:
- Smallest code change.
- Lowest silhouette risk.
- Straight edges and outer corner arc stay as they are.

Cons:
- May only soften, not eliminate, the seam.

### Option 2: Broader corner-region blend

Blend from edge-aligned direction to radial direction through a wider rounded-corner band.

Pros:
- More likely to hide the seam.

Cons:
- Higher chance of corners feeling too rounded.
- Larger behavioral change than needed for the next pass.

## Decision

Use Option 1 first. If the seam is still too visible after verification, follow with a second pass that explores Option 2 separately.

## Detailed Design

Update only the inside-corner branch of `gradSdRoundedRect(...)`.

- Keep the existing outside-corner behavior that normalizes `max(cornerCoord, 0.0)`.
- In the inside branch, compute a small transition factor from the difference between `cornerCoord.x` and `cornerCoord.y`.
- Use that factor to smoothly mix between the horizontal and vertical unit directions instead of selecting one with `step(...)`.
- Normalize or otherwise keep the resulting direction stable before applying `coordSign`.

The transition band should stay narrow so that the field changes only near the bisector where the seam originates.

## Data Flow Impact

- `surfaceHeight(...)` remains the source of displacement magnitude.
- `surfaceGradient(...)` remains the source of the specular normal.
- `gradSdRoundedRect(...)` remains the analytical input for refraction direction.
- `main(...)` keeps the current `refractionDir` composition and center bias.

## Error Handling And Stability

- Preserve the existing `safeNormalize(...)` behavior.
- Preserve the existing `axisSafeSign(...)` behavior.
- Avoid introducing a new zero-vector path in the inside-corner blend.
- Keep the exact-center fallback behavior unchanged.

## Testing

- Extend the shader-string regression to assert that the hard inside-corner `step(...)` branch has been replaced by a smoothing path.
- Re-run the focused shader test.
- Re-run the existing compile checks that cover Android, JVM, and iOS shader compilation.

## Verification Criteria

- Shader test passes.
- Compile checks pass.
- The refraction field changes only in the local inside-corner branch.
- The next screenshot review shows the seam is weaker without obvious pill-like corners.
