# Glass Size Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Calibrate Haze Glass against iOS 26 Regular across component geometry, expose adaptive versus fixed sizing, and verify the fitted response on Android and Skiko holdouts.

**Architecture:** The Swift capture app produces immutable training and holdout references for controlled size, aspect-ratio, and roundness sweeps. Common Kotlin resolves those logical geometry inputs through a small bounded model before layer bounds and render parameters are built, while the existing shaders continue receiving ordinary resolved values.

**Tech Stack:** SwiftUI, Swift Testing, shell capture tooling, Kotlin Multiplatform, Compose Multiplatform, AGSL/SkSL runtime effects, assertk, Roborazzi, Metalava, Gradle.

---

Repository policy requires explicit user authorization before creating commits. Commit steps are
therefore omitted. Before the native `--import` capture in Task 3, obtain authorization to commit
Tasks 1 and 2 because the provenance-safe capture script intentionally rejects dirty worktrees.

## File Map

### Native capture

- Modify `internal/ios-glass-reference-capture/App/CaptureScene.swift`: model capture pages, scene variants, calibration roles, and page-specific surfaces.
- Modify `internal/ios-glass-reference-capture/App/ReferenceSceneView.swift`: render the selected page and each surface's authoritative shape.
- Modify `internal/ios-glass-reference-capture/App/CaptureMetadata.swift`: serialize page-specific surface geometry and calibration metadata.
- Modify `internal/ios-glass-reference-capture/AppTests/CaptureSceneTests.swift`: lock scene parsing, page layouts, roles, dimensions, and radii.
- Modify `internal/ios-glass-reference-capture/AppTests/CaptureMetadataTests.swift`: lock schema-2 readiness metadata.
- Modify `internal/ios-glass-reference-capture/scripts/capture-references.sh`: capture and validate the complete multi-page bundle.
- Modify `internal/ios-glass-reference-capture/scripts/capture-references-test.sh`: cover the expanded bundle and atomic import.
- Modify `internal/ios-glass-reference-capture/README.md`: document the new bundle and capture workflow.
- Replace `haze-screenshot-tests/src/commonTest/resources/glass/ios26/manifest.json`: schema-2 native capture manifest.
- Add `haze-screenshot-tests/src/commonTest/resources/glass/ios26/<page>-<background>-<appearance>.png`: page fixtures for baseline and calibration sweeps.

### Glass API and model

- Create `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassSizing.kt`: public adaptive/fixed policy.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`: default adaptive policy.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt`: add sizing to `GlassOptics`.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`: resolve, copy, clear, and invalidate sizing.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDirtyFields.kt`: track sizing and layer-bound invalidation.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`: own geometry input, bounded response, and baseline resolution.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`: consume the shared resolved optics.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`: cover default/style/local/direct sizing precedence.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectOverrideTest.kt`: cover clear/copy/dirty behavior.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderParamsTest.kt`: cover bounds, continuity, clamping, density, rotation, and shape ownership.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectKeysTest.kt`: cover adapted-key changes and fixed identity.
- Regenerate `haze-glass/api/api.txt`: publish the experimental API surface.

### Calibration and visual verification

- Create `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassCalibrationCases.kt`: shared native matrix and logical geometry.
- Modify `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassImageMetrics.kt`: store native metrics and derive bands for every case.
- Modify `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassIos26ReferenceMetricsTest.kt`: measure every immutable reference page and print the training/holdout report.
- Modify `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInvariantSample.kt`: render calibration geometries and assert holdout metrics and resize continuity.
- Modify `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassDepthDesktopScreenshotTest.kt`: run Skiko holdouts.
- Modify `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt`: run Android holdouts.
- Modify `docs/effects/glass.md`: document adaptive baselines, fixed sizing, shape ownership, and calibration range.

## Task 1: Model The Native Capture Matrix

**Files:**
- Modify: `internal/ios-glass-reference-capture/App/CaptureScene.swift`
- Modify: `internal/ios-glass-reference-capture/App/ReferenceSceneView.swift`
- Test: `internal/ios-glass-reference-capture/AppTests/CaptureSceneTests.swift`

- [ ] **Step 1: Replace the fixed-layout assertions with failing matrix assertions**

Add tests that require six pages and the approved geometry. Keep the existing baseline frames byte-for-byte compatible.

