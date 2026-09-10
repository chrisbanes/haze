# Embed a sample

You can embed one Haze Wasm sample in an article with an iframe. Choose a released documentation
version that includes sample embedding, then replace `<VERSION-WITH-EMBED-SUPPORT>` below with that
version. Do not use a moving alias, snapshot, or `latest` for a published article.

```html
<iframe
  src="https://chrisbanes.github.io/haze/<VERSION-WITH-EMBED-SUPPORT>/sample/wasm/index.html?embed=true&amp;sample=scaffold&amp;effect=glass&amp;theme=light"
  title="Haze Glass Scaffold sample"
  loading="lazy"
  style="width: 100%; height: 560px; border: 0;"
></iframe>
```

The iframe width follows its container. Change the `height` value to suit the sample and surrounding
copy; the sample scrolls inside that fixed frame. Browser lazy loading is a proximity hint, so it does
not guarantee an exact visibility boundary or pause the sample when it is off screen.

Use the same URL as a normal link when readers need to open the demo directly:

```text
https://chrisbanes.github.io/haze/<VERSION-WITH-EMBED-SUPPORT>/sample/wasm/index.html?embed=true&sample=scaffold&effect=glass&theme=light
```

## URL parameters

`embed=true` enables the standalone sample view. It requires both `sample` and `effect`. The browser
decodes the query string and uses the first value if a key appears more than once. Unknown keys are
ignored. Any missing or invalid explicit embed value, sample, effect, unsupported sample/effect pair,
or theme shows a compact error instead of selecting another demo.

| Parameter | Values | Default |
| --- | --- | --- |
| `sample` | A route from the catalog below | Required |
| `effect` | `blur` or `glass`, when the chosen sample supports it | Required |
| `theme` | `system`, `light`, or `dark` | `system` |

The theme changes the sample app theme only. It does not synchronize with the parent page or recolor
artwork that deliberately uses its own dark scene design. Each embed starts with the ordinary defaults
for that sample. Its own controls remain available, including artwork paging, dialogs, drag gestures,
playback, reset, and recording controls; navigation back to the sample browser is hidden.

## Sample catalog

| Sample | `sample` | Effects |
| --- | --- | --- |
| Scaffold | `scaffold` | `blur`, `glass` |
| Scaffold (adaptive) | `scaffold-adaptive` | `blur`, `glass` |
| Scaffold (quality) | `scaffold-quality` | `blur`, `glass` |
| Scaffold (balanced) | `scaffold-balanced` | `blur`, `glass` |
| Scaffold (performance) | `scaffold-performance` | `blur`, `glass` |
| Scaffold (progressive blur) | `scaffold-progressive` | `blur`, `glass` |
| Scaffold (progressive blur, quality) | `scaffold-progressive-quality` | `blur`, `glass` |
| Scaffold (masked) | `scaffold-masked` | `blur`, `glass` |
| Scaffold (masked, quality) | `scaffold-masked-quality` | `blur`, `glass` |
| Credit Card | `credit-card` | `blur`, `glass` |
| Images List | `images-list` | `blur`, `glass` |
| List over Image | `list-over-image` | `blur`, `glass` |
| Dialog | `dialog` | `blur`, `glass` |
| Popup | `popup` | `blur`, `glass` |
| Materials | `materials` | `blur`, `glass` |
| List with Sticky Headers | `list-with-sticky-headers` | `blur`, `glass` |
| Bottom Sheet | `bottom-sheet` | `blur`, `glass` |
| Content Blurring | `content-blurring` | `blur`, `glass` |
| Custom VisualEffect | `custom-visual-effect` | `blur` |
| Layer Transformations | `layer-transformations` | `blur`, `glass` |
| Glass — Product | `glass-product` | `glass` |
| Glass — Playground | `glass-playground` | `glass` |
| Glass — Lab | `glass-lab` | `glass` |

Find the first release that supports embedding in its release notes or documentation navigation, and
pin that exact version in the article. A query string cannot add this feature to an older release.
