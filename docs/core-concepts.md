# Core Concepts

This page explains the fundamental concepts and patterns used throughout Haze, regardless of which effect you're using.

## HazeState

`HazeState` is a state holder that manages the rendering targets for visual effects. You create one using `rememberHazeState()`:

```kotlin
val hazeState = rememberHazeState()
```

This state is then shared between a `Modifier.hazeSource` (content to blur from) and one or more `Modifier.hazeEffect` (content to apply the blur to).

## Modifiers

Haze provides two main modifiers that work together:

### Modifier.hazeSource

Marks a composable as a source of content that can be blurred by effects elsewhere in the hierarchy.

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState)
) {
    // content
}
```

Parameters:

- **state**: The `HazeState` instance to share
- **zIndex**: Optional z-index for layering (when using overlapping effects)
- **key**: Optional identifier for filtering which areas to draw

### Modifier.hazeEffect

Applies a visual effect to a composable, drawing blurred content from areas marked with `Modifier.hazeSource`.

```kotlin
TopAppBar(
    modifier = Modifier.hazeEffect(state = hazeState) {
        blurEffect {
            style = HazeMaterials.thin()
        }
    }
)
```

The effect is configured inside the lambda block using effect-specific builders.

## HazeEffectScope

The lambda block parameter of `Modifier.hazeEffect` receives a `HazeEffectScope`, which provides common properties applicable to all effects:

### Common Properties

```kotlin
modifier = Modifier.hazeEffect(state = hazeState) {
    // Common properties
    inputScale = HazeInputScale.Auto
    drawContentBehind = true
    canDrawArea = { area -> true }
    
    // Effect-specific configuration
    blurEffect {
        // ...
    }
}
```

#### inputScale

Controls the resolution at which the effect source content is rendered. This is a performance optimization that allows the effect to be applied at a lower resolution before being scaled back up.

Options:

- `HazeInputScale.None`: No scaling (default)
- `HazeInputScale.Auto`: Automatic scaling with platform defaults
- `HazeInputScale.Fixed(value)`: Fixed scaling factor (0.0 to 1.0)

#### drawContentBehind

When `true`, the original source content is drawn before the effect is applied. When `false`, only the effect is drawn. Defaults to `true`.

#### canDrawArea

An optional filter function that controls which source areas should be included in the effect rendering. Useful for excluding specific layers:

```kotlin
canDrawArea = { area ->
    // return true to include, false to exclude
    area.key != "exclude_me"
}
```

## Foreground vs Background Effects

Haze supports two modes of applying effects:

### Background Effect (Most Common)

The effect blurs content from elsewhere (behind the composable). Requires both `hazeSource` and `hazeEffect`:

```kotlin
Box {
    LazyColumn(
        modifier = Modifier.hazeSource(state = hazeState)
    ) {
        // content
    }
    
    TopAppBar(
        modifier = Modifier.hazeEffect(state = hazeState) {
            blurEffect { /* ... */ }
        }
    )
}
```

### Foreground Effect

The effect blurs the content within the composable itself. Only requires `hazeEffect`:

```kotlin
Box(
    modifier = Modifier.hazeEffect {
        blurEffect { /* ... */ }
    }
) {
    // This content will be blurred
}
```

## Deep UI Hierarchies

When `HazeState` needs to be passed through many levels of nested composables, you can use a composition local instead:

```kotlin
val LocalHazeState = compositionLocalOf { HazeState() }

@Composable
fun HazeExample() {
    val hazeState = rememberHazeState()
    
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box {
            Background()
            Foreground()
        }
    }
}

@Composable
fun Foreground() {
    Text(
        modifier = Modifier.hazeEffect(state = LocalHazeState.current) {
            blurEffect { /* ... */ }
        }
    )
}
```

## Overlapping Effects

You can have multiple composables that both draw effects from the same source and serve as sources for other effects. This enables complex layering:

```kotlin
Box {
    val hazeState = rememberHazeState()
    
    Background(
        modifier = Modifier.hazeSource(hazeState, zIndex = 0f)
    )
    
    Card(
        modifier = Modifier
            .hazeSource(hazeState, zIndex = 1f)
            .hazeEffect(hazeState)
    )
    
    TopAppBar(
        modifier = Modifier
            .hazeSource(hazeState, zIndex = 2f)
            .hazeEffect(hazeState)
    )
}
```

In this example:
- Background is at zIndex 0
- Card at zIndex 1 draws the background through its effect
- TopAppBar at zIndex 2 draws both background and card through its effect

## Dialogs

When using effects with dialogs, the effect source must be marked before the dialog is shown:

```kotlin
val hazeState = rememberHazeState()
var showDialog by remember { mutableStateOf(false) }

Box {
    LazyColumn(
        modifier = Modifier.hazeSource(state = hazeState)
    ) {
        // background content
    }
    
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                modifier = Modifier.hazeEffect(state = hazeState) {
                    blurEffect { /* ... */ }
                }
            ) {
                // dialog content
            }
        }
    }
}
```

## Screenshot Testing

Haze supports screenshot testing with platform-specific considerations:

### Android with Robolectric

The `RenderEffect.createBlurEffect()` tile mode support was only recently added to Robolectric. For correct results at effect edges, run tests against SDK 35+:

```kotlin
@Config(sdk = [35])
class MyScreenshotTest {
    // tests
}
```

Without this, you may see strange results at effect boundaries on earlier SDK levels (though this doesn't affect real devices).

Other screenshot testing libraries may work but haven't been tested. YMMV.
