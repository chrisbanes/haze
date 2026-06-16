# Haze Source Transition Retention Design

## Context

Issue #983 reports a black flash on Android when a persistent `TopAppBar` uses
`Modifier.hazeEffect(state) { blurEffect { ... } }` while screen content below it is replaced
during navigation. The likely failure mode is a transient frame where the `hazeEffect` node remains
attached but its shared `HazeState` has no drawable `HazeArea`s because the outgoing source detached
before the incoming source has drawn.

Today, background `HazeEffectNode.draw()` only invokes the visual effect when `areas.isNotEmpty()`.
When areas are empty, the effect is skipped and only the app bar content is drawn. This means
`BlurVisualEffect.backgroundColor` and tint configuration cannot help during the transition gap,
because those properties are applied inside the blur delegate after `VisualEffect.draw()` is called.

## Goal

Preserve visual continuity across short source detach/attach gaps by drawing the previous valid blur
or glass output while new source content is unavailable.

The library should not keep stale source layers alive to solve this. `HazeSourceNode` should continue
to clear and release its `HazeArea` layer on detach. Retention belongs to the effect output, owned by
the visual effect delegate that rendered it.

## Non-Goals

- Do not change app placement guidance or require users to move `hazeSource`.
- Do not make missing `hazeSource` setup silently look correct forever.
- Do not retain source `HazeArea.contentLayer` after source detach.
- Do not introduce public API unless an internal hook cannot model the behavior cleanly.

## Proposed Behavior

For background effects, source-dependent visual effects should retain their last successfully
rendered output. If a later frame has no drawable source areas, the effect draws that retained output
instead of skipping the effect entirely.

The retained output is replaced every time the delegate renders from at least one valid source area.
It is cleared when any of these happen:

- the effect node detaches;
- the `HazeState` instance changes;
- the effect node size or layer size changes;
- the visual effect delegate changes;
- the current frame has source areas but none provide a valid drawable layer, and this persists past
  the transition frame.

The retention path should be short-lived by construction: it only exists after a previous valid
render, and it is invalidated by structural changes that make the retained output likely unrelated.

## Architecture

Add an internal capability for visual effects to say whether they can draw without source areas.
`HazeEffectNode` can then keep the current default for most effects while allowing blur and liquid
glass to opt into retained-output drawing.

The capability should be internal unless a public extension point is clearly needed. A possible shape:

```kotlin
internal interface RetainedOutputVisualEffect {
  fun canDrawRetainedOutput(context: VisualEffectContext): Boolean
}
```

`HazeEffectNode.draw()` background mode would draw the effect when either:

- `areas.isNotEmpty()`, or
- `visualEffect` supports retained output and reports that retained output is available.

`BlurVisualEffect` and `LiquidGlassVisualEffect` delegate the answer to their active delegates.

## Delegate Behavior

`RenderEffectBlurVisualEffectDelegate` is the primary blur implementation. It already owns the
scaled content layer used to draw the blurred output. It should avoid re-recording that layer from
empty input and instead draw the last recorded layer when retained output is available.

The retained layer is valid only after a successful draw from at least one valid source layer. A frame
with empty `context.areas` should not overwrite it.

`RenderScriptBlurVisualEffectDelegate` should follow the same policy for its output `contentLayer`:
continue drawing the last completed output while source areas are temporarily unavailable, but do not
start a new blur job from empty input.

`ScrimBlurVisualEffectDelegate` does not need retained output because it does not depend on source
layers. It can continue drawing its configured scrim normally when selected.

`RuntimeShaderLiquidGlassDelegate` should implement the same retained-output policy for LiquidGlass.
Today it uses `haze-liquidglass`'s `createAndDrawScaledContentLayer`, which creates a transient
content layer and releases it after drawing. To support retention, the runtime delegate should own a
reusable output layer, re-record it only when at least one valid source layer is available, and draw
that layer on empty-source transition frames. Empty-source frames must not replace the retained layer
with an empty transparent input.

`FallbackLiquidGlassDelegate` does not need retained output because it draws from style values rather
than source layers. It can continue rendering normally when selected.

## Data Flow

1. A source area draws and records its content into `HazeArea.contentLayer`.
2. A source-dependent visual effect renders from one or more valid source layers.
3. The delegate marks retained output as available.
4. During navigation, the outgoing source detaches and removes its `HazeArea` from `HazeState`.
5. The persistent effect node sees no areas but asks the visual effect if retained output is
   available.
6. The delegate draws the retained output for that frame.
7. When the incoming source draws, the delegate renders from the new source layer and replaces the
   retained output.

## Testing

Add focused tests around the state transition rather than a full Navigation reproduction:

- A background `hazeEffect` with a custom retained-output effect draws normally when areas exist.
- After a source is removed while the effect remains attached, `HazeEffectNode` still invokes the
  effect if retained output is available.
- A background effect that never had source areas keeps current behavior and is not drawn.
- `BlurVisualEffect` delegate tests verify that an empty-area frame does not overwrite the retained
  RenderEffect/RenderScript output.
- `LiquidGlassVisualEffect` delegate tests verify that an empty-area frame does not overwrite the
  retained runtime shader output.
- Size or state changes clear retained output.

Android screenshot coverage can be added later if the unit-level behavior is insufficient to catch
the transition path.

## Risks

The main risk is showing stale content longer than intended. The clear rules above limit this to
short detach/attach gaps and structural continuity. If implementation evidence shows that "source
areas exist but no area has a valid content layer yet" also occurs during normal navigation, the same
retention policy should cover that frame without replacing the retained output.

Another risk is making custom visual effects accidentally participate in retention. Keeping the hook
internal and opt-in avoids changing default behavior.
