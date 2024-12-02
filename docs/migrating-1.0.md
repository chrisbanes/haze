## Changes

Here's a list of known changes in v1.0.x. There may be others, so please file issues if you encounter other unexpected changes.

### Functional changes

#### Haze no longer clips background content

This means that blurred areas may appear less blurry at first, as you're seeing the original content behind, and then the blurred content on top. This clipping has been removed, as the clipped areas can't take part in translations, scales, etc.

**Change:** Use the `backgroundColor` property on `HazeStyle` to set an appropriate opaque background color. The `HazeMaterial`s automatically use the Material3 Surface color, so you may have nothing to do here.

#### Blurred effect may look different

As we're now using a common implementation on all platforms, the Skia-backed platforms now have a completely different implementation. Haze has also tried to maintain consistency across the platforms, so the differences should be small. However the differences will likely trigger screenshot tests to fail.

**Change:** This is working as intended for small changes, so go ahead and update your screenshot golden images. If you feel that the differences are too large, please file an issue with examples.

### API changes

#### More styling parameters

- **What:** We now have more styling parameters, including a mask `Brush`, and alpha parameter.
- **Why:** More is more, right?

#### 🆕 HazeChildScope

- **What:** We now have a parameter on `Modifier.hazeContent` which allow you to provide a lambda block for controlling all of Haze's styling parameters. It is similar to concept to `Modifier.graphicsLayer { ... }`. See [here](usage.md#hazechildscope) for more information.
- **Why:** This has been primarily added to aid animating Haze's styling parameters, in a performant way.

#### Default style functionality on Modifier.haze has been moved

- **What:** In previous versions, there was a `style` parameter on `Modifier.haze`, which has been moved in v1.0.
- **Migration:** Use the new [LocalHazeStyle](../api/haze/dev.chrisbanes.haze/-local-haze-style.html) composition local instead.
- **Why:** Composition locals are used throughout styling frameworks, so this is a better API going forward.

#### HazeArea has been removed

- **What:** The `HazeArea` class has been removed
- **Migration:** None. This was mostly an internal API so if you did have a use case for this, let me know via an issue.
- **Why:** HazeArea instances were how we updated `Modifier.haze` instances about individual children (back-writes). With the changes listed below, drawing is now the responsibility of the children themselves, therefore there is no need to communicate this state back up.

## Why have these changes been made?

Below we'll go through some of the background of the changes in v1.0. You don't need to know this stuff, but it might be interesting for some.

### GraphicsLayers

For Haze, the most exciting APIs we now have access are the new GraphicsLayer APIs. These are very similar to RenderNodes in Android, which Haze has always used on Android. In a very short summary, GraphicsLayers give us a common API to use on all platforms.

This has resulted in Haze now having a single implementation across all platforms now, re-using the old Android implementation. This should help minimize platform differences, and bugs.

### New pipeline

In v0.7 and older, Haze is all 'smoke and mirrors'. It draws all of the blurred areas in the `hazeBackground` layout node. The `hazeContent` nodes just update the size, shape, etc, which the `hazeBackground` modifier reads, to know where to draw.

With the adoption of GraphicsLayers, we now have a way to pass 'drawn' content around, meaning that we are no longer bound by the restraints of before. v1.0 contains a re-written drawing pipeline, where the blurred content is drawn by the `hazeContent`, not the parent. The parent `hazeBackground` is now only responsible for drawing the background content into a graphics layer, and putting it somewhere for the children to access.

This fixes a number of long-known issues on Haze, where all were caused by the fact that the blurred area wasn't drawn by the child.
