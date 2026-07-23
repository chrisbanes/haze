# ADR-0001: Treat Haze 2 as an intentional source break

## Status

Accepted

## Date

2026-06-07

## Context

Haze 1 exposed blur behavior directly from the core module. Haze 2 introduced a pluggable visual
effects architecture, moved blur into its own module, and renamed the blur APIs around their more
specific roles.

Keeping the old root-package names and forwarding overloads would make some Haze 1 call sites
compile, but it would not provide complete source compatibility. A partial compatibility surface
would imply a safer migration than the library could actually guarantee, retain duplicate ways to
express the same behavior, and constrain the Haze 2 API before its architecture had settled.

Haze 2 is a major release, so callers already expect to make source changes. The project can use
that boundary to establish a coherent modular API and document the migration explicitly.

## Decision

Haze 2 is an intentional hard source break from Haze 1:

- Core effect orchestration remains in `haze`, while blur behavior lives in `haze-blur` and blur
  material presets live in `haze-blur-materials`.
- Public blur APIs use the `dev.chrisbanes.haze.blur` package and the Haze 2 names, including
  `HazeBlurStyle`, `HazeColorEffect`, `LocalHazeBlurStyle`, and `blurEffect`.
- Broad Haze 1 root-package aliases and forwarding overloads are removed rather than maintained as
  an incomplete compatibility layer.
- Any temporary compatibility aid during the Haze 2 alpha cycle must be narrow and explicit. It
  does not establish a general source-compatibility policy.
- The migration guide is the supported bridge from Haze 1 to Haze 2 and must describe dependency,
  package, symbol, and usage changes directly.

The new blur style contract also distinguishes an unspecified list of color effects from an
explicitly empty list:

- `null` means unspecified and allows the next style-precedence tier to supply color effects.
- `emptyList()` means specified empty and clears inherited color effects.
- Non-empty lists mean specified effects.

Style objects defensively snapshot caller-owned lists so their `@Immutable` contract cannot be
invalidated by later mutation.

## Alternatives Considered

### Keep broad deprecated aliases and forwarding overloads

This would reduce edits for some callers, but only for the subset of Haze 1 APIs that happened to
have a forwarding equivalent. It was rejected because it would preserve two public vocabularies,
imply compatibility the project did not provide, and make the Haze 2 architecture harder to evolve.

### Add a complete Haze 1 compatibility module

A dedicated adapter could preserve more call sites while keeping aliases out of the new modules.
It was rejected because maintaining a second public surface would add significant implementation,
testing, documentation, and support cost during a major architectural rewrite.

### Make the source break explicit and provide a precise migration guide

This was chosen because it makes the compatibility boundary honest, keeps the Haze 2 API coherent,
and concentrates migration support in documentation rather than permanent forwarding code.

## Consequences

- Haze 1 callers must update dependencies, imports, type names, and blur configuration when moving
  to Haze 2.
- The migration guide is release-critical documentation and must stay synchronized with the public
  API.
- API validation and focused tests should prevent removed compatibility names or ambiguous style
  semantics from returning accidentally.
- The core module is no longer coupled to blur and can host other `VisualEffect`
  implementations without treating blur as the built-in default.
- Blur styles remain ergonomic at construction boundaries while retaining defensible immutable
  semantics.

## References

- [V2 API Cleanup Design](https://github.com/chrisbanes/haze/blob/ca1019fa3c99a62c50aa8cfcdc1b9e806457912c/docs/superpowers/specs/2026-06-07-v2-api-cleanup-design.md)
- [Migrating to Haze 2.0](../migrating-2.0.md)
- [PR #963: V2 API Cleanup](https://github.com/chrisbanes/haze/pull/963)
