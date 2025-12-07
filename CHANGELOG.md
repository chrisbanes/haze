# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2.0.0 WIP <small>TBD</small> { id="2.0.0" }

Major architectural refactor introducing a pluggable visual effects system for improved modularity and extensibility.

### Highlights

#### New `VisualEffect` Interface

Haze now uses a `VisualEffect` interface that separates the core effect infrastructure from specific effect implementations. This enables better separation of concerns, a smaller core module, and potential for custom visual effects in the future.

#### New `haze-blur` Module

All blur functionality has been extracted from the core `haze` module into a separate `haze-blur` module:

```kotlin
implementation("dev.chrisbanes.haze:haze:2.0.0")
implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
```

#### New `blurEffect {}` API

All blur-related properties now require a `blurEffect {}` wrapper:

```kotlin
Modifier.hazeEffect(state = hazeState) {
  blurEffect {
    blurRadius = 20.dp
    tints = listOf(HazeTint(...))
  }
}
```

### Breaking Changes

- **New module dependency:** Blur functionality now requires the `haze-blur` module
- **API nesting:** Blur properties (`blurRadius`, `tints`, `style`, `noiseFactor`, `progressive`, `mask`, etc.) now require `blurEffect {}` wrapper
- **Package changes:** Blur classes moved to `dev.chrisbanes.haze.blur` package:
  - `HazeStyle` → `dev.chrisbanes.haze.blur.HazeStyle`
  - `HazeTint` → `dev.chrisbanes.haze.blur.HazeTint`
  - `HazeProgressive` → `dev.chrisbanes.haze.blur.HazeProgressive`
  - `LocalHazeStyle` → `dev.chrisbanes.haze.blur.LocalHazeStyle`
- **Removed APIs:** `rememberHazeState(blurEnabled)` parameter removed (use `blurEffect { blurEnabled = ... }`)

### Migration