```swift
@Test
func referencePagesContainApprovedGeometry() {
    #expect(ReferencePage.allCases == [
        .baseline, .sizeSmall, .sizeMedium, .sizeLarge, .aspect, .roundness,
    ])
    #expect(ReferenceLayout.surfaces(for: .baseline).map(\.id) == [
        "capsule", "card", "panel",
    ])
    #expect(ReferenceLayout.surfaces(for: .sizeSmall).map(\.logicalSize) == [
        CGSize(width: 66, height: 44),
        CGSize(width: 96, height: 64),
        CGSize(width: 132, height: 88),
    ])
    #expect(ReferenceLayout.surfaces(for: .sizeMedium).map(\.logicalSize) == [
        CGSize(width: 168, height: 112),
        CGSize(width: 216, height: 144),
        CGSize(width: 264, height: 176),
    ])
    #expect(ReferenceLayout.surfaces(for: .sizeLarge).map(\.logicalSize) == [
        CGSize(width: 330, height: 220),
    ])
    #expect(ReferenceLayout.surfaces(for: .aspect).map { $0.logicalSize.width / $0.logicalSize.height } == [
        1, 1.5, 2, 3, 4,
    ])
    #expect(ReferenceLayout.surfaces(for: .roundness).map(\.cornerRadius) == [
        0, 12, 24, 36, 48,
    ])
}

@Test
func trainingAndHoldoutRolesAreLocked() {
    #expect(ReferenceLayout.surfaces(for: .sizeSmall).map(\.role) == [
        .training, .holdout, .training,
    ])
    #expect(ReferenceLayout.surfaces(for: .sizeMedium).map(\.role) == [
        .holdout, .training, .holdout,
    ])
    #expect(ReferenceLayout.surfaces(for: .sizeLarge).map(\.role) == [.training])
    #expect(ReferenceLayout.surfaces(for: .aspect).map(\.role) == [
        .training, .holdout, .training, .holdout, .training,
    ])
    #expect(ReferenceLayout.surfaces(for: .roundness).map(\.role) == [
        .training, .holdout, .training, .holdout, .training,
    ])
}
```

- [ ] **Step 2: Run the Swift tests and verify the new symbols fail to compile**

Run:

```bash
xcodebuild test \
  -project internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj \
  -scheme CaptureApp \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.3.1'
```

Expected: FAIL because `ReferencePage`, `logicalSize`, `cornerRadius`, and `role` do not exist.

- [ ] **Step 3: Add page, role, shape, and surface definitions**

Use value types so readiness metadata and view rendering consume one source of truth:

```swift
enum ReferencePage: String, CaseIterable, Codable, Sendable {
    case baseline
    case sizeSmall = "size-small"
    case sizeMedium = "size-medium"
    case sizeLarge = "size-large"
    case aspect
    case roundness
}

enum GlassAppearance: String, CaseIterable, Codable, Sendable {
    case light
    case dark
}

enum CaptureBackground: String, CaseIterable, Codable, Sendable {
    case uniform
    case grid
}

enum CalibrationRole: String, Codable, Sendable {
    case training
    case holdout
    case regression
}

enum ReferenceShape: Equatable, Sendable {
    case capsule
    case roundedRectangle(cornerRadius: CGFloat)
}

struct ReferenceSurface: Identifiable, Equatable, Sendable {
    let id: String
    let frame: CGRect
    let shape: ReferenceShape
    let role: CalibrationRole

    var logicalSize: CGSize { frame.size }
    var cornerRadius: CGFloat {
        switch shape {
        case .capsule: frame.height / 2
        case let .roundedRectangle(cornerRadius): cornerRadius
        }
    }
}
```

Keep a 360 x 720 viewport. Center each surface horizontally and use these vertical placements:

```swift
// size-small y: 40, 144, 288
// size-medium y: 24, 184, 384
// size-large y: 250
// aspect y: 24, 152, 280, 408, 536
// roundness y: 24, 152, 280, 408, 536
```

Give every calibration surface a globally unique ID such as `size-44`, `aspect-3`, and
`roundness-36`. Baseline surfaces use role `regression`.

- [ ] **Step 4: Expand capture scenes without duplicating page layout**

Model a scene as page plus the existing appearance/background pair. Preserve legacy baseline file
names and prefix calibration files with the page name:

