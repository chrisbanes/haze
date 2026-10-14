# UIKit interop on iOS

Haze captures pixels that Compose draws. Content hosted by `UIKitView` or `UIKitViewController`
is rendered by UIKit, so Haze cannot capture it.

## Overlay placement

`UIKitInteropProperties.placedAsOverlay` changes where UIKit is composited relative to
Compose's Metal canvas; it does not make UIKit pixels available to Haze.

| `placedAsOverlay` | Placement | Effect on Haze |
| --- | --- | --- |
| `false` (default) | UIKit stays below the Compose Metal canvas. Compose draws a transparent cut-out where the UIKit view appears. | Haze cannot capture the UIKit view. |
| `true` | UIKit is placed above the Compose Metal canvas. | The UIKit view covers overlapping Compose and Haze content. Haze still cannot capture it. |

See Compose Multiplatform's [overlay placement guidance](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html#overlay-placement-for-interop-views).
The other `UIKitInteropProperties`, `interactionMode` and `isNativeAccessibilityEnabled`, do not
affect capture either.

## Support

| UIKit content | Can Haze capture it? | Requirement |
| --- | --- | --- |
| `WKWebView` in `UIKitView` | No | Use a renderer that draws into Compose. |
| `AVPlayerLayer` hosted by `UIKitView` | No | Use a video renderer that draws into Compose. |
| Other `UIKitView` or `UIKitViewController` content | No | Render the content in Compose instead. |

To apply Blur or Glass over this content, use a rendering path that draws into Compose and mark
it with `hazeSource`.
