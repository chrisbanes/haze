# Compose v2 Test API Migration

## Goal

Migrate all Compose test code from v1 APIs (`androidx.compose.ui.test.junit4`) to v2 APIs (CMP `compose.uiTest` accessor). Two areas are affected: the screenshot test infrastructure (`internal/screenshot-test`) and two instrumented test files in `haze-blur`.

## Architecture

```
haze-screenshot-tests (55+ test methods — no changes)
  └── extends ScreenshotTest (expect/actual)
        ├── [Android] ScreenshotTest.android.kt  ← MIGRATED
        └── [JVM]    ScreenshotTest.jvm.kt       ← already v2, no changes

haze-blur/src/androidInstrumentedTest/
  ├── BlurVisualEffectRecompositionLoopInstrumentationTest.kt     ← MIGRATED
  └── BlurVisualEffectRecompositionCountInstrumentationTest.kt   ← MIGRATED
```

The `ScreenshotUiTest` interface (`setContent`, `captureRoot`, `waitForIdle`) is preserved — zero changes to screenshot test methods.

## Changes by file

### 1. `internal/screenshot-test/src/androidMain/.../ScreenshotTest.android.kt`

**Remove:**
- `@get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()` (v1 JUnit4 rule)
- `@get:Rule val roborazziRule = RoborazziRule(...)` (replaced by programmatic context)
- `@Before fun fixComposeResources()` (moves into test runner setup)
- Imports for `androidx.compose.ui.test.junit4.createAndroidComposeRule`, `RoborazziRule`

**Replace with:**
- `runScreenshotTest` becomes self-contained: launches `ActivityScenario`, creates `AndroidComposeUiTestEnvironment`, configures Roborazzi programmatically via `provideRoborazziContext()` (same pattern as JVM variant), runs test, closes scenario
- `@GraphicsMode(NATIVE)` and `@Config(sdk = [28, 32, 35], qualifiers = ...)` remain on the class

### 2. Instrumented tests (2 files)

**`BlurVisualEffectRecompositionLoopInstrumentationTest.kt`:**
- Drop `@RunWith(AndroidJUnit4::class)`
- Drop `@get:Rule val composeTestRule = createComposeRule()`
- Add `createAndroidComposeRule()` from CMP `compose.uiTest` (stored as plain property)
- Replace `ComposeContentTestRule` receiver type with `AndroidComposeUiTest` in extensions

**`BlurVisualEffectRecompositionCountInstrumentationTest.kt`:**
- Same changes as above

### 3. `internal/screenshot-test/build.gradle.kts`

| Dependency | Action |
|---|---|
| `compose.desktop.uiTestJUnit4` (androidMain) | Remove |
| `libs.roborazzi.junit` (androidMain) | Remove |

### 4. `haze-blur/build.gradle.kts`

| Dependency | Action |
|---|---|
| `androidx.compose.ui:ui-test-junit4` | Remove |
| `androidx.test.ext:junit-ktx` | Remove (if only used by these tests) |

Add `compose.uiTest` CMP accessor if not already present.

## Verification

1. `./gradlew :haze-screenshot-tests:test` — all screenshot tests pass
2. `./gradlew :haze-screenshot-tests:recordRoborazzi` — screenshots regenerate correctly
3. `./gradlew :haze-blur:connectedAndroidTest` — instrumented tests pass (requires device/emulator)
4. `./gradlew build` — full multi-platform build succeeds
5. `./gradlew spotlessApply` — formatting applied
