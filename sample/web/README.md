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

`sample` and `effect` together open a sample directly with normal navigation retained:

```text
https://chrisbanes.github.io/haze/<VERSION-WITH-EMBED-SUPPORT>/sample/wasm/index.html?sample=glass-product&effect=glass&theme=light
```

In-app Back returns to the effect's sample list, then the Blur/Glass chooser. These parameters select
the initial screen only: navigating does not update the URL or add browser Back/Forward history.
Omitting both parameters opens the ordinary chooser.

`embed=true` enables the standalone sample view with navigation hidden. Omitting `embed` or setting
`embed=false` retains navigation. Both selection modes require `sample` and `effect`. The browser
decodes the query string and uses the first value if a key appears more than once. Unknown keys are
ignored. Any invalid explicit embed value, incomplete selection, invalid sample or effect, unsupported
sample/effect pair, or invalid theme shows a compact error instead of selecting another demo.

| Parameter | Values | Default |
| --- | --- | --- |
| `sample` | A route from the catalog below | Required |
| `effect` | `blur` or `glass`, when the chosen sample supports it | Required |
| `theme` | `system`, `light`, or `dark` | `system` |

The theme works for both direct sample links and embeds. It changes the sample app theme only.
It does not synchronize with the parent page or recolor artwork that deliberately uses its own dark
scene design. Each embed starts with the ordinary defaults
for that sample. Its own controls remain available, including artwork paging, dialogs, drag gestures,
playback, reset, and recording controls; navigation back to the sample browser is hidden.

## Sample catalog

| Sample | `sample` | Effects |
| --- | --- | --- |
| Scaffold | `scaffold` | `blur`, `glass` |
| Scaffold (progressive blur) | `scaffold-progressive` | `blur`, `glass` |
| Scaffold (masked) | `scaffold-masked` | `blur`, `glass` |
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

Check a release's `sample/web/README.md` for embedding support, then pin that exact documentation
version in the article. A query string cannot add this feature to an older release.