```swift
struct CaptureScene: RawRepresentable, CaseIterable, Codable, Equatable, Sendable {
    let page: ReferencePage
    let appearance: GlassAppearance
    let background: CaptureBackground

    init(page: ReferencePage, appearance: GlassAppearance, background: CaptureBackground) {
        self.page = page
        self.appearance = appearance
        self.background = background
    }

    var rawValue: String {
        let suffix = "\(background.rawValue)-\(appearance.rawValue)"
        return page == .baseline ? suffix : "\(page.rawValue)-\(suffix)"
    }

    var outputFilename: String { "\(rawValue).png" }

    static let allCases = ReferencePage.allCases.flatMap { page in
        CaptureBackground.allCases.flatMap { background in
            GlassAppearance.allCases.map { appearance in
                CaptureScene(page: page, appearance: appearance, background: background)
            }
        }
    }
}
```

Implement `init?(rawValue:)` by selecting the single entry in `allCases` with a matching raw value.
Encode and decode `CaptureScene` as its raw string so readiness JSON preserves the existing scene
contract. Derive `colorScheme` and `isGrid` from `appearance` and `background`.

- [ ] **Step 5: Render the selected page and authoritative shape**

Replace the three hardcoded calls in `ReferenceSceneView` with a loop over
`ReferenceLayout.surfaces(for: scene.page)`. Switch on `ReferenceShape`: use `Capsule()` only for the
legacy capsule and `RoundedRectangle(cornerRadius:)` otherwise. Continue applying
`.glassEffect(.regular.tint(nil).interactive(false), in: shape)`.

- [ ] **Step 6: Run the Swift tests**

Run the Step 2 command.

Expected: PASS, including all legacy scene parsing and frame assertions updated for the value-type
scene model.

## Task 2: Expand Readiness And Bundle Validation

**Files:**
- Modify: `internal/ios-glass-reference-capture/App/CaptureMetadata.swift`
- Modify: `internal/ios-glass-reference-capture/AppTests/CaptureMetadataTests.swift`
- Modify: `internal/ios-glass-reference-capture/scripts/capture-references.sh`
- Modify: `internal/ios-glass-reference-capture/scripts/capture-references-test.sh`
- Modify: `internal/ios-glass-reference-capture/README.md`

- [ ] **Step 1: Write failing schema-2 metadata tests**

Require each readiness surface to carry geometry and calibration metadata:

```swift
struct CaptureSurface: Codable, Equatable, Sendable {
    let frame: PixelRect
    let cornerRadius: Int
    let role: CalibrationRole
}

@Test
func readinessContainsOnlySelectedPageSurfaces() {
    let scene = CaptureScene(
        page: .sizeLarge,
        appearance: .dark,
        background: .grid,
    )
    let ready = CaptureReady.make(
        scene: scene,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    #expect(ready.schemaVersion == 2)
    #expect(ready.surfaces == [
        "size-220": CaptureSurface(
            frame: PixelRect(x: 45, y: 750, width: 990, height: 660),
            cornerRadius: 165,
            role: .training,
        ),
    ])
}
```

- [ ] **Step 2: Run Swift tests and verify failure**

Run the Task 1 Swift test command.

Expected: FAIL because readiness schema 2 and `CaptureSurface` do not exist.

- [ ] **Step 3: Implement schema-2 readiness metadata**

Change `CaptureReady.surfaces` to `[String: CaptureSurface]`, set `schemaVersion = 2`, and source the
selected page from `ReferenceLayout.surfaces(for: scene.page)`. Pixel-scale both frame and corner
radius. Update the stable JSON contract test to assert `page`, `frame`, `cornerRadius`, and `role`.

- [ ] **Step 4: Make the shell bundle page-driven**

Define the six page names once and derive the 24 expected PNG names:

```bash
REFERENCE_PAGES=(baseline size-small size-medium size-large aspect roundness)
REFERENCE_VARIANTS=(uniform-light uniform-dark grid-light grid-dark)

scene_name() {
  local page=$1
  local variant=$2
  if [[ "$page" == baseline ]]; then
    printf '%s' "$variant"
  else
    printf '%s-%s' "$page" "$variant"
  fi
}
```

Use this function for launch arguments, readiness lookup, crop filenames, manifest scenes, bundle
validation, temporary staging, and atomic import. A valid bundle contains exactly one manifest and
24 PNGs. Reject missing, duplicate, or extra files.

Generate manifest schema 2 with:

