# Haze

Haze provides visual effects whose reusable appearance descriptions can be applied consistently
across supported platforms.

## Language

**Glass style**:
A reusable description of a Glass material's appearance.
_Avoid_: Material variant

**Built-in Glass style**:
A library-provided Glass style representing a supported named material response, such as Regular or
Clear. It owns the optical, edge, lighting, chromatic, tone, and content-normal responses while
preserving independently composed shape, background colour, tint, alpha, light position, and
interaction appearance. It remains recognisable when a renderer simplifies advanced optics.
_Avoid_: Material preset

**Regular Glass style**:
The default built-in Glass style whose material response adapts to the surface geometry.
_Avoid_: Default Glass style

**Clear Glass style**:
A built-in Glass style with a fixed authored optical response that prioritises visibility of content
behind the surface. Surface geometry may still change its edge rendering.
_Avoid_: Transparent Glass, no effect