For detailed migration instructions, see the [Migration Guide](https://chrisbanes.github.io/haze/migrating-2.0/).

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.7.1...2.0.0

## 1.7.1 <small>2025-11-24</small> { id="1.7.1" }

### Changed
* Use LruCache rather than SieveCache in #827

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.7.0...1.7.1

## 1.7.0 <small>2025-11-10</small> { id="1.7.0" }

### Highlights

- 🆕 `forceInvalidateOnPreDraw` parameter on `HazeEffectScope` ([doc](https://chrisbanes.github.io/haze/latest/api/haze/dev.chrisbanes.haze/-haze-effect-scope/force-invalidate-on-pre-draw.html))
- Optimized noise texture handling on Android
- Only enable pre-draw listeners when we need to use them
- This should be the last of the v1.x releases (barring any blocking issues which crop up).

### Key dependencies
  - Kotlin 2.2.20
  - Compose Multiplatform 1.9.3
  - Jetpack Compose 1.9.4

### Changed
* Dependency updates in #752
* Add workaround for Kotlin Yarn errors in #764
* Bump compile and min SDK versions in #773
* Optimize areas and area offsets in #779
* Compose 1.9.x and Kotlin 2.2.20 in #791
* Reduce screenshot test thresholds in #806
* Only enable pre-draw listeners when necessary in #809
* Add a common pre-draw listener in #811
* Invalidate on position change in #812
* Update Android version references in platforms.md by @XIVIX134 in #814

### Added
* Add LeakCanary to sample in #769
* [Android] Optimize noise texture in #778
* Enable Kotlin explicit API in #790
* Introduce expandLayerBounds flag in #807
* Add forceInvalidateOnPreDraw flag in #810

### New Contributors
* @XIVIX134 made their first contribution in #814

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.10...1.7.0

## 1.6.10 <small>2025-08-13</small> { id="1.6.10" }

### Added
* Add clipToAreasBounds flag in #744

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.9...1.6.10

## 1.6.9 <small>2025-07-18</small> { id="1.6.9" }

### Fixed
* Fix transparent edges... again in #728

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.8...1.6.9

## 1.6.8 <small>2025-07-14</small> { id="1.6.8" }

### Changed
* Reduce unnecessary draw invalidations in #725

### Fixed
* Check if node is attached from updateEffect in #724

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.7...1.6.8

## 1.6.7 <small>2025-07-06</small> { id="1.6.7" }

### Fixed
* Workaround Compose draw exception on Android 9 in #707
* Fix bounded edge treatment not working in #710
* Fix Haze'd dialogs not blurring background content in #714

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.6...1.6.7

## 1.6.6 <small>2025-06-28</small> { id="1.6.6" }

### Changed
* Remove dependency on kotlinx-datetime in #704

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.5...1.6.6

## 1.6.5 <small>2025-06-27</small> { id="1.6.5" }

### Changed
* Add Dokka docs module in #687
* Hide internal functions in #691

### Fixed
* [RenderScript] Don't crash when blurRadius == 0 in #686
* Fix docs publish action in #690

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.4...1.6.5

## 1.6.4 <small>2025-06-06</small> { id="1.6.4" }

### Changed
* Refactor screenshot testing setup in #673
* Enable Dokka v2 in #676

### Fixed
* Fix overlapping content with layer transformations in #674

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.3...1.6.4

## 1.6.3 <small>2025-06-01</small> { id="1.6.3" }

### Highlights

🆕 MacOS CMP targets - Thanks to @YuKongA for adding 'native' MacOS targets. These are completely experimental (and untested), as per MacOS targets for CMP.

### Added
* Add macos targets by @YuKongA in #660

### Fixed
* Skip drawing if not attached in #666
* Skip layout calls if not attached in #668

### New Contributors
* @YuKongA made their first contribution in #660

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.2...1.6.3

## 1.6.2 <small>2025-05-20</small> { id="1.6.2" }

### Fixed
* Stop clipping the drawn layer size in #654
* Call drawContent safely in #655

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.1...1.6.2

## 1.6.1 <small>2025-05-19</small> { id="1.6.1" }

### Changed
* Clarify fallbackTint kdoc in #647

### Fixed
* Update outdated Dialog doc by @Skaldebane in #639
* Handle RenderScript not initializing in #645

### New Contributors
* @Skaldebane made their first contribution in #639

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.0...1.6.1

## 1.6.0 <small>2025-05-12</small> { id="1.6.0" }

### Highlights

#### 🤖 Support for all versions of Android

Haze now supports blurring for all versions of Android, using RenderScript underneath. The new [platforms](https://chrisbanes.github.io/haze/latest/platforms/#android) documentation has all of the details.

#### 🎨 Foreground (content) blurring

Haze always been a library which enables background blurring. There are times where you need to blur the foreground content though, which Haze has always left to `Modifier.blur`. With the addition of older Android support, and all of the other features Haze supports, it makes sense for Haze to support both scenarios. New in this release is foreground blurring ([doc](https://chrisbanes.github.io/haze/latest/usage/#foreground-blurring)).

#### 📐 Blurred edge treatment

With the addition of foreground blurring, the need to be able to customise how content is blurred at the edges is useful. This release contains a new `blurredEdgeTreatment` on the effect scope, which works the same way as `Modifier.blur`.

### Added
* Add RenderScript backed blur implementation in #590
* Add `HazeState.blurEnabled` in #602
* Update docs for 1.6.0 in #603
* Add new platform docs in #605
* Add trace functions in #615
* Add support for content blurring in #616
* Add blurredEdgeTreatment property in #625

### Changed
* Remove the final withSaveLayer call in #601
* Tidy up HazeProgressive drawing in #604
* Scale the noise texture appropriately in #613
* Tweaks to Progressive support for RenderScript in #621
* Compose Multiplatform 1.8.0 in #634

### Fixed
* Fix masking for content blurring in #619
* Fix input scaled content being 1px smaller in #622
* Noise fixes for Android in #626
* Fix clamping blur effect on edges in #629
* [RenderScript] Fix tint and noise size being incorrect in #632

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.4...1.6.0

## 1.6.0-rc02 <small>2025-05-06</small> { id="1.6.0-rc02" }

### Changed
* Compose Multiplatform 1.8.0 in #634

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.0-rc01...1.6.0-rc02

## 1.6.0-rc01 <small>2025-05-05</small> { id="1.6.0-rc01" }

### Fixed
* [RenderScript] Fix tint and noise size being incorrect in #632

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.0-beta03...1.6.0-rc01

## 1.6.0-beta03 <small>2025-05-04</small> { id="1.6.0-beta03" }

### Added
* Add blurredEdgeTreatment property in #625

### Changed
* Noise fixes for Android in #626

### Fixed
* Fix clamping blur effect on edges in #629

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.0-beta02...1.6.0-beta03

## 1.6.0-beta02 <small>2025-04-29</small> { id="1.6.0-beta02" }

### Added
* Add trace functions in #615
* Add support for content blurring in #616

### Changed
* Scale the noise texture appropriately in #613
* Tweaks to Progressive support for RenderScript in #621

### Fixed
* Fix masking for content blurring in #619
* Fix input scaled content being 1px smaller in #622

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.6.0-beta01...1.6.0-beta02

## 1.6.0-beta01 <small>2025-04-23</small> { id="1.6.0-beta01" }

### Highlights

#### 🆕 Support for all Android devices

We now support for older Android versions, using an implementation of blurring which uses [RenderScript](https://developer.android.com/guide/topics/renderscript/compute). You can read the new [Platforms](https://chrisbanes.github.io/haze/latest/platforms/) documentation for more information on how to try it.

Big thanks to @desugar-64 for the help on this.

#### Android 12 is now enabled by default

We have now identified a fix for the issues which meant that blurring was disabled on Android 12, and it is now enabled by default.

### Added
* Add RenderScript backed blur implementation in #590
* Add `HazeState.blurEnabled` in #602
* Update docs for 1.6.0 in #603
* Add new platform docs in #605

### Changed
* Remove the final withSaveLayer call in #601
* Tidy up HazeProgressive drawing in #604

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.4...1.6.0-beta01

## 1.5.4 <small>2025-04-17</small> { id="1.5.4" }

### Changed
* Re-use BlurEffect instances in #600

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.3...1.5.4

## 1.5.3 <small>2025-04-13</small> { id="1.5.3" }

### Changed
* Reduce scope of consumer R8 rules in #579
* Extract BlurEffect interface in #589
* Use `Sample` as routes directly by @Goooler in #593

### Fixed
* Fix links on docs site in #581
* Fix haze effects inside of of pagers by @Monkopedia in #594

### New Contributors
* @Goooler made their first contribution in #593
* @Monkopedia made their first contribution in #594

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.2...1.5.3

## 1.5.2 <small>2025-03-23</small> { id="1.5.2" }

### Changed
* Migrate samples to AndroidX Navigation in #563
* Disable context-receivers compiler flag in #564
* Update baseline profile in #568

### Fixed
* Fix flicker when using AndroidX Navigation in #566
* Fix wrong blur rect after rotating in #567

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.1...1.5.2

## 1.5.1 <small>2025-03-19</small> { id="1.5.1" }

### Fixed
* Fix compilation failure of iOS sample by @keta1 in #553
* [Skiko] Fix noise effect not being masked for progressive effect in #555

### New Contributors
* @keta1 made their first contribution in #553

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.5.0...1.5.1

## 1.5.0 <small>2025-03-06</small> { id="1.5.0" }

### Highlights

- Optimised blurring shader for progressive effects. Thanks to @Kyant0 in #537
- HazeProgressive.Brush. You can now supply completely custom masks (via a shader) for progressive effects.
- Added `HazeLogger`. You can now turn on Haze's internal logging. Handy for debugging and reporting issues.
- Various bug fixes. Thanks to everyone who reported issues.

### Added
* Add iOS sample in #532
* Add HazeLogger in #534
* Add HazeProgressive.Brush in #542
* Add HazeProgressive.forShader in #546

### Changed
* Optimize Gaussian blur shader by @Kyant0 in #537

### Fixed
* Fix progressive effect being drawn incorrectly  in #538
* Don't create GraphicsLayer if dimension is zero in #540
* Fix support for nested Haze hierarchies in #545
* Fix baseline profile generation in #550

### New Contributors
* @Kyant0 made their first contribution in #537

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.4.0...1.5.0

## 1.4.0 <small>2025-02-28</small> { id="1.4.0" }

### Added
* Add HazeDialog composable in #513

### Changed
* Bump Android Compile SDK to 35 in #512
* Re-introduce expanded layer size in #522
* Use high-precision types in blurring shader in #530

### Fixed
* Fix default style to include blurring in #527
* Fix progressive effect on certain OEMs in #528

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.3.1...1.4.0

## 1.3.1 <small>2025-02-09</small> { id="1.3.1" }

### Changed
* Move Lifecycle usage to androidMain in #500
* Only use positionOnScreen on Android in #503

### Fixed
* Release GraphicsLayer when stopped in #499

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.3.0...1.3.1

## 1.3.0 <small>2025-01-27</small> { id="1.3.0" }

### Added
* Add Brush support to HazeTint in #481
* Add Bottom Sheet sample in #485
* Add support for radial progressive effects in #491
* Adds section on using Haze in deep UI hierarchies. by @StylingAndroid in #494

### Changed
* Move all samples to `shared` in #484

### Fixed
* Fix alpha property changes not invalidating in #490

### New Contributors
* @StylingAndroid made their first contribution in #494

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.2.2...1.3.0

## 1.2.2 <small>2025-01-16</small> { id="1.2.2" }

### Fixed
* Add workaround for positionOnScreen throwing in #472
* [Skia] Fix hazeSource content changes not updating hazeEffects in #476

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.2.1...1.2.2

## 1.2.1 <small>2025-01-13</small> { id="1.2.1" }

### Fixed
* Fix Haze not drawing any effects in previews in #470
* Fix issues with missing effects in LazyLayouts in #469

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.2.0...1.2.1

## 1.2.0 <small>2025-01-09</small> { id="1.2.0" }

### Highlights

#### API renames

I have renamed a number of the APIs in this release to better reflect what they actually do these days:

- `Modifier.haze` -> `Modifier.hazeSource`
- `Modifier.hazeChild` -> `Modifier.hazeEffect`
- `HazeChildScope` -> `HazeEffectScope`

I've kept the old APIs around and deprecated them for easy migration.

#### Overlapping blurred areas

Haze now supports different `hazeEffect` areas which overlap. See [here](https://chrisbanes.github.io/haze/1.2.0/usage/#overlapping-blurred-layouts) for more information.

#### Versioned documentation

A small quality of life change, but the documentation website is now versioned: https://chrisbanes.github.io/haze/

### Changed
* Stop using synchronized lazy in #446
* Add support for multiple Haze nodes attached to a HazeState in #441
* Rename modifiers for clarity in #452
* [Sample] Hook up back button on Android in #459
* Remove expanded layer size in #461
* Small tidy ups ready for release in #463

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.1.1...1.2.0

## 1.1.1 <small>2024-12-04</small> { id="1.1.1" }

### Changed
* Use onPlaced for screenshot tests in #433

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.1.0...1.1.1

## 1.1.0 <small>2024-12-02</small> { id="1.1.0" }

### Highlights

🆕 Input Scale - A new feature in this release is being able to set the [input scale](https://chrisbanes.github.io/haze/latest/usage/#input-scale), allowing you to downscale content for _potential_ performance gains.

### Added
* Add inputScale in #416
* Improve HazeInputScale API in #423
* Add automatic HazeInputScale in #427

### Changed
* Reduce variance in benchmarks in #418
* More benchmark consistency work in #424
* Remove noise scaling on Android in #428

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.0.2...1.1.0

## 1.0.2 <small>2024-11-15</small> { id="1.0.2" }

### Highlights

🆕 Ability to control where the blurring effect is used - You can now control where the blur effect is used. Specifically for Android, there's more information [here](https://chrisbanes.github.io/haze/faq/#what-versions-of-android-does-haze-work-on).

### Added
* Add blurEnabled on HazeChildScope property in #408

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.0.1...1.0.2

## 1.0.1 <small>2024-11-11</small> { id="1.0.1" }

### Added
* Add `preferPerformance` flag on HazeProgressive in #401

### Changed
* Cache RenderEffects in #402

### Fixed
* Implement fallback for HazeProgressive in #400

**Full Changelog**: https://github.com/chrisbanes/haze/compare/1.0.0...1.0.1

## 1.0.0 <small>2024-11-07</small> { id="1.0.0" }

This is the first stable 1.0.0 release! 🎉

For a full list of changes since 0.7.3, see the [migration guide](https://chrisbanes.github.io/haze/migrating-1.0/).

### New Contributors
* @Sanlorng made their first contribution in #306

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.7.3...1.0.0

## 0.9.0-rc03 <small>2024-11-02</small> { id="0.9.0-rc03" }

### Changed
* Remove LayoutAwareModifierNode and onPlaced again in #383

### Fixed
* Fix crashes caused by blurRadius of 0px in #382

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-rc02...0.9.0-rc03

## 0.9.0-rc02 <small>2024-10-26</small> { id="0.9.0-rc02" }

### Changed
* Doc updates for Haze v0.9 in #369
* Upgrade to Robolectric 4.14-beta-1 in #370
* Start using LayoutAwareModifierNode and onPlaced again in #372

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-rc01...0.9.0-rc02

## 0.9.0-rc01 <small>2024-10-21</small> { id="0.9.0-rc01" }

### Highlights

Performance improvements for progressive blur - On platforms which support runtime shaders (everything other than Android SDK < 33), progressive is ~1.9x faster than before, through the usage of a new (custom) runtime shader.

### Added
* Add runtime shader for progressive blur in #368

### Changed
* Small micro optimizations in #364

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-beta04.1...0.9.0-rc01

## 0.9.0-beta04.1 <small>2024-10-16</small> { id="0.9.0-beta04.1" }

### Fixed
* Fix defaults for blurRadius and noiseFactor in #361

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-beta04...0.9.0-beta04.1

## 0.9.0-beta04 <small>2024-10-16</small> { id="0.9.0-beta04" }

### Highlights

#### Compose Multiplatform 1.7.0

CMP 1.7.0 has gone stable. Go and upgrade.

#### Progressive blurs (aka gradient blurs)

We now have access to progressive blurring with an API very similar to the `Brush` gradient APIs.

> [!CAUTION]
> The performance of progressive blurring is untested as yet, but for sure it's going to be slower than without. I also haven't put any performance work into this as yet. That will come for the next release (hopefully rc01).

#### Tweaked styling APIs (again)

The styling APIs have changed again (sorry about that), but I'm feeling much better about the new API. We now have a the LocalHazeStyle composition local (for global styling), `style` parameter on hazeChild for node-specific, and then the individual properties on hazeChild `block`.

### Added
* Progressive blur in #346
* Add ExoPlayer sample in #356

### Changed
* More benchmark tests (and perf improvements) in #349
* Throw error on descendant layouts in #357
* Style improvements for 0.9.x in #360

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-beta03...0.9.0-beta04

## 0.9.0-beta03 <small>2024-10-04</small> { id="0.9.0-beta03" }

### Changed
* Only use a GraphicsLayer on Android on hw-accel canvases in #341
* Remove the unused defaultStyle vars in #342

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-beta02...0.9.0-beta03

## 0.9.0-beta02 <small>2024-10-01</small> { id="0.9.0-beta02" }

### Highlights

- We now depend on Compose Multiplatform 1.7.0-rc01
- Lots of API tweaks!

### Added
* Add alpha param to hazeChild in #313

### Changed
* Tidy up API for v0.9.0 in #330
* Disable invalidation tick in #331

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-beta01...0.9.0-beta02

## 0.9.0-beta01 <small>2024-09-05</small> { id="0.9.0-beta01" }

### Highlights

- New version of `hazeChild` which takes lambda parameters. Should be a lot more efficient if you need to animate properties.
- Using Jetpack Compose 1.7.0 (GA)
- Using Compose Multiplatform 1.7.0-beta01
- (New) FluentMaterials class, which mimics Windows blurring styles. Thanks @Sanlorng!

### Added
* Add FluentMaterials by @Sanlorng in #306
* Add lambda version of HazeChild in #309

### New Contributors
* @Sanlorng made their first contribution in #306

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha08...0.9.0-beta01

## 0.9.0-alpha08 <small>2024-08-21</small> { id="0.9.0-alpha08" }

### Added
* Add Brush suppport for tints in #298

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha07...0.9.0-alpha08

## 0.9.0-alpha07 <small>2024-08-15</small> { id="0.9.0-alpha07" }

### Fixed
* Add workaround for invalidations not happening on Skia backed platforms in #296

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha06...0.9.0-alpha07

## 0.9.0-alpha06 <small>2024-08-07</small> { id="0.9.0-alpha06" }

### Fixed
* Fix clipping size in #288

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha05...0.9.0-alpha06

## 0.9.0-alpha05 <small>2024-08-01</small> { id="0.9.0-alpha05" }

### Changed
* Remove shape clipping in #287

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha04...0.9.0-alpha05

## 0.9.0-alpha04 <small>2024-07-23</small> { id="0.9.0-alpha04" }

### Changed
* Implement better edges in #275
* Invalidate the render effect on size changes in #278

### Fixed
* Fix clipping for rectangles in #279

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha03...0.9.0-alpha04

## 0.9.0-alpha03 <small>2024-07-19</small> { id="0.9.0-alpha03" }

### Highlights

🆕 Masks - You can now supply a `Brush` to `hazeChild` which will act as a mask. The mask allows things like gradient blurs, by supplying a `Brush.verticalGradient` or similar.

### Added
* Add ability to provide mask for blurred areas in #267

### Changed
* Revert usage of Poko in #268
* Turn off auto invalidation in #271
* Revert "Update plugin mavenpublish to v0.29.0" in #273

### Fixed
* Throw error when background color is not specified in #265
* Fix samples crashing in #270
* Fix content node being placed at incorrect position in #272

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha02...0.9.0-alpha03

## 0.9.0-alpha02 <small>2024-07-17</small> { id="0.9.0-alpha02" }

### Fixed
* Fix NPE from missing content layer in #263

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.9.0-alpha01...0.9.0-alpha02

## 0.9.0-alpha01 <small>2024-07-16</small> { id="0.9.0-alpha01" }

For more information, see https://chrisbanes.github.io/haze/migrating-0.9/

### Changed
* [next] Initial upgrade to Compose Multiplatform 1.7.0-alpha in #250
* New rendering mode in #259
* Add some docs for 0.9 in #261

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.7.3...0.9.0-alpha01

## 0.7.3 <small>2024-07-08</small> { id="0.7.3" }

### Changed
* Integrate GlobalPositionAwareModifierNode into Haze modifiers in #246
* Add ListOverImage sample in #248

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.7.2...0.7.3

## 0.7.2 <small>2024-06-17</small> { id="0.7.2" }

### Changed
* Web updates (JS/Canvas) by @nevrozza in #228

### Fixed
* Fix sample code in usage docs by @lhoyong in #211
* Fix typo in naming of "lorem ipsum" placeholder value by @twyatt in #215
* Fix CI and docs build in #237

### New Contributors
* @lhoyong made their first contribution in #211
* @twyatt made their first contribution in #215
* @nevrozza made their first contribution in #228

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.7.1...0.7.2

## 0.7.1 <small>2024-05-01</small> { id="0.7.1" }

### Changed
* Remove usage of LFS in #198
* Kotlin 1.9.23 + CMP 1.6.2 in #199
* Don't include test sources in API files in #204

### Added
* Add scroll behavior to Scaffold sample in #197

### Fixed
* Fix glitch with clipping content out in #205
* Reset state when LazyLayout reuses node in #203
* [Android] Fix dialog being offset for non edge-to-edge windows in #206
* Fix content clip path at edges in #207

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.7.0...0.7.1

## 0.7.0 <small>2024-04-12</small> { id="0.7.0" }

### Changed
* Enable Robolectric hardware rendering in #147
* Tidy up settings.gradle.kts in #181
* Remove deprecated library and APIs in #182

### Fixed
* Fix HazeChild being conditionally added in #174

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.6.2...0.7.0

## 0.6.2 <small>2024-03-10</small> { id="0.6.2" }

### Fixed
* Fix corner radius not applying to the correct corners by @mikepenz in #162

### Added
* [Test] Add test-case to verify rounded corners by @mikepenz in #163

### New Contributors
* @mikepenz made their first contribution in #162

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.6.1...0.6.2

## 0.6.1 <small>2024-03-09</small> { id="0.6.1" }

### Changed
* Migrate samples to use Coil in #154

### Added
* Add Kotlin WebAssembly support in #153

### Fixed
* [Android] Fix empty bounds handling in #160

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.6.0...0.6.1

## 0.6.0 <small>2024-03-06</small> { id="0.6.0" }

### Highlights
- [iOS and Desktop] Fix edges not being blurred uniformly (#142). Big thanks to @Dynaruid for contributing this!
- [Android] Fix artifacts on rounded edges (#132)

### Changed
* Use Poppins in screenshot testing in #144

### Fixed
* Fixed where the edges appeared as if they were not properly blurred by @Dynaruid in #142
* Fix Android clip/rounding issue in #133
* Correct default value for Noise in Usage.md by @dev-weiqi in #149

### New Contributors
* @Dynaruid made their first contribution in #142
* @dev-weiqi made their first contribution in #149

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.5.4...0.6.0

## 0.5.4 <small>2024-02-27</small> { id="0.5.4" }

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.5.3...0.5.4

## 0.5.3 <small>2024-02-18</small> { id="0.5.3" }

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.5.2...0.5.3

## 0.5.2 <small>2024-02-14</small> { id="0.5.2" }

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.5.1...0.5.2

## 0.5.1 <small>2024-02-07</small> { id="0.5.1" }

### Fixed
* Fix Android noise texture changing luminance in #126

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.5.0...0.5.1

## 0.5.0 <small>2024-01-31</small> { id="0.5.0" }

### Highlights

- 🏃 Optimizations and performance increases (see [performance docs](https://chrisbanes.github.io/haze/performance/))
- 🌫️ New Materials library (see [materials docs](https://chrisbanes.github.io/haze/materials/))
- 🆕 Tidied up styling API
- ✨ Updated to Compose Multiplatform 1.6.0-beta01 (and Jetpack Compose 1.6.0)
- 🔁 Merged haze and haze-jetpack-compose
- 📜 (Android) New baseline profiles bundled in library. Thanks to @simonlebras for this.

### Changed
* Merge :haze and :haze-jetpack-compose in #106
* Lots of small fixes in #113
* Optimize Android implementations in #115

### Added
* Baseline profile by @simonlebras in #93
* Baseline Profile tweaks in #95
* Add HazeStyle class in #110
* Add Haze Materials in #111
* Add macrobenchmark tests in #116

### Fixed
* Fix Small Typo in Docs by @jorgedotcom in #100

### New Contributors
* @simonlebras made their first contribution in #93
* @jorgedotcom made their first contribution in #100

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.5...0.5.0

## 0.4.5 <small>2024-01-12</small> { id="0.4.5" }

### Changes
* Android Previews (and screenshot tests) now display a scrim, rather than nothing.
* Haze children from different Android windows (i.e. Dialogs) now work

### Fixed
* Calculate areas in screen coordinates in #88
* Merge Android implementations in #91

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.4...0.4.5

## 0.4.4 <small>2024-01-08</small> { id="0.4.4" }

### Fixed
* Fix hazeChild tint not updating on Android base in #83

### Added
* Add screenshot tests to verify `hazeChild` tint changes in #85

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.3...0.4.4

## 0.4.3 <small>2024-01-05</small> { id="0.4.3" }

### Fixed
* Avoid using `RenderNode` blur implementation on API 31 for now. See #77
* Transparent tints now work everywhere.

### Added
* Allow setting a tint on each `hazeChild`

### Changed
* Fix transparent tints causing crash on Android in #74
* Skip RenderNode impl on API 31 in #79
* Allow override tint on `hazeChild` in #81

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.2...0.4.3

## 0.4.2 <small>2024-01-03</small> { id="0.4.2" }

### Fixed
* Android minimum SDK is now 21 to match Compose.
* `haze` and `hazeChild` causing previews to crash.

### Changed
* Drop Android minimum sdk version to 21 in #61
* No-op on Android impl when LocalInspectionMode is true in #72

### Added
* Add screenshot testing with Roborazzi in #70

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.1...0.4.2

## 0.4.1 <small>2023-12-12</small> { id="0.4.1" }

### Fixed
* Apply Jetpack Compose Shape to HazeChild.kt on init by @almozavr in #55
* Port #55 to :haze in #56

### New Contributors
* @almozavr made their first contribution in #55

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.4.0...0.4.1

## 0.4.0 <small>2023-12-11</small> { id="0.4.0" }

### New API!

I have broken the existing API, but hopefully you can see why. You no longer need to manually calculate bounds. `HazeState` + `Modifier.haze()` + `Modifier.hazeChild()` is all you need.

``` kotlin
val hazeState = remember { HazeState() }

Box {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .haze(
        // Pass it the HazeState we stored above
        state = hazeState,
        // Need to provide background color of the content
        backgroundColor = MaterialTheme.colorScheme.surface,
      ),
  ) {
    // todo
  }

  Text(
    text = "Content will be blurred behind this",
    modifier = Modifier
      // We use hazeChild on anything where we want the background
      // blurred. We can even provide a shape.
      .hazeChild(
        state = hazeState,
        shape = RoundedCornerShape(16.dp),
      ),
  )
}
```

### Changed
* Update to Kotlin 1.9.20 in #27
* API refactor in #36
* Small API tidy-ups in #53
* More API tweaks in #54

### Fixed
* Fix position issue by @MohamedRejeb in #52

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.3.1...0.4.0

## 0.3.1 <small>2023-11-10</small> { id="0.3.1" }

Small hotfix. We now build Android and JVM targets outputting Java 11 bytecode.

### Fixed
* Fix JVM targets in #28

**Full Changelog**: https://github.com/chrisbanes/haze/compare/0.3.0...0.3.1

## 0.3.0 <small>2023-11-08</small> { id="0.3.0" }

### Added
* 🆕 Round Rect support - We now have support for rounded rectangles, thanks to @MohamedRejeb.
* Add RoundRect support by @MohamedRejeb in #16

### Changed
* Apply noise texture using HARD_LIGHT in #21
* Use Metalava to track public API in #23
* Provide more customization in the API in #22

### New Contributors
* @MohamedRejeb made their first contribution in #16

**Full Changelog**: https://github.com/chrisbanes/haze/compare/v0.2.0...0.3.0

## 0.2.0 <small>2023-11-01</small> { id="0.2.0" }

### Changed
* Update Android sample to use haze-jetpack-compose in #9
* Use NodeElement instead of composed by @qdsfdhvh in #13
* Migrate Android implementations to Modifier.Node in #15

### Added
* Add noise to Android implementation in #12

### Fixed
* Fix Skiko backed Haze not displaying multiple Rects in #17

### New Contributors
* @qdsfdhvh made their first contribution in #13

**Full Changelog**: https://github.com/chrisbanes/haze/compare/v0.1.0...v0.2.0

## 0.1.0 <small>2023-10-30</small> { id="0.1.0" }

First release!

**Full Changelog**: https://github.com/chrisbanes/haze/commits/v0.1.0