```json
{
  "schemaVersion": 2,
  "platform": "iOS 26",
  "material": "Regular",
  "tint": "transparent",
  "scenes": {},
  "surfaces": {},
  "producer": {}
}
```

Each scene records page, appearance, and background. Merge readiness surface entries by globally
unique ID; require repeated metadata to be identical across all four variants of a page.

- [ ] **Step 5: Extend shell self-test fixtures**

Generate fake readiness and PNG fixtures for every page/variant combination. Assert:

```text
25 files validate
missing calibration PNG fails
extra PNG fails
schema-1 readiness fails
conflicting repeated surface metadata fails
atomic import installs all 25 files
failed import restores the previous complete bundle
```

- [ ] **Step 6: Run native and shell tests**

Run:

```bash
xcodebuild test \
  -project internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj \
  -scheme CaptureApp \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.3.1'
internal/ios-glass-reference-capture/scripts/capture-references-test.sh
```

Expected: both commands PASS.

- [ ] **Step 7: Update capture documentation**

Document six pages, 24 PNGs, schema 2, training/holdout metadata, and the unchanged pinned producer
requirements. Replace every five-file-bundle statement with the exact 25-file contract.

## Task 3: Capture And Import Immutable iOS References

**Files:**
- Replace: `haze-screenshot-tests/src/commonTest/resources/glass/ios26/manifest.json`
- Add/replace: `haze-screenshot-tests/src/commonTest/resources/glass/ios26/*.png`

- [ ] **Step 1: Obtain commit authorization and establish a clean provenance boundary**

Commit the approved spec and plan together with Tasks 1 and 2 only after explicit user
authorization. Confirm:

```bash
```

Expected: no output. Do not bypass, weaken, or patch around the capture script's cleanliness check.

- [ ] **Step 2: Run a fresh capture without import**

Run:

```bash
internal/ios-glass-reference-capture/scripts/capture-references.sh
```

Expected: Xcode 26.3/iOS 26.3.1 capture succeeds and
`internal/ios-glass-reference-capture/build/captures/current` validates as one manifest plus 24 RGB
PNGs.

- [ ] **Step 3: Validate the staged bundle explicitly**

Run:

```bash
internal/ios-glass-reference-capture/scripts/capture-references.sh \
  --validate-only internal/ios-glass-reference-capture/build/captures/current
```

Expected: PASS with schema 2 and all declared bounds inside 1080 x 2160.

- [ ] **Step 4: Import with a second fresh capture**

Run:

```bash
internal/ios-glass-reference-capture/scripts/capture-references.sh --import
```

Expected: the complete resource directory is atomically replaced by the validated 25-file bundle.

- [ ] **Step 5: Inspect the imported contract**

Confirm the manifest producer remains Xcode 26.3, iOS build 23D8133, iPhone 17, scale 3, RGB, and
that all training and holdout roles match Task 1. Inspect every page image for clipping, overlap,
system UI, or an incorrect background.

## Task 4: Add The Public Sizing Policy

**Files:**
- Create: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassSizing.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDirtyFields.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectOverrideTest.kt`

- [ ] **Step 1: Write failing API behavior tests**

Extend existing tests with:

```kotlin
@Test
fun sizing_resolvesDirectStyleLocalAndDefaultPrecedence() {
  val effect = GlassVisualEffect().apply {
    compositionLocalStyle = GlassStyle(optics = GlassOptics(sizing = GlassSizing.Fixed))
  }
  assertThat(effect.sizing).isEqualTo(GlassSizing.Fixed)

  effect.style = GlassStyle(optics = GlassOptics(sizing = GlassSizing.Adaptive))
  assertThat(effect.sizing).isEqualTo(GlassSizing.Adaptive)

  effect.sizing = GlassSizing.Fixed
  assertThat(effect.sizing).isEqualTo(GlassSizing.Fixed)

  effect.clearSizingOverride()
  assertThat(effect.sizing).isEqualTo(GlassSizing.Adaptive)
}

@Test
fun sizingOverride_copyAndClearPreserveExpectedSource() {
  val original = GlassVisualEffect().apply { sizing = GlassSizing.Fixed }
  val copy = GlassVisualEffect(original)
  assertThat(copy.sizing).isEqualTo(GlassSizing.Fixed)
  copy.clearSizingOverride()
  assertThat(copy.sizing).isEqualTo(GlassDefaults.sizing)
  assertThat(GlassDirtyFields.stringify(copy.dirtyTracker)).contains("Sizing")
}
```

- [ ] **Step 2: Run tests and verify compilation failure**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests '*GlassStyleTest' --tests '*GlassVisualEffectOverrideTest'
```

