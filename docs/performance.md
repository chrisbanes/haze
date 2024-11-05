Real-time blurring is a non-trivial operation, especially for mobile devices, so developers are rightly worried about the performance impact of using something like Haze.

Haze tries to use the most performant mechanism possible on each platform, which can basically be simplified into 2: `RenderNode` and `RenderEffect` on Android, and using Skia's `ImageFilter`s directly on iOS and Desktop.

## Benchmarks

To quantify performance, we have a number of [Macrobenchmark tests](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) to measure Haze's effect on drawing performance on Android. We'll be using these on every major release to ensure that we do not unwittingly regress performance.

Anyway, in the words of Jerry Maguire, "Show Me The Money"...

We currently have 4 benchmark scenarios, each of them is one of the samples in the sample app, and picked to cover different things:

- **Scaffold**. The simple example, where the app bar and bottom navigation bar are blurred, with a scrollable list. This example uses rectangular haze areas.
- **Scaffold, with progressive**. Same as Scaffold, but using a progressive blur.
- **Images List**. Each item in the list has it's own `haze` and `hazeChild`. As each item has it's own `haze`, the internal haze state does not change all that much (the list item content moves, but the `hazeChild` doesn't in terms of local coordinates). This is more about multiple testing `RenderNode`s. This example uses rounded rectangle haze areas (i.e. we use `clipPath`).
- **Credit Card**. A simple example, where the user can drag the `hazeChild`. This tests how fast Haze's internal state invalidates and propogates to the `RenderNode`s. This example uses rounded rectangle haze areas like 'Images List'.

!!! abstract "Test setup"
    All of the tests were ran with 10 iterations on a Pixel 6, running the latest version of Android available. All of the numbers below are the P50 (median) frame duration time in milliseconds.

As with all benchmark tests, the results are only true for the exact things being tested. Using Haze in your own applications may result in different performance characteristics, so it is wise to write your own performance tests to validate the impact to your apps.

#### 0.7.3 vs 1.0.0

| Test          | 0.7.3      | 1.0.0      | Difference   |
| ------------- | ---------- | -----------| ------------ |
| Scaffold      | 6.9 ms     | 6.4 ms     | :material-trending-down: -7%     |
| Scaffold (progressive) (SDK 32)     | -     | 14.8 ms     | -    |
| Scaffold (progressive) (SDK 34)     | -     | 7.9 ms     | -     |
| Images List   | 6.9 ms    | 6.8 ms     | :material-trending-down: -1%  |
| Credit Card   | 4.9 ms     | 4.7 ms     | :material-trending-down: -4%  |

#### 1.0.0 vs baseline

We can also measure the rough cost of using Haze in the same samples. Here we've ran the same tests, but with Haze being disabled:

| Test          | 1.0.0 (disabled)  | 1.0.0      | Difference   |
| ------------- | ------------------| -----------| ------------ |
| Scaffold      | 4.9 ms            | 6.4 ms     | +31%         |
| Images List   | 4.6 ms            | 6.8 ms     | +48%         |
| Credit Card   | 4.1 ms            | 4.7 ms     | +15%         |

!!! example "Full results"
    For those interested, you can find the full results in this [spreadsheet](https://docs.google.com/spreadsheets/d/1wZ9pbX0HDIa08ITwYy7BrYYwOq2sX-HUyAMQlcb3dI4/edit?usp=sharing).
