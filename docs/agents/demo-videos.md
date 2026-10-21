# Documentation demo videos

Record Haze demos from the JVM desktop sample on macOS. The browser sample was too slow for the
Glass container recording. Use the browser to verify the finished video in the documentation.

## Launch the sample

Run from the repository root with Java 21, following the [Gradle invocation rules](../../AGENTS.md).
For example:

```sh
./gradlew :sample:desktop:run --no-scan \
  -Phaze.embed=true \
  -Phaze.sample=glass-container-showcase \
  -Phaze.effect=glass \
  -Phaze.theme=light
```

The desktop entry point opens a selected sample in a 1280×720 window. This includes the title bar;
capture dimensions also depend on display scaling. Other routes are in
[`Samples.kt`](../../sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt).

### Packaged app fallback

If the UI tool cannot attach to the plain Java window, build a macOS app:

```sh
./gradlew :sample:desktop:createDistributable --no-scan
```

Open `sample/desktop/build/compose/binaries/main/app/desktop.app` through the UI tool. The
`-Phaze.*` launch properties apply to Gradle's Java execution tasks, so configure the packaged app
separately. Before launching, add these entries to the existing `[JavaOptions]` section in its
`Contents/app/desktop.cfg`, preserving the other entries:

```ini
java-options=-Dhaze.embed=true
java-options=-Dhaze.sample=glass-container-showcase
java-options=-Dhaze.effect=glass
java-options=-Dhaze.theme=light
```

This changes only generated output; rebuilding the bundle regenerates its configuration.
If Compose's runtime check rejects the installed Homebrew JDK, its documented local packaging
override is `-Pcompose.desktop.packaging.checkJdkVendor=false`. Apply it only to that packaging
invocation and verify that the resulting app launches before recording.

## Capture the window

Reset the demo to restore normal (1×) speed; half speed extends the showcase loop to 28 seconds.
Wait for any assets to load and the animation to settle. Enter the sample's recording mode to
hide its controls. Keep the pointer outside the intended crop and avoid scene interactions, which
can pause the showcase.

Discover the current `Haze Sample` window ID using the available window-inspection tools. This
read-only macOS query prints matching IDs and bounds:

```sh
swift -module-cache-path /tmp/haze-demo-swift-cache - <<'SWIFT'
import CoreGraphics
import Foundation
guard let windows = CGWindowListCopyWindowInfo(
  [.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID
) as? [[String: Any]] else {
  fputs("Window metadata unavailable; check WindowServer access.\n", stderr)
  exit(1)
}
for window in windows where window[kCGWindowName as String] as? String == "Haze Sample" {
  print("ID:", window[kCGWindowNumber as String] ?? "unknown",
        "bounds:", window[kCGWindowBounds as String] ?? "unknown")
}
SWIFT
```

Refresh the ID after relaunching the app. Capture that window directly: display or rectangle
capture can record the macOS computer-use privacy screen instead of the app.

Test a two-second recording and inspect an extracted frame before recording the full sequence:

```sh
screencapture -v -V2 -o -l<WINDOW_ID> /tmp/haze-demo-test.mov
ffmpeg -hide_banner -loglevel error -i /tmp/haze-demo-test.mov \
  -frames:v 1 /tmp/haze-demo-test.png
```

Replace `<WINDOW_ID>` with the discovered numeric ID. `-o` excludes the window shadow; the title
bar still needs cropping. When the test shows the intended scene, record two complete cycles with
settling time. The drawing-palette showcase has a fourteen-second cycle, so record 30 seconds:

```sh
screencapture -v -V30 -o -l<WINDOW_ID> /tmp/haze-demo-raw.mov
```

Use fresh temporary filenames for another take. Close the sample through its window controls
after recording, or cancel a Gradle launch through its owning tool session.

## Trim and encode

Inspect frames across the raw recording to choose a complete loop and a crop that keeps every
member visible, including the colour control at full separation. Exclude the title bar and demo controls.
Frame the effect closely enough to remain useful at phone width. Derive crop coordinates from the
actual capture pixels; window dimensions in points are not capture dimensions on Retina displays.

Encode a silent H.264 MP4 with browser-compatible pixel format and fast-start metadata. This
example produces a fourteen-second, 60 fps file; replace every placeholder with values from the take:

```sh
ffmpeg -hide_banner -loglevel error -ss <START_SECONDS> -i /tmp/haze-demo-raw.mov \
  -vf 'crop=<WIDTH>:<HEIGHT>:<X>:<Y>,scale=1280:720:flags=lanczos,fps=60' \
  -frames:v 840 -c:v libx264 -preset slow -crf 21 -pix_fmt yuv420p \
  -an -movflags +faststart docs/media/<NAME>.mp4
```

Use a 16:9 crop for that scale to preserve proportions, or choose output dimensions matching the
crop. Keep the raw take until the result has been reviewed.

## Verify and embed

- Inspect the raw frame timestamps for long gaps. Encoding at 60 fps can duplicate frames and
  does not establish smooth capture. Use `ffprobe -v error -select_streams v:0
  -show_entries frame=best_effort_timestamp_time -of csv=p=0 /tmp/haze-demo-raw.mov` to extract them.
  Watch the encoded clip, including its loop boundary, for pauses, jumps, missing transitions or
  loading images.
- Check dimensions, duration, frame count, file size and absence of audio with `ffprobe`. For
  example, use `-v error -show_streams -show_format <VIDEO_PATH>`.
- Embed the MP4 from `docs/media/` using the existing `type:video` Markdown syntax. Give it a short
  explanatory caption and an accessible label. The site supplies muted, looping autoplay; use
  `style="width:100%;height:auto"` to preserve the video's aspect ratio and `playsinline` for inline
  playback. See the [container page](../effects/glass/containers.md) for an example.
- Run `./scripts/build_docs.sh build`. Inspect the built page at desktop and phone widths: confirm
  playback, looping, useful framing and no horizontal overflow. Restore any temporary viewport
  override afterward.

Review the final diff so only the intended documentation and encoded media are included.