Expected: FAIL because `GlassSizing` and sizing properties do not exist.

- [ ] **Step 3: Implement the public enum and style default**

```kotlin
@ExperimentalHazeApi
public enum class GlassSizing {
  Adaptive,
  Fixed,
}
```

Add `GlassDefaults.sizing = GlassSizing.Adaptive`, include it in `GlassDefaults.style`, and add
`val sizing: GlassSizing? = null` to `GlassOptics`.

- [ ] **Step 4: Implement effect precedence and invalidation**

Use a nullable backing field like the existing object overrides:

```kotlin
private var _sizing: GlassSizing? = null

public var sizing: GlassSizing
  get() = _sizing ?: styleOptics.sizing ?: localOptics.sizing ?: GlassDefaults.sizing
  set(value) {
    if (value != _sizing) {
      HazeLogger.d(TAG) { "sizing changed. Current: $_sizing. New: $value" }
      _sizing = value
      dirtyTracker += GlassDirtyFields.Sizing
    }
  }

public fun clearSizingOverride() {
  if (_sizing != null) {
    HazeLogger.d(TAG) { "sizing override cleared. Current: $_sizing" }
    _sizing = null
    dirtyTracker += GlassDirtyFields.Sizing
  }
}
```

Copy `_sizing` in the copy constructor. Add `Sizing` after `Style` in `GlassDirtyFields`, include it
in `InvalidateFlags`, `LayerBoundsFlags`, and `stringify()`.

- [ ] **Step 5: Run focused tests**

Run the Step 2 command.

Expected: PASS.

## Task 5: Build The Pure Bounded Geometry Resolver

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderParamsTest.kt`

- [ ] **Step 1: Replace current profile tests with failing policy and invariant tests**

Cover the public contract independently of final fitted coefficients:

```kotlin
@Test
fun fixedSizing_returnsIdentityResponse() {
  val response = calculateRegularGeometryResponse(
    sizing = GlassSizing.Fixed,
    materialSizePx = Size(660f, 330f),
    density = Density(3f),
    cornerRadiiPx = CornerRadii(165f, 165f, 165f, 165f),
  )
  assertThat(response).isEqualTo(RegularGeometryResponse.Identity)
}

@Test
fun adaptiveSizing_isDensityAndRotationInvariant() {
  val first = calculateRegularGeometryResponse(
    GlassSizing.Adaptive,
    Size(480f, 240f),
    Density(2f),
    CornerRadii(48f, 48f, 48f, 48f),
  )
  val second = calculateRegularGeometryResponse(
    GlassSizing.Adaptive,
    Size(720f, 1440f),
    Density(6f),
    CornerRadii(144f, 144f, 144f, 144f),
  )
  assertThat(first).isEqualTo(second)
}

@Test
fun adaptiveSizing_clampsToCalibratedDomainAndIsContinuous() {
  fun response(shortSideDp: Float) = calculateRegularGeometryResponseForLogicalGeometry(
    shortestSideDp = shortSideDp,
    aspectRatio = 1.5f,
    symmetricRoundness = 0.5f,
  )
  assertThat(response(1f)).isEqualTo(response(44f))
  assertThat(response(1_000f)).isEqualTo(response(220f))
  assertResponseDeltaBelow(response(111.99f), response(112.01f), 0.001f)
}
```

Also test aspect clamping `1..4`, roundness clamping `0..1`, finite outputs, corner permutation,
invalid-geometry identity fallback, and response bounds.

- [ ] **Step 2: Run the focused model tests and verify failure**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests '*GlassRenderParamsTest'
```

Expected: FAIL because the response types and logical-geometry resolver do not exist.

- [ ] **Step 3: Add geometry input and response types**

```kotlin
internal data class RegularGeometryResponse(
  val blurScale: Float,
  val displacementScale: Float,
  val reachScale: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
) {
  companion object {
    val Identity = RegularGeometryResponse(1f, 1f, 1f, 1f, 0f)
  }

  fun resolve(refractionStrength: Float): RegularGeometryResponse {
    val strength = refractionStrength.coerceIn(0f, 1f)
    return RegularGeometryResponse(
      blurScale = lerp(1f, blurScale, strength),
      displacementScale = lerp(1f, displacementScale, strength),
      reachScale = lerp(1f, reachScale, strength),
      toneGain = lerp(1f, toneGain, strength),
      neutralLiftWeight = neutralLiftWeight * strength,
    )
  }
}
```

