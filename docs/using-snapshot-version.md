# Using a Snapshot Version of the Library

If you would like to depend on the cutting edge version of the library, you can use the [snapshot versions][snap] that are published to
[Sonatype Central Repository](https://central.sonatype.org/)'s snapshot repository. These are updated on every commit to `main`.
All Haze artifacts from a snapshot build use the same version.

<p data-haze-snapshot-version-container hidden aria-live="polite">
  Current snapshot version: <code data-haze-snapshot-version></code>
</p>

To do so:

```kotlin
repositories {
    // ...
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

dependencies {
    // Replace XXX-SNAPSHOT with the latest version from the metadata link above.
    // Core infrastructure (required)
    implementation("dev.chrisbanes.haze:haze:XXX-SNAPSHOT")

    // For blur effects (most users will need this)
    implementation("dev.chrisbanes.haze:haze-blur:XXX-SNAPSHOT")

    // For Glass effects
    implementation("dev.chrisbanes.haze:haze-glass:XXX-SNAPSHOT")
}
```

[snap]: https://central.sonatype.com/repository/maven-snapshots/dev/chrisbanes/haze/haze/maven-metadata.xml
