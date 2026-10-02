# Desktop window rendering tests

`HazeDesktopWindowRenderingTest` opens two real Compose Desktop windows and checks pixels
captured from the display. These tests need a working X11 display and cannot run in a headless
JVM. CI uses Xvfb with Skiko's software renderer.

## Prerequisites and commands

Use Java 21, Python 3, `xvfb`, and `xauth` on Linux. Run commands from the repository root. The
raw task is useful when developing the fixture:

```sh
SKIKO_RENDER_API=SOFTWARE xvfb-run -a -s '-screen 0 1280x800x24 -nolisten tcp' \
  ./gradlew :haze:desktopWindowTest -Phaze.disableAppleTargets --no-scan
```

The CI acceptance gate runs all five test methods and verifies the exact JUnit report:

```sh
SKIKO_RENDER_API=SOFTWARE xvfb-run -a -s '-screen 0 1280x800x24 -nolisten tcp' \
  ./gradlew :haze:verifyDesktopWindowTests -Phaze.disableAppleTargets --no-scan
```

The verification task depends on a fresh `desktopWindowTest` execution. It rejects missing,
filtered, duplicated, failed, errored, or skipped methods. A missing or unusable display fails the
test task; the suite does not skip when display setup is unavailable.

Agent-invoked Gradle commands must use the repository's managed `gradle-run` wrapper with Java 21
and `--no-scan`; CI and human-run commands may use the direct commands above.

## Results and coverage

The JUnit report is written to
`haze/build/test-results/desktopWindowTest/TEST-dev.chrisbanes.haze.HazeDesktopWindowRenderingTest.xml`.
Screen captures and accompanying diagnostic text are stored under
`haze/build/outputs/desktop-window-tests/<test-method>/`. CI retains both locations when the Linux
job fails.

The tests cover source-backed pixel alignment across independent native windows, movement of
either window, source-only redraw, and recovery after moving both windows. They verify the Linux
software-rendering path at 1:1 density. They do not establish hardware GPU behavior, macOS native
window behavior, other display densities, or shader quality/performance.