`calculateRegularGeometryResponse()` converts px to dp before deriving shortest side, orientation-
independent aspect, and minimum normalized radius. Invalid sizes or density return identity.

- [ ] **Step 4: Implement a bounded continuous model seam**

Move coefficients into one private immutable value and keep evaluation pure:

```kotlin
private data class RegularGeometryCoefficients(
  val blur: GeometryCurve,
  val displacement: GeometryCurve,
  val reach: GeometryCurve,
  val tone: GeometryCurve,
  val lift: GeometryCurve,
)

private data class GeometryCurve(
  val base: Float,
  val size: Float,
  val aspect: Float,
  val roundness: Float,
  val sizeRoundness: Float,
)
```

Use clamped smoothstep features for the three domains and evaluate
`base + size*s + aspect*a + roundness*r + sizeRoundness*s*r`. Initially preserve the current
renderer at the three regression geometries; Task 8 replaces these seed coefficients with the fit
selected only from training cases. Clamp final response fields to conservative declared ranges:

```kotlin
blurScale in 0.4f..1.1f
displacementScale in 0.25f..2f
reachScale in 0.5f..1.5f
toneGain in 0.9f..1.1f
neutralLiftWeight in 0f..0.12f
```

- [ ] **Step 5: Run model tests**

Run the Step 2 command.

Expected: PASS.

## Task 6: Share Resolution Between Bounds And Rendering

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderParamsTest.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectKeysTest.kt`

- [ ] **Step 1: Write failing baseline-resolution tests**

```kotlin
@Test
fun resolvedOptics_preserveZeroBaselinesAndClampReach() {
  val resolved = resolveRegularGeometryOptics(
    response = RegularGeometryResponse(
      blurScale = 0.5f,
      displacementScale = 2f,
      reachScale = 1.5f,
      toneGain = 1.05f,
      neutralLiftWeight = 0.1f,
    ),
    refractionStrength = 1f,
    shortestSidePx = 100f,
    blurRadiusPx = 0f,
    refractionScalePx = 0f,
    refractionHeight = 1f,
  )
  assertThat(resolved.blurRadiusPx).isEqualTo(0f)
  assertThat(resolved.refractionScalePx).isEqualTo(0f)
  assertThat(resolved.refractionHeightPx).isEqualTo(100f)
}

@Test
fun fixedLayerPadding_usesLiteralBaselineValues() {
  val effect = GlassVisualEffect().apply {
    sizing = GlassSizing.Fixed
    blurRadius = 32.dp
    refractionScale = 15f
  }
  assertThat(effect.calculateLayerBounds(Rect(0f, 0f, 200f, 100f), Density(1f)))
    .isEqualTo(Rect(0f, 0f, 200f, 100f).inflate(32f + 15f * effect.refractionStrength + 2f))
}
```

Add a key test proving adaptive geometry changes blur/optical keys while `Fixed` preserves literal
baseline key values.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :haze-glass:jvmTest \
  --tests '*GlassRenderParamsTest' \
  --tests '*GlassRenderEffectKeysTest'
```

Expected: FAIL because shared resolved optics do not exist and bounds still use the old profile.

- [ ] **Step 3: Add one baseline resolver**

```kotlin
internal data class ResolvedRegularGeometryOptics(
  val blurRadiusPx: Float,
  val blurSigmaPx: Float,
  val refractionScalePx: Float,
  val refractionHeightPx: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
)
```

`resolveRegularGeometryOptics()` applies the strength-resolved response, preserves zero baselines,
caps reach at the shortest side, and calculates sigma from the adapted unscaled blur radius.

- [ ] **Step 4: Use the same resolver in both call sites**

In `calculateLayerBounds()`, resolve radii and geometry from the unscaled `rect` and `Density`, then
reserve padding from resolved blur and displacement.

In `buildRenderParams()`, resolve from unscaled `context.size`, unscaled radii, and density before
applying `coordinates.scaleFactor`. Populate existing `GlassRenderParams` fields from scaled resolved
values. Do not add shader uniforms or render passes.

