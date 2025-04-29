# Using a Snapshot Version of the Library

If you would like to depend on the cutting edge version of the library, you can use the [snapshot versions][snap] that are published to
[Sonatype Central Repository](https://central.sonatype.org/)'s snapshot repository. These are updated on every commit to `main`.

To do so:

```kotlin
repositories {
    // ...
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

dependencies {
    // Check the latest SNAPSHOT version from the link above
    implementation("dev.chrisbanes.haze:haze:XXX-SNAPSHOT")
}
```

 [snap]: https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/dev/chrisbanes/haze/haze/
