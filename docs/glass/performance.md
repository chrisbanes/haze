# Glass performance

Glass tuning advice is now part of the [performance guide](../performance.md#glass).
For styling and API examples, see the [Glass overview](../effects/glass.md).

## Start here

See the [recommended workflow](../performance.md#recommended-workflow).

## Glass-specific cost drivers

See [Glass guidance](../performance.md#glass) and [common cost drivers](../performance.md#common-cost-drivers).

## When to change performance mode

See [performance mode](../performance.md#performance-mode).

## What to test

See [Glass guidance](../performance.md#glass) and [measurement advice](../performance.md#measure-on-target-devices).

## Benchmark results

These Pixel 8a measurements compare source-backed Glass with native Android Backdrop at 60 Hz. Each
value is the arithmetic mean of the P90 from two order-reversed passes, with eight measured
iterations per method. Negative frame overrun means the frame finished before its deadline.

| Quality workload | Source CPU P90 (ms) | Backdrop CPU P90 (ms) | Backdrop CPU difference | Source overrun P90 (ms) | Backdrop overrun P90 (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Glass, stable input | 3.54 | 5.18 | 46% higher | -10.69 | -0.48 |
| Glass, changing input | 3.46 | 4.24 | 23% higher | -1.84 | -0.48 |
| Nine Glass nodes, changing input | 3.94 | 4.74 | 20% higher | -6.85 | -5.18 |

Backdrop had higher CPU frame P90 in both passes for all three workloads, although every row
retained spare frame-deadline time at P90. Backdrop used approximately 6–9 MB less median peak GPU
memory for these Glass workloads. The stable test continuously invalidates the effect while leaving
the source pixels unchanged; it does not represent an idle static screen. These normal-scheduling
CPU results are device-specific and do not measure GPU shader duration. See the
[complete setup and measurements](../benchmark-results.md#native-android-backdrop).