- [ ] **Step 5: Run focused module tests**

Run:

```bash
./gradlew :haze-glass:jvmTest
```

Expected: PASS.

## Task 7: Measure The Expanded Native Fixtures

**Files:**
- Create: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassCalibrationCases.kt`
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassImageMetrics.kt`
- Modify: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassIos26ReferenceMetricsTest.kt`

- [ ] **Step 1: Define the shared case catalog from the approved manifest**

```kotlin
internal enum class GlassCalibrationRole { Training, Holdout, Regression }

internal data class GlassCalibrationGeometry(
  val id: String,
  val page: String,
  val bounds: IntRect,
  val sizeDp: DpSize,
  val cornerRadiusDp: Dp,
  val role: GlassCalibrationRole,
)
```

Populate one entry per manifest surface. Baseline bounds remain unchanged. Calibration bounds are
the 3x pixel-scaled frames emitted by schema 2.

- [ ] **Step 2: Write a failing all-fixtures measurement test**

Replace the three-surface loop with:

```kotlin
@Test
fun regularReferenceMetrics_areDerivedFromImmutableResources() {
  GlassCalibrationCases.forEach { geometry ->
    GlassAppearance.entries.forEach { appearance ->
      val grid = resourceSnapshot("${geometry.page}-grid-${appearance.fileName}.png")
      val uniform = resourceSnapshot("${geometry.page}-uniform-${appearance.fileName}.png")
      val measured = measureGlassOpticalMetrics(
        grid = grid,
        uniform = uniform,
        surfaceBounds = geometry.bounds,
        backgroundBounds = IntRect(0, 0, 1080, 192),
        gridSpacingPx = 48,
      )
      println("${geometry.role} ${geometry.id} $appearance: $measured")
      check(measured.isFinite())
    }
  }
}
```

Map baseline page filenames to the preserved legacy names.

- [ ] **Step 3: Run the JVM reference test**

Run:

```bash
./gradlew :haze-screenshot-tests:jvmTest \
  --tests '*GlassIos26ReferenceMetricsTest.regularReferenceMetrics_areDerivedFromImmutableResources'
```

Expected: PASS and print metrics for every appearance and surface. Fail if a fixture, declared bound,
or source-detail region is invalid.

- [ ] **Step 4: Store immutable metric centers and derived bands**

Add every printed center to `Ios26RegularReferenceMetrics`, keyed by geometry ID and appearance.
Retain `Float.band()` so displacement uses a minimum 1px tolerance and normalized metrics use a
minimum `1/255` tolerance. Add an assertion that the center map keys exactly equal all declared
geometry/appearance pairs; no missing or extra calibration data is allowed.

Change `GlassReferenceKey` from an enum-backed `GlassSurface` field to a `geometryId: String` field,
and migrate the three existing baseline keys to IDs `capsule`, `card`, and `panel` before adding the
new matrix entries.

- [ ] **Step 5: Re-run the reference test**

Run the Step 3 command.

Expected: PASS with measured values matching stored immutable centers within `1e-6f`.

## Task 8: Fit Training Cases And Verify Holdouts

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInvariantSample.kt`
- Modify: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassDepthDesktopScreenshotTest.kt`
- Modify: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt`

- [ ] **Step 1: Add a shared renderer for one calibration geometry**

Create `GlassCalibrationSample(appearance, geometry, drawGrid)` using the geometry's `DpSize` and
`RoundedCornerShape(geometry.cornerRadiusDp)`. Set only `shape`; leave all optical values at
`GlassDefaults.style` so calibration exercises default adaptive behavior and shape ownership.

- [ ] **Step 2: Add training report and strict holdout assertions**

```kotlin
internal fun ScreenshotUiTest.measureGlassCalibrationCase(
  appearance: GlassAppearance,
  geometry: GlassCalibrationGeometry,
): GlassOpticalMetrics

internal fun ScreenshotUiTest.assertGlassCalibrationHoldouts() {
  GlassCalibrationCases.filter { it.role == GlassCalibrationRole.Holdout }.forEach { geometry ->
    GlassAppearance.entries.forEach { appearance ->
      val metrics = measureGlassCalibrationCase(appearance, geometry)
      val bands = Ios26RegularReferenceBands.getValue(
        GlassReferenceKey(appearance, geometry.id),
      )
      check(metrics.failures(bands, "${geometry.id} $appearance").isEmpty())
    }
  }
}
```

