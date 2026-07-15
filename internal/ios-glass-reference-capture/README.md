# iOS Glass Reference Capture

This checked-in Xcode project renders four pinned scenes using Apple's iOS Liquid Glass Regular
material and captures their measured 1080x2160-pixel viewports. The capture script owns simulator
discovery, build, readiness validation, lossless cropping, manifest generation, bundle validation,
and optional atomic import.

## Requirements

- Xcode 26.3 with the iOS 26.3 simulator runtime installed.
- The `com.apple.CoreSimulator.SimDeviceType.iPhone-17` simulator device type.
- `jq`, `sips`, and Git available on `PATH`.
- A clean Git worktree, including no untracked files. Git-ignored tool output under `build/` does not
  make the worktree dirty.

The producer pins `HEAD` during initial provenance validation and writes that pinned revision to the
manifest. It re-checks both `HEAD` and worktree cleanliness immediately before refreshing current
staging and again immediately before import replacement; any mid-capture change aborts that commit
boundary. During the final import check, only the exact active process-owned sibling import
directory is classified as transaction output. Other tracked, staged, or untracked paths—including
similarly prefixed siblings—still abort the import.

The script reuses an available iPhone 17 simulator named `Haze Glass Reference` under the
pinned iOS 26.3 runtime. If none exists, it creates only that dedicated simulator. It chooses
deterministically when multiple dedicated devices exist and never deletes or repurposes unrelated
simulators.

`project.yml` is the maintainer source for regenerating the checked-in Xcode project. XcodeGen is
not required to run captures. The script also has no `rtk` runtime dependency.

## Commands

Run these from `internal/ios-glass-reference-capture`:

```bash
./scripts/capture-references.sh
./scripts/capture-references.sh --validate-only build/captures/current
./scripts/capture-references.sh --import
```

The default command always performs a fresh Release build and capture. It validates a temporary
five-file bundle before atomically refreshing `build/captures/current`, which contains only:

- `manifest.json`
- `uniform-light.png`
- `uniform-dark.png`
- `grid-light.png`
- `grid-dark.png`

Full simulator screenshots and readiness payloads remain outside the staged bundle under
`build/captures/framebuffers` and `build/captures/readiness` for diagnosis. A failed capture or
validation leaves the prior validated `build/captures/current` bundle intact. Before cropping, each
full screenshot must itself be a 1206x2622 RGB PNG matching the pinned readiness framebuffer.

Mutating commands hold the exclusive `build/.capture-references.lock` across provenance checks,
build output, staging, and replacement. A concurrent invocation fails without touching shared
state. Locks are never assumed stale or deleted by a non-owner; investigate a reported owner PID
before manually resolving a lock left by an ungraceful machine/process termination.

`--import` does not reuse stale staging. It performs another complete fresh capture, copies exactly
the manifest and four PNGs to a sibling temporary directory, validates that directory, and then
atomically replaces the entire five-file resource bundle at
`haze-screenshot-tests/src/commonTest/resources/glass/ios26`. If installation fails before
the commit rename, the previous resource bundle is restored by sibling rename and the command exits
nonzero. Termination signals are ignored only while the old and new directories are renamed, so the
previous bundle cannot be stranded at its backup path between those operations. Backup deletion is
best-effort after the new bundle is installed. `SIGKILL` and power loss cannot be trapped; inspect
any reported sibling backup before retrying. `IMPORT_DESTINATION` is available only for tests and
dry-run imports; it executes the same replacement workflow.

Potentially blocking external operations are bounded: simulator boot readiness is 120 seconds, the
Release build is 600 seconds, and other simulator/image/provenance commands are 60 seconds. Timeout
handling terminates and reaps the complete command process group. The
`CAPTURE_BOOT_TIMEOUT_SECONDS`, `CAPTURE_BUILD_TIMEOUT_SECONDS`, and
`CAPTURE_COMMAND_TIMEOUT_SECONDS` overrides exist for regression tests only; normal captures should
use the fixed defaults.

## Intentional Regeneration Workflow

1. Run the default command and inspect the four cropped images, `manifest.json`, and retained
   readiness/framebuffer diagnostics.
2. Run `--validate-only build/captures/current` for an explicit validation pass.
3. Run `--import` only when intentionally regenerating accepted references. It fresh-captures all
   scenes again before replacement.
4. Review the complete five-file resource diff. Acceptance and committing of those resources is a
   separate task.

## Troubleshooting

- `Required available simulator runtime not found`: install the iOS 26.3 runtime in Xcode Settings
  and confirm it is available to `simctl`.
- `Required command not found`: install `jq` or ensure the reported Xcode command-line tool is on
  `PATH`.
- `Xcode 26.3 is required`: select the exact supported Xcode with `xcode-select`. The manifest keeps
  the complete `xcodebuild -version` output, including its actual build-version line.
- `Producer repository worktree must be clean`: commit, stash, or remove every tracked change and
  untracked source file before capture. Ignored `build/` output can remain.
- `Another capture run holds lock`: wait for the reported PID. Do not delete the lock as stale
  without investigating whether that process still owns shared capture/build state.
- `Release build failed` or `Built capture app was not found`: select Xcode 26.3 with
  `xcode-select`, then retry. The script expects the checked-in project and exact Release simulator
  app product path.
- `Simulator did not finish booting`, install, container, or launch failures: open Simulator once,
  confirm the pinned runtime/device type is healthy, and retry. Unrelated devices are never removed.
- `Timed out after 15 seconds waiting for valid readiness`: inspect the scene launch and the latest
  files under `build/captures/readiness`; readiness is written only after two display frames and the
  1.2-second foreground stability gate.
- Readiness, crop, PNG, color-space, or manifest contract failures indicate that simulator geometry
  or producer output changed. Do not import; inspect the retained full-frame and readiness data.
- `timed out after ... seconds`: inspect the named boot/build/simulator/image operation. The child
  command tree has been terminated and the owned lock released, so fix the underlying tool or
  simulator issue and rerun.
- Atomic install failures report the destination, return nonzero, and restore its previous validated
  bundle. If restoration fails, preserve the exact backup path named in the `CRITICAL` diagnostic.
  A backup-cleanup warning means the new bundle was installed; inspect and manually remove the
  reported residual sibling backup when safe.
