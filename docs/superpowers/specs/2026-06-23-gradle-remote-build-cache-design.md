# Gradle Remote Build Cache Design

## Goal

Add optional support for a Gradle remote build cache. The cache endpoint and credentials are supplied through Gradle project properties, which CI can inject via `ORG_GRADLE_PROJECT_*` environment variables.

## Property Contract

The build uses these Gradle properties:

- `remoteBuildCacheUrl`
- `remoteBuildCacheUsername`
- `remoteBuildCachePassword`
- `remoteBuildCacheEnabled`
- `remoteBuildCachePush`

CI injects them with environment variables:

```yaml
env:
  ORG_GRADLE_PROJECT_remoteBuildCacheUrl: ${{ secrets.GRADLE_REMOTE_CACHE_URL }}
  ORG_GRADLE_PROJECT_remoteBuildCacheUsername: ${{ secrets.GRADLE_REMOTE_CACHE_USERNAME }}
  ORG_GRADLE_PROJECT_remoteBuildCachePassword: ${{ secrets.GRADLE_REMOTE_CACHE_PASSWORD }}
  ORG_GRADLE_PROJECT_remoteBuildCacheEnabled: true
  ORG_GRADLE_PROJECT_remoteBuildCachePush: true
```

The secret names can remain generic. The Gradle property names are also generic because the properties describe Gradle remote cache behavior rather than Haze-specific behavior.

## Gradle Settings Behavior

The root `settings.gradle.kts` owns remote cache configuration through a `buildCache` block.

Local cache behavior remains unchanged. The existing `org.gradle.caching=true` property continues to opt the build into Gradle caching, and the local cache remains available whether or not the remote cache is configured.

Remote cache participation is explicit:

- `remoteBuildCacheEnabled=true` enables remote cache reads.
- `remoteBuildCachePush=true` enables remote cache writes.
- Remote writes require both `remoteBuildCacheEnabled=true` and `remoteBuildCachePush=true`.
- If `remoteBuildCacheEnabled` is absent or any value other than `true`, the remote cache is disabled.
- If remote caching is enabled but `remoteBuildCacheUrl`, `remoteBuildCacheUsername`, or `remoteBuildCachePassword` is missing, the remote cache is disabled.

The Gradle configuration should use `providers.gradleProperty(...)` for all remote cache properties. It should not read cache credentials directly from `System.getenv(...)`; implicit Gradle property propagation from `ORG_GRADLE_PROJECT_*` is the intended integration point.

The existing `isCi` helper can continue to drive build scan behavior, but remote cache read/write decisions should come from the explicit properties above rather than inferring trust from `CI` inside Gradle.

## CI Behavior

GitHub Actions decides when remote cache access is active by setting or omitting Gradle properties.

Trusted push builds on `main` and `v1` should set:

- endpoint and credentials
- `remoteBuildCacheEnabled=true`
- `remoteBuildCachePush=true`

Untrusted pull request builds and local builds should omit `remoteBuildCacheEnabled`, which leaves the remote cache disabled. They continue to use the local Gradle cache only.

If a future workflow wants read-only remote cache access, it can set `remoteBuildCacheEnabled=true` and omit `remoteBuildCachePush` or set it to `false`.

## Error Handling

Missing remote cache configuration should not fail the build. The remote cache should be disabled unless every required value is present and `remoteBuildCacheEnabled=true`.

Invalid remote cache URLs or rejected credentials may still fail at Gradle runtime once remote caching is explicitly enabled. That is acceptable because the operator has opted into remote cache usage for that build.

## Testing

Verification should cover:

- `./gradlew help -Phaze.disableAppleTargets` with no remote cache properties.
- `./gradlew help -Phaze.disableAppleTargets -PremoteBuildCacheEnabled=true` without endpoint or credentials, confirming the build still configures successfully.
- `./gradlew help -Phaze.disableAppleTargets -PremoteBuildCacheEnabled=true -PremoteBuildCacheUrl=https://example.invalid/cache -PremoteBuildCacheUsername=user -PremoteBuildCachePassword=password`, confirming the settings script accepts a complete remote cache configuration.

Full remote cache read/write behavior requires real credentials and should be validated in CI after secrets are configured.
