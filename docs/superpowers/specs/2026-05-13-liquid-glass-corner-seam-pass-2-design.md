# Liquid Glass Corner Seam Pass 2 Design

## Summary

Apply a broader rounded-corner direction-field blend in the Liquid Glass refraction shader to further reduce the remaining corner seam after pass 1. The pass keeps the exact shape mask, signed distance, displacement magnitude, and specular normal behavior unchanged, but replaces the pass-1 inside-corner-only smoothing with a corner-band blend that still prioritizes a rectangular read over fully radial corners.

## Problem

Pass 1 softened the hard inside-corner axis switch in `gradSdRoundedRect(...)`, but the regenerated screenshots still show a visible corner seam. That suggests the issue is not limited to a narrow bisector transition. The rounded-corner field still behaves too much like separate edge domains that meet inside the corner, leaving a visible directional discontinuity.

## Goals

- Further reduce the visible corner seam beyond pass 1.
- Preserve the rectangular read of the rounded rectangle.
- Keep straight-edge behavior unchanged.
- Keep shape distance, edge mask, displacement magnitude, and specular normal logic unchanged.

## Non-Goals

- Reworking the displacement magnitude path.
- Reworking the sampled-height specular normal path.
- Changing the rounded-rectangle SDF or edge mask.
- Making the corner field fully radial everywhere.

## Approach Options

### Option 1: Corner-band blend from edge-aligned to arc-aligned direction

Inside the rounded-corner region, blend from the existing edge-aligned direction toward the outer-arc direction as the sample moves deeper into the corner. Keep the blend confined to the corner band so straight edges and the flat interior remain unchanged.

Pros:
- Best balance between seam reduction and silhouette preservation.
- Keeps the change local to the rounded-corner region.
- Straight edges stay stable.

Cons:
- More behavior change than pass 1.
- Needs careful band shaping to avoid corners feeling too soft.

### Option 2: Full radial direction field in rounded corners

Use a radial direction throughout the rounded-corner region.

Pros:
- Most likely to remove the seam aggressively.
- Conceptually simple.

Cons:
- Highest risk of reintroducing a pill-like look.
- Larger change to the current rectangular visual character.

### Option 3: Wider pass-1 bisector smoothing only

Keep the same pass-1 structure but widen the smoothing window further away from the bisector.

Pros:
- Smallest extension of the current code.
- Lowest implementation risk.

Cons:
- Least likely to solve the remaining seam.
- Does not address the broader corner-region directional mismatch.

## Decision

Use Option 1. It gives a stronger seam-reduction pass than pass 1 while still preserving the rectangular read as the primary visual constraint.

## Detailed Design

Update `gradSdRoundedRect(...)` so the rounded-corner region uses a two-stage direction choice:

- Keep the current straight-edge behavior outside the rounded-corner region.
- In the rounded-corner region, compute an edge-aligned direction that still favors the dominant axis near the straight edges.
- Compute an arc-aligned direction from the rounded-corner geometry, matching the outer-corner direction already used when the sample is outside the corner box.
- Compute a corner-progress blend factor that increases as the sample moves deeper into the rounded corner and away from the straight-edge-dominant part of the field.
- Mix from the edge-aligned direction toward the arc-aligned direction using that corner-progress factor.
- Normalize the mixed direction safely before applying `coordSign`.

The blend should start late enough that corners still read as rectangular where the edge is visually dominant, then transition smoothly enough near the arc to weaken the seam that remains after pass 1.

## Data Flow Impact

- `surfaceHeight(...)` remains the source of displacement magnitude.
- `surfaceGradient(...)` remains the source of the specular normal.
- `gradSdRoundedRect(...)` remains the analytical source of refraction direction.
- `main(...)` keeps the current center bias and displacement composition.

## Error Handling And Stability

- Preserve the existing `safeNormalize(...)` behavior.
- Preserve the existing `axisSafeSign(...)` behavior.
- Avoid introducing a zero-vector path when blending edge-aligned and arc-aligned directions.
- Keep the existing exact-center fallback behavior in `main(...)` unchanged.

## Testing

- Update the shader-string regression so it asserts the broader corner-band blend markers instead of only the pass-1 inside-corner smoothing markers.
- Re-run the focused shader test.
- Re-run the targeted Android, JVM, and iOS compile checks.
- Re-record the seam-focused Android and desktop screenshots.

## Verification Criteria

- Shader test passes.
- Compile checks pass.
- Straight edges remain visually unchanged.
- The rounded corners still read rectangular rather than pill-like.
- The next screenshot review shows the corner seam is weaker than pass 1.
