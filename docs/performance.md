Real-time blurring is a non-trivial operation, especially for mobile devices, so developers are rightly worried about the performance impact of using something like Haze.

Haze tries to use the most performant mechanism possible on each platform, which can basically be simplified into 2: `RenderEffect` on Android, and using Skia's `ImageFilter`s directly on iOS and Desktop.

## Input Scale

Blur effects use adaptive input scaling by default. The common cross-platform policy uses the
resolved blur radius in physical pixels and expanded capture-layer pixel area to choose `1.0`,
`0.8`, or `0.5`. Stronger blur can hide more downsampling, while the area gate avoids reallocating
small layers for negligible savings. Progressive blur is capped at `0.8`; Glass remains unscaled by
default. Explicit `HazeSampling.FullResolution`, `Adaptive`, and `Fixed` values always win. You can
find configuration details [here](blur/usage.md#input-scale).

In terms of the performance benefit which scaling provides, it's fairly small. In our Android benchmark tests, using an `inputScale` set to `0.5` reduced the _cost of Haze_ by **5-20%**. You can read more about this below.

!!! abstract "Cost of Haze"
    Just to call out: the percentage that I mentioned is a reduction in the cost of Haze, not the total frame duration. Haze itself introduces a cost, which you can read more about below. The reduction in total frame duration duration will be in the region of 3-5%.

## Benchmarks

To quantify performance, we have a number of [Macrobenchmark tests](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) to measure Haze's effect on drawing performance on Android. We'll be using these on every major release to ensure that we do not unwittingly regress performance.

Anyway, in the words of Jerry Maguire, "Show Me The Money"...

We currently have 4 benchmark scenarios, each of them is one of the samples in the sample app, and picked to cover different things:

- **Scaffold**. The simple example, where the app bar and bottom navigation bar are blurred, with a scrollable list. This example uses rectangular haze areas.
- **Scaffold, with progressive**. Same as Scaffold, but using a progressive blur.
- **Images List**. Each item has its own `hazeSource` and `hazeBlur`. The list item content moves,
  but its effect does not move in local coordinates, so this primarily exercises multiple
  `RenderNode`s. This example uses rounded rectangle haze areas (that is, `clipPath`).
- **Credit Card**. A simple example where the user can drag the `hazeBlur` modifier. This tests how
  quickly Haze invalidates state and propagates it to the `RenderNode`s. This example uses rounded
  rectangle haze areas like Images List.

!!! abstract "Test setup"
    All of the tests were ran with 16 iterations on a Pixel 6, running the latest version of Android available.

As with all benchmark tests, the results are only true for the exact things being tested. Using Haze in your own applications may result in different performance characteristics, so it is wise to write your own performance tests to validate the impact to your apps. Benchmark tests will always have variability in them too, so don't take the numbers listed below as exact values. Look at them more as a guide.

### Adaptive default validation

The adaptive blur default was validated with three interleaved 16-iteration Scaffold pairs on a
Pixel 6 at 90Hz. Each pair compared explicit unscaled input with the adaptive default. Median P90
CPU duration improved from 9.817ms to 9.746ms, while Perfetto actual-frame duration improved from
11.889ms to 11.388ms (4.21%). CPU duration, actual-frame duration, and frame-overrun headroom
improved in every pair.

Representative progressive and masked pairs also improved actual-frame P90 by 5.76% and 6.53%
respectively. Runs with a different or mismatched refresh rate, or with invalid app navigation
state, were discarded and repeated before comparison.

The numbers listed below the P90 frame durations in milliseconds, which tend to be a good indicator of frames where a user interaction is happening (scrolling, etc). However, as these are the P90 values, these indicate the longest 10% frame durations, and thus are (probably) not indicitive of the performance which users see most of the time. It all depends on the distribution of the frame durations, but we're quickly getting into entry-level statistics, which is beyond what we're trying to document here.

#### Cost of Haze

We can also measure the rough cost of using Haze in the same samples. Here we've ran the same tests, with Haze being completely disabled:

| Test          | v1.x (disabled)  | v1.x      | Difference   |
| ------------- | ------------------| -----------| ------------ |
| Scaffold      | 7.5 ms            | 9.7 ms     | +29%         |
| Images List   | 6.6 ms            | 9.6 ms     | +45%         |
| Credit Card   | 6.6 ms            | 13.1 ms    | +98%         |

#### Cost of features

We can also measure the rough cost of using features, such as input scale, progressive and masking:

| Test                                      | P90 frame duration (ms)  | Difference (in Haze cost) |
| -------------                             | -------------------------| -----------|
| Scaffold                                  | 9.7 ms                   | -          |
| Scaffold (inputScale = 0.5)               | 9.6 ms                   | -5%        |
| Scaffold (masked)                         | 9.8 ms                   | +5%        |
| Scaffold (progressive)                    | 9.7 ms                   | 0%         |
| Scaffold (progressive, inputScale = 0.5)  | 9.4 ms                   | -14%       |

The values are all very close, with the differences easily being within a margin of error, so don't use these differences as exact values (especially with the variability that we mentioned above). I think there's two big take aways here though:

- Masking has a negligible effect on frame durations.
- Progessive has a negligible effect on frame durations, when using using our custom blur shader (Android SDK 34+, all other platforms).
- Input Scale has a small but positive effect on frame duration.

!!! example "Full results"
    For those interested, you can find the full results in this [spreadsheet](https://docs.google.com/spreadsheets/d/1wZ9pbX0HDIa08ITwYy7BrYYwOq2sX-HUyAMQlcb3dI4/edit?usp=sharing).

### Glass on Android

Glass has its own [performance guide](glass/performance.md), covering the modern Android benchmark
results and Perfetto interpretation.
