# UIKit interop on iOS

Haze captures pixels that Compose draws into its graphics layer. Content hosted by
`UIKitView` or `UIKitViewController` is rendered by UIKit, so it is not part of that
captured Compose layer by default.

## Overlay placement

`UIKitInteropProperties.placedAsOverlay` changes where UIKit is composited relative to
Compose's Metal canvas; it does not make UIKit pixels available to Haze.

| `placedAsOverlay` | Placement | Effect on Haze |
| --- | --- | --- |
| `false` (default) | UIKit stays below the Compose Metal canvas. Compose draws a transparent cut-out where the UIKit view appears. | The UIKit pixels are outside Haze's captured source. |
| `true` | UIKit is placed above the Compose Metal canvas. | The UIKit view covers any overlapping Compose or Haze content; its pixels are still outside Haze's captured source. |

This follows Compose Multiplatform's [overlay placement guidance](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html#overlay-placement-for-interop-views)
and the [`UIKitInteropProperties` API source](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/viewinterop/UIKitInteropProperties.ios.kt).

`interactionMode` controls touch handling and `isNativeAccessibilityEnabled` controls
native accessibility exposure. Both are independent of pixel capture and do not change
whether Haze can use UIKit content as a source.

## Support

| UIKit content | Can Haze capture it? | Requirement |
| --- | --- | --- |
| `WKWebView` in `UIKitView` | No | Use a renderer that draws the visual content into the Compose layer before Haze captures it. |
| `AVPlayerLayer` hosted by `UIKitView` | No | Use a video rendering path that draws into the Compose layer before Haze captures it. |
| Other `UIKitView` or `UIKitViewController` content | No | Visual content must render into the Compose layer before it can be captured by Haze. |

Use an iOS UIKit interop view when native composition or interaction is the goal. To blur
or glass its visual content with Haze, choose a rendering path whose pixels are drawn into
the same Compose layer as `hazeSource`.
