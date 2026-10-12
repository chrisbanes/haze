# Effect runtimes

When authoring or modifying built-in effect runtimes (for example, `BlurVisualEffect` or the
internal Glass runtime), follow these conventions:

- Annotate the class with `@Stable` for Compose skippability.
- Use a `needsDelegateSelection` flag to defer delegate creation from `update()` to `draw()`,
  avoiding work on frames where no draw occurs.
- Expose a `Local*Style` composition local (e.g., `LocalGlassStyle`). Add a matching
  `*Defaults.style` property only when it represents behavior not already covered by individual
  defaults or named built-in styles.
- Resolve replayable styles in this order: defaults → composition local → explicit style.
- Guard the delegate property setter with `isAttached` to prevent calling `attach()`/`detach()`
  before the effect is node-attached.
- Log runtime-property changes and per-draw or per-update messages through `HazeLogger.d(TAG)`. Its
  `enabled` guard is inlined, so a capturing message allocates nothing while logging is off.
- Make platform-specific `updateDelegate` functions return the new `Delegate` instance rather than
  mutating the property as a side-effect.
- Expose delegate capabilities (content drawn behind, retained output, backdrop support,
  runtime-shader recovery) as members of the runtime's `Delegate` interface with no-op defaults.
  The runtime calls them on `delegate` and does not type-check delegates anywhere except delegate
  selection (`updateDelegate`).
- A delegate that keeps a `GraphicsLayer` across draws (`RetainedGraphicsLayer`) sets every layer
  property it uses on every draw, because a retained layer keeps what the previous draw set. It
  releases the layer when a draw stops using it and on detach, trim memory (`MODERATE` and above)
  and retained-output clears.
