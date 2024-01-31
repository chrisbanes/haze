Real-time blurring is a non-trivial operation, especially for mobile devices, so developers are rightly worried about the performance impact of using something like Haze.

Haze tries to use the most performant mechanism possible on each platform, which can basically be simplified into 2: `RenderNode` and `RenderEffect` on Android, and using Skia's `ImageFilter`s directly on iOS and Desktop.

## Android

On Android, Haze actually has two implementations:

- On API ~~31~~ 32+ we can use [RenderNode](https://developer.android.com/reference/android/graphics/RenderNode) and [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) to achieve real time blurring (and much more).
- On older platforms, we have a fallback mechanism did uses a translucent scrim (overlay) instead. This is also what is used for software backed canvases, such as [Android Studio previews](https://developer.android.com/jetpack/compose/tooling/previews), Robolectric, [Paparrazi](https://github.com/cashapp/paparazzi), etc.

We'll ignore the scrim implementation here, as that is fairly simple and unlikely to cause any performance issues.

### Things to watch out for

First let's highlight some things to look out for when using Haze.

#### Non-`RectangleShape`s

The `shape` parameter on `hazeChild` is very useful for content which isn't rectangular, but it does come at a cost. To support this, Haze needs to extract an `Outline` and then a `Path` from the shape, so we can clip the resulting blurred content to the provided shape. We actually need to call `clipPath` twice for each area, first to clip the blurred content, and second to `clipOutRect` the original content (otherwise you see the content behind blurred areas). Path clipping is notoriously slow as it's a complex operation, which inevitably makes rendering slower.

!!! info
    Haze still needs to clip content if you use a `RectangleShape`. The difference is that we can use `clipRect` instead, which is a lot faster.

This warning is not to stop you using different kinds of shapes, it's just to highlight that there are tradeoffs in terms of performance.

### Benchmarks

To quantify performance, in 0.5.0 we've added a number of [Macrobenchmark tests](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) to measure Haze's effect on drawing performance. We'll be using these on every major release to ensure that we do not unwittingly regress performance.

Anyway, in the words of Jerry Maguire, "Show Me The Money"...

We currently have 3 benchmark scenarios, each of them is one of the samples in the sample app, and picked to cover different things:

- **Scaffold**. The simple example, where the app bar and bottom navigation bar are blurred, with a scrollable list. This example uses rectangular haze areas.
- **Images List**. Each item in the list has it's own `haze` and `hazeChild`. As each item has it's own `haze`, the internal haze state does not change all that much (the list item content moves, but the `hazeChild` doesn't in terms of local coordinates). This is more about multiple testing `RenderNode`s. This example uses rounded rectangle haze areas (i.e. we use `clipPath`).
- **Credit Card**. A simple example, where the user can drag the `hazeChild`. This tests how fast Haze's internal state invalidates and propogates to the `RenderNode`s. This example uses rounded rectangle haze areas like 'Images List'.

!!! abstract "Test setup"
    All of the tests were ran with 10 iterations on a Pixel 6, running the latest version of Android available. All of the numbers below are the P50 (median) frame duration time in milliseconds.

As with all benchmark tests, the results are only true for the exact things being tested. Using Haze in your own applications may result in different performance characteristics, so it is wise to write your own performance tests to validate the impact to your apps.

#### 0.4.5 vs 0.5.0

Haze 0.5.0 contains a number of performance improvements, especially on Android. In fact, measuring this was the whole reason why these tests were written. You can see that Haze 0.5.0 outperforms 0.4.5 in both of the more complex scenarios. This is not a surprise as these both trigger a lot of internal state updates, and the bulk of the optimizations were designed to re-use and skip updates where possible.

The Scaffold result of `+0.2` ms is likely in the error of margin for this of kind of testing, but something to keep an eye on.

| Test          | 0.4.5      | 0.5.0      | Difference   |
| ------------- | ---------- | -----------| ------------ |
| Scaffold      | 6.6 ms     | 6.8 ms     | :material-trending-up: +3%     |
| Images List   | 18.4 ms    | 6.3 ms     | :material-trending-down: -66%  |
| Credit Card   | 7.5 ms     | 6.6 ms     | :material-trending-down: -12%  |

#### 0.5.0 vs baseline

We can also measure the rough cost of using Haze in the same samples. Here we've ran the same tests, but with Haze being disabled:

| Test          | 0.5.0 (disabled)  | 0.5.0      | Difference   |
| ------------- | ------------------| -----------| ------------ |
| Scaffold      | 5.3 ms            | 6.8 ms     | +28%         |
| Images List   | 4.8 ms            | 6.3 ms     | +31%         |
| Credit Card   | 5.1 ms            | 6.6 ms     | +29%         |

!!! example "Full results"
    For those interested, you can find the full results in this [spreadsheet](https://docs.google.com/spreadsheets/d/1wZ9pbX0HDIa08ITwYy7BrYYwOq2sX-HUyAMQlcb3dI4/edit?usp=sharing).

## Skia-backed platforms (iOS and Desktop)

TODO