Keep training cases report-only while fitting. Existing capsule/card/panel remain enforced regression
validation but are not labeled holdouts. Add a separate `assertGlassCalibrationRegressionCases()`
that filters `Regression` and applies the same immutable bands.

- [ ] **Step 3: Run only Skiko training measurements before tuning**

Run:

```bash
./gradlew :haze-screenshot-tests:jvmTest \
  --tests '*GlassDepthDesktopScreenshotTest.glass_regularAdaptiveTrainingReport'
```

Expected: training metrics print. Do not run, inspect, or log holdout renderer results before the
coefficient fit is frozen.

- [ ] **Step 4: Fit only the training cases**

Adjust `RegularGeometryCoefficients` using the normalized training residual report. Keep the model
terms fixed to the independent size/aspect/roundness terms unless those terms cannot fit training;
only then activate the single predeclared size-roundness interaction. Do not inspect holdout residuals
between coefficient edits.

Stop fitting when every training metric lies within its iOS band. Record the final coefficient values
directly in the immutable common-Kotlin coefficient object.

- [ ] **Step 5: Run Skiko holdouts once after fitting**

Run:

```bash
./gradlew :haze-screenshot-tests:jvmTest \
  --tests '*GlassDepthDesktopScreenshotTest.glass_regularAdaptiveHoldouts' \
  --tests '*GlassDepthDesktopScreenshotTest.glass_regularAdaptiveRegressionCases'
```

Expected: PASS for every reserved holdout and existing regression case. If a holdout fails, reject
the model rather than tuning to that result; revisit model assumptions and capture design in the
spec before proceeding.

- [ ] **Step 6: Run Android holdouts**

Add matching desktop and Android test methods that call the shared assertions, then run:

```bash
./gradlew :haze-screenshot-tests:test
```

Expected: Android API 35 and Skiko pass the same predeclared bands.

- [ ] **Step 7: Add resize continuity coverage**

Render a fixed-aspect, fixed-roundness surface from 44dp through 220dp in 1dp increments. Measure
adjacent displacement, attenuation, and luma values and assert no delta exceeds the largest adjacent
delta observed between native training points plus the existing minimum metric tolerance.

Run the full screenshot test command again.

Expected: PASS with no breakpoint-like jump.

## Task 9: Documentation, API, Formatting, And Final Verification

**Files:**
- Modify: `docs/effects/glass.md`
- Regenerate: `haze-glass/api/api.txt`
- Modify generated screenshot goldens only where adaptive defaults intentionally changed output.

- [ ] **Step 1: Update Glass documentation**

Document:

```kotlin
glassEffect {
  shape = RoundedCornerShape(24.dp) // Authoritative material shape
  sizing = GlassSizing.Adaptive     // Default; explicit optical values are baselines
}

glassEffect {
  sizing = GlassSizing.Fixed        // Use literal blur/refraction baselines
}
```

Remove any redundant `Modifier.clip()` used only to establish the Glass boundary. Explain that an
outer clip is invisible to Glass and should share the same shape only when child content also needs
clipping. State the calibrated domain: shortest side 44..220dp, aspect 1..4, roundness 0..1.

- [ ] **Step 2: Apply formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected: PASS.

- [ ] **Step 3: Regenerate and check API**

Run:

```bash
./gradlew :haze-glass:apiDump :haze-glass:apiCheck
```

Expected: PASS; API includes `GlassSizing`, `GlassOptics.sizing`, `GlassDefaults.sizing`,
`GlassVisualEffect.sizing`, and `clearSizingOverride()`.

- [ ] **Step 4: Run native tooling verification**

Run:

```bash
xcodebuild test \
  -project internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj \
  -scheme CaptureApp \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.3.1'
internal/ios-glass-reference-capture/scripts/capture-references-test.sh
internal/ios-glass-reference-capture/scripts/capture-references.sh \
  --validate-only haze-screenshot-tests/src/commonTest/resources/glass/ios26
```

Expected: all PASS.

- [ ] **Step 5: Run module and visual verification**

Run:

```bash
./gradlew :haze-glass:check :haze-screenshot-tests:test
```

Expected: PASS.

- [ ] **Step 6: Inspect the final diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only the spec, plan, approved source/API/docs changes, native manifest/PNGs, and intentional
screenshot goldens are present; `git diff --check` prints no errors.
