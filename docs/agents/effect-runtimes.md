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
- Log mutable internal runtime-property changes via `HazeLogger.d(TAG)` where they aid debugging.
- Make platform-specific `updateDelegate` functions return the new `Delegate` instance rather than
  mutating the property as a side-effect.

