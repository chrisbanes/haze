[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.haze/snapper)](https://search.maven.org/search?q=g:dev.chrisbanes.haze)

![](assets/header.png)

## Deprecated

Snapper is now deprecated, due to it's functionality being replaced by [`SnapFlingBehavior`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/snapping/SnapFlingBehavior) in Jetpack Compose 1.3.0.

The `SnapFlingBehavior` API is very similar to Snapper, so migration should be very easy. I haven't provided an automatic migration path, as I feel that it's important to learn the new API by performing the migration yourself.

## Library

Snapper is a library which brings snapping to the Compose scrolling layouts (currently LazyColumn and LazyRow):

=== "Sample app"

    <video width="100%" controls loop style="max-width: 600px">
        <source src="assets/demo.mp4" type="video/mp4">
        Your browser does not support the video tag.
    </video>


=== "Tivi"

    <video width="100%" controls loop style="max-width: 600px">
        <source src="assets/tivi.mp4" type="video/mp4">
        Your browser does not support the video tag.
    </video>


The basic usage looks like so:

``` kotlin
val lazyListState = rememberLazyListState()

LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(lazyListState),
) {
    // content
}
```

## API Summary

The API is generally split into a few things:

- [SnapperFlingBehavior](api/lib/dev.chrisbanes.haze/-snapper-layout-info/), which is what apps provide to scrollable containers.
- A number of [remember functions](api/lib/dev.chrisbanes.haze/remember-snapper-fling-behavior.html) allowing easy use of `SnapperFlingBehavior` from composables.
- [SnapperFlingLayoutInfo](api/lib/dev.chrisbanes.haze/-snapper-layout-info/), which is an facade class allowing `SnapperFlingBehavior` to interact with different scrollable container state in a generic way.
- Implementations of `SnapperFlingLayoutInfo` for easy integration, such as [LazyListFlingLayoutInfo](api/lib/dev.chrisbanes.haze/-lazy-list-snapper-layout-info/).

For examples, refer to the [samples](https://github.com/chrisbanes/snapper/tree/main/sample/src/main/java/dev/chrisbanes/snapper/sample).

## Download

[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.haze/snapper)](https://search.maven.org/search?q=g:dev.chrisbanes.haze)

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "dev.chrisbanes.haze:haze:<version>"
}
```

Snapshots of the development version are available in Sonatype's [snapshots repository][snap]. These are updated on every commit.

## License

```
Copyright 2023 Chris Banes

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

[compose]: https://developer.android.com/jetpack/compose
[snap]: https://oss.sonatype.org/content/repositories/snapshots/dev/chrisbanes/haze/haze/
