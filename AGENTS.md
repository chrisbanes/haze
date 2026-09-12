# Repository guidance

Haze is a Kotlin Multiplatform library. Core APIs live in `haze`, effects in `haze-blur` and
`haze-glass`, presets in the materials modules, and shared tooling in `internal/`. Examples live
in `sample/`; library screenshots in `haze-screenshot-tests`, sample screenshots in
`sample/screenshot-tests`, and site content in `docs/` and `site/`.

## Everyday rules

- Use `rg` and `rg --files` for searches. Use worktrees or subagents when isolation or independent
  work makes them worthwhile; keep validation proportional to the change.
- Use Java 21. Agent-invoked Gradle commands must use `--no-scan` unless a Build Scan is explicitly
  authorized. Preserve the repository's configured CI Build Scan behaviour.
  Prefer targeted module tasks; run `./gradlew check --no-scan` before opening a PR.
- Follow `.editorconfig`: two-space Kotlin indentation, ktlint `intellij_idea`, and trailing commas.
  Apply Spotless to changed modules before committing.
- Keep public packages under `dev.chrisbanes.haze.*`; use PascalCase for composables, camelCase
  for parameters, and `*Defaults` for reusable configuration containers.
- Prefer `@Poko` for internal/private value types. Configure
  `pokoAnnotation.set("dev/chrisbanes/haze/Poko")`; use `data class` when `copy` or destructuring
  is intentional.
- Use AssertK for value, type, collection, boolean, and exception assertions, including custom
  assertion helpers. Do not use assertions from `kotlin.test`, JUnit, or Truth. Prefer semantic
  assertions (`isNull`, `contains`, `isInstanceOf`); use `isTrue`/`isFalse` only for boolean results.
  Give tests descriptive names such as `functionName_emitsExpectedBlur`.
- Keep commits focused with imperative subjects. PRs should explain motivation, affected modules,
  relevant issues, validation, and updated screenshots for UI changes. Include configuration
  changes needed by generated artifacts.

## Samples

Sample code is teaching material intended to be copied independently. Keep each example explicit
and locally complete. Do not extract helpers or parameterize sample composables just to remove
duplication; flag duplication only when it creates a concrete correctness or maintenance risk.
Share application infrastructure, fixtures, or abstractions that are themselves being demonstrated.

## Android physical-device benchmarks

- Read [`internal/benchmark/README.md`](internal/benchmark/README.md) before running or interpreting
  Macrobenchmarks. Use a physical device, keep it plugged in, awake, and unlocked for the complete
  run, and restore temporary device settings such as fixed-performance mode afterward.
- Use eight measured iterations for comparable Blur and Glass results. Use dry-run mode for one
  automation-validation iteration, or override `androidx.benchmark.iterations` to three for a quick
  diagnostic signal; do not publish either as benchmark evidence.
- Launch measured samples and profiling scenarios directly through their benchmark intent extras.
  Do not scroll through the sample list in per-iteration setup. Disable UiAutomator's idle wait only
  around continuously animating profiling scenarios and restore its previous timeout afterward.
- On Android 17 beta devices, a Chromium process hosted by the Google app can leave Perfetto waiting
  about 30 seconds for the `track_event` data source on every iteration. If logcat reports that
  producer timing out, run `adb shell am force-stop com.google.android.googlequicksearchbox` before
  restarting the suite; do not attribute that delay to the measured workload.
- Treat Gradle wall time, instrumentation time, and the fixed-duration measured window as separate
  quantities. Preserve every completed iteration and its trace, confirm the expected result and
  artifact count, and inspect thermal state and CPU placement before attributing a regression.

## Task guides

Read the relevant guide before starting that work:

| Task | Guidance |
| --- | --- |
| Android physical-device benchmarks | [Runbook](internal/benchmark/README.md) |
| Screenshot tests | [Agent rules](docs/agents/screenshot-tests.md) and [commands, profiles, recording](docs/screenshot-tests.md) |
| Built-in effect runtimes | [Implementation patterns](docs/agents/effect-runtimes.md) |
| Release changelog | [Release guidance](docs/agents/releases.md) |
| Issues and PRDs | [Issue tracker](docs/agents/issue-tracker.md) |
| Triage | [Canonical labels](docs/agents/triage-labels.md) |
| Domain documentation | [Root context and ADRs](docs/agents/domain.md) |
| GitHub Project execution | [Project binding and policy](docs/agents/run-github-project.md) |
