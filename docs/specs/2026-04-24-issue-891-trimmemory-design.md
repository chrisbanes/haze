# Fix: "drawRenderNode called on a context with no surface" crash (#891)

## Context

When a Haze-using app is backgrounded and Android issues `TRIM_MEMORY_COMPLETE` (or `onLowMemory`), the RenderScript blur delegate retains its `RenderScriptContext` and its cached `Surface`. When the app returns to the foreground, the next draw attempts to lock/unlock a Surface whose underlying hardware context has been destroyed, causing the fatal `drawRenderNode called on a context with no surface!` crash on Android 10.

## Design

### 1. `TrimMemoryLevel` enum (commonMain)

A severity-ordered enum mapping Android `onTrimMemory(int)` levels to a platform-agnostic API:

```kotlin
@Suppress("unused")
enum class TrimMemoryLevel {
  UI_HIDDEN,
  BACKGROUND,
  MODERATE,
  COMPLETE,
}
```

`COMPLETE` is the highest severity and is also emitted when the platform calls `onLowMemory()`.

### 2. `VisualEffect.onTrimMemory()`

A new default method on `VisualEffect`:

```kotlin
public fun onTrimMemory(level: TrimMemoryLevel): Unit = Unit
```

Effects can override this to release heavy resources in response to memory pressure.

### 3. Platform callback registration (`haze` module)

An internal `expect`/`actual` helper registers/unregisters system trim-memory callbacks for the `HazeEffectNode` lifecycle:

```kotlin
// commonMain
internal expect fun registerTrimMemoryCallback(
  context: PlatformContext,
  callback: (TrimMemoryLevel) -> Unit,
): Cancellable

// androidMain
internal actual fun registerTrimMemoryCallback(...): Cancellable =
  context.registerComponentCallbacks(object : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
      callback(level.toTrimMemoryLevel())
    }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() = callback(TrimMemoryLevel.COMPLETE)
  })
```

`HazeEffectNode.onAttach` registers the callback; `onDetach` cancels it. On non-Android platforms the actual is a no-op.

### 4. `RenderScriptBlurVisualEffectDelegate`

- **`onTrimMemory`** (delegated from `BlurVisualEffect`):
  - Releases `renderScriptContext` (which destroys the RenderScript allocations, bitmaps, and their stale `Surface`).
  - Cancels `currentJob`.

- **Defensive try-catch in `updateSurface`**:
  Wrap `rs.inputSurface.drawGraphicsLayer(...)` in a `try/catch (IllegalStateException)`. If the surface is already destroyed, release the context, null it out, and return early so the next draw creates a fresh context. This is a last-resort guard for any races between the trim signal and the actual draw.

## Files changed

- `haze/src/commonMain/kotlin/dev/chrisbanes/haze/TrimMemoryLevel.kt` *(new)*
- `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt`
- `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
- `haze/src/androidMain/kotlin/dev/chrisbanes/haze/TrimMemoryCallback.kt` *(new)*
- `haze/src/nonAndroidMain/…/TrimMemoryCallback.kt` *(new, no-op)*
- `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderScriptBlurVisualEffectDelegate.kt`

## Backwards compatibility

`onTrimMemory` is a default interface method with an empty body; existing custom `VisualEffect` implementations require no changes.

## Testing

Manual reproduction steps from the issue:
1. Navigate to a page using Haze
2. Press home to background the app
3. `adb shell am send-trim-memory <pkg> COMPLETE`
4. Return to the app
5. Expect: no crash; blur may have a one-frame stutter while rebuilding the context, but recovers gracefully.
