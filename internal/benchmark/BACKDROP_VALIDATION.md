# Backdrop validation evidence

## Emulator correctness evidence (2026-09-05)

The committed native-backdrop fixes were exercised on the supported preview emulator before any
performance claim. The run used the `Medium_Phone` AVD with the
`android-37.2-beta3/google_apis_playstore_ps16k/arm64-v8a` revision 3 image, SDK 37, full SDK
37.1, preview 3723, fingerprint
`google/sdk_gphone16k_arm64/emu64a16k:DEV/CP41.260731.005.B1/16056512:user/dev-keys`, emulator
37.1.11.0 build 15917651, and the Apple M1 Max `skiagl` host renderer. The tested commit was
`56c866937a872cc9b73e063b34b12d3c4d74ade5`.

The core suite passed 4 tests, Blur passed 6, and Glass passed 5; all had zero failures, errors,
or skips. The suites covered native fallback state, progressive Blur, clip transitions, paired
offscreen pixel scenes, and Glass window/offscreen pixel scenes. The saved XML reports are
`haze/build/outputs/androidTest-results/connected/androidMain/TEST-Medium_Phone(AVD) - 17.xml`,
with equivalent paths under `haze-blur/` and `haze-glass/`.

This emulator result establishes preview correctness only. It does not provide physical-device
37.2 compositor evidence, per-offscreen capture-counter evidence, or the order-reversed
fixed-performance measurements required by the
[physical performance gate](README.md#android-372-sourcebackdrop-comparisons).
