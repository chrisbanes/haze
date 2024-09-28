The 0.9 version contains a full re-write (actually more of a refactor) of Haze. It uses Compose Mutliplatform 1.7.0 (currently pre-release), meaning that we now have access to some new and exciting APIs.

!!! warning "Should I use v0.9 pre-release?"

    I think that all depends on your appetite for prerelease software. Haze itself is now much simpler than ever before, and most of the complexity is now in GraphicsLayer. On Android, we're using the latest Jetpack Compose 1.7.x stable versions. If you're only using Haze on Android, I would feel confident using it.

    On other platforms, Compose Multiplatform is at 1.7.0-beta, so not stable, but getting there.

## Changes

Here's a list of known changes in v0.9. There may be others, so please file issues if you encounter other unexpected changes.

### Functional changes

#### Haze no longer clips background content

This means that blurred areas may appear less blurry at first, as you're seeing the original content behind, and then the blurred content on top. This clipping has been removed, as the clipped areas can't take part in translations, scales, etc.

**Change:** Use the `backgroundColor` property on `HazeStyle` to set an appropriate opaque background color. The `HazeMaterial`s automatically use the Material3 Surface color, so you may have nothing to do here.

#### Blurred effect may look different

As we're now using a common implementation on all platforms, the Skia-backed platforms now have a completely different implementation. Haze has also tried to maintain consistency across the platforms, so the differences should be small. However the differences will likely trigger screenshot tests to fail.

**Change:** This is working as intended for small changes, so go ahead and update your screenshot golden images. If you feel that the differences are too large, please file an issue with examples.

### API changes

#### Default style functionality on Modifier.haze has been removed

- **What:** In previous versions, there was a `style` parameter on `Modifier.haze`, which has been removed in v0.9.
- **Migration:** Move all styling to `Modifier.hazeChild` calls.
- **Why:** Previously `Modifier.haze` to be the source of truth for styling, as it was responsible for all drawing. With the changes listed below, drawing is now the responsibility of the children themselves, therefore it makes little sense to invert the responsibility.

## Why have these changes been made?

Below we'll go through some of the background of the changes in v0.9. You don't need to know this stuff, but it might be interesting for some.

### GraphicsLayers

For Haze, the most exciting APIs we now have access are the new GraphicsLayer APIs. These are very similar to RenderNodes in Android, which Haze has always used on Android. In a very short summary, GraphicsLayers give us a common API to use on all platforms.

This has resulted in Haze now having a single implementation across all platforms now, re-using the old Android implementation. This should help minimize platform differences, and bugs.

### New pipeline

In v0.7 and older, Haze is all 'smoke and mirrors'. It draws all of the blurred areas in the `haze` layout node. The `hazeChild` nodes just update the size, shape, etc, which the `haze` modifier reads, to know where to draw.

With the adoption of GraphicsLayers, we now have a way to pass 'drawn' content around, meaning that we are no longer bound by the restraints of before. v0.9 contains a re-written drawing pipeline, where the blurred content is drawn by the `hazeChild`, not the parent. The parent `haze` is now only responsible for drawing the background content into a graphics layer, and putting it somewhere for the children to access.

This fixes a number of long-known issues on Haze, where are all caused by the fact that the blurred area wasn't drawn by the child.
