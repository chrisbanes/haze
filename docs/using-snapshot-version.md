# Using a Snapshot Version of the Library

If you would like to depend on the cutting edge version of the Snapper
library, you can use the [snapshot versions][snap] that are published to
[Sonatype OSSRH](https://central.sonatype.org/)'s snapshot repository. These are updated on every commit to `main`.

To do so:

```groovy
repositories {
    // ...
    maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
}

dependencies {
    // Check the latest SNAPSHOT version from the link above
    classpath 'dev.chrisbanes.haze:haze:XXX-SNAPSHOT'
}
```

You might see a number of different versioned snapshots. If we use an example:

* `0.3.0-SNAPSHOT` is a build from the `main` branch, and depends on the latest tagged Jetpack Compose release (i.e. [alpha03](https://developer.android.com/jetpack/androidx/releases/compose#1.0.0-alpha03)).

These builds are updated regularly, but there's no guarantee that we will create one for a given snapshot number.

*Note:* you might also see versions in the scheme `x.x.x.ui-YYYY-SNAPSHOT`. These are the same, just using an older suffix.


 [snap]: https://oss.sonatype.org/content/repositories/snapshots/dev/chrisbanes/haze/