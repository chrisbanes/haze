# Upgrade AGP 8.13.2 → 9.2.1

## Goal

Upgrade Android Gradle Plugin from 8.13.2 to 9.2.1 to stay current before 8.x falls out of support.

## Approach

Version bump + fix-forward. Bump the version, build, iteratively fix whatever breaks.

## Current State

| Component | Version |
|-----------|---------|
| AGP | 8.13.2 |
| Gradle | 9.5.1 |
| Kotlin | 2.3.21 |
| compileSdk/targetSdk | 36 |
| minSdk | 23 |
| JDK toolchain | 21 |
| `org.gradle.android.cache-fix` | 3.0.3 |

## Changes

### 1. Version bump

**File**: `gradle/libs.versions.toml`

Change `agp = "8.13.2"` to `agp = "9.2.1"`.

### 2. Build logic migration (`gradle/build-logic/`)

The main risk area. `Android.kt` uses `BaseExtension` which AGP has been deprecating:

```kotlin
// Current (Android.kt)
import com.android.build.gradle.BaseExtension

private fun Project.android(action: BaseExtension.() -> Unit) =
    extensions.configure<BaseExtension>(action)
```

If `BaseExtension` is removed or its APIs changed in AGP 9, migrate to `CommonExtension` or the new type-safe DSL. Key APIs used:
- `compileSdkVersion()` → may need `compileSdk`
- `defaultConfig { minSdk; targetSdk }`
- `compileOptions { sourceCompatibility; targetCompatibility }`

Also check `AndroidComponentsExtension` usage:
- `beforeVariants(selector().withBuildType("release"))` — likely stable
- `HasUnitTestBuilder` — likely stable

### 3. Gradle properties cleanup

**File**: `gradle.properties`

- `android.suppressUnsupportedCompileSdk=34` — review if still needed or update value
- `kotlin.mpp.androidSourceSetLayoutVersion=2` — v2 is now the default; may be removable
- `android.defaults.buildfeatures.*` flags — verify still valid property names

### 4. Cache-fix plugin compatibility

**File**: `gradle/libs.versions.toml` — `cacheFixPlugin` version `3.0.3`

The plugin claims "AGP 7.0+" support but only tests through latest patch of each minor release. AGP 9.x is untested. If the plugin fails to apply or causes build errors:
- First: try bumping to the latest version
- If no compatible version exists: remove the plugin (AGP 9 may have fixed the underlying cache issues)

### 5. Screenshot test references

AGP version changes can affect rendering (font hinting, anti-aliasing). After the upgrade:
- Run `./gradlew :haze-screenshot-tests:test` to check for diffs
- If intentional rendering changes: regenerate with `./gradlew :haze-screenshot-tests:recordRoborazzi`

## Verification

1. `./gradlew assembleDebug` — must compile cleanly
2. `./gradlew testDebug` — unit tests pass
3. `./gradlew :haze-screenshot-tests:test` — screenshot tests pass (or regenerate if rendering changed)
4. `./gradlew spotlessApply` — formatting clean
5. `./gradlew build` — full build passes

## Files to modify

- `gradle/libs.versions.toml` — version bump
- `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/Android.kt` — if `BaseExtension` API changed
- `gradle.properties` — cleanup stale properties
- Screenshot reference images — if rendering changed

## Scope

- AGP version bump only
- No new AGP 9 features adopted (deferred to future work)
- No unrelated dependency updates
