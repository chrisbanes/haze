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

**Glass optics**:
An immutable description of a Glass material's refraction, blur, and depth response. Its optical
parameter values may be fixed across surface sizes or interpolated by size without becoming
different semantic kinds of optics.
_Avoid_: Fixed optics, adaptive optics

**Regular Glass style**:
The default built-in Glass style whose blur and depth adapt to the surface's shortest side while its
refraction response uses authored constants.
_Avoid_: Default Glass style

**Clear Glass style**:
A built-in Glass style that prioritises visibility of content behind the surface. Its blur and depth
adapt to the surface's shortest side while its refraction response remains authored specifically for
Clear.
_Avoid_: Transparent Glass, no effect

**Optical size point**:
A point that pairs a Glass surface's shortest dimension with one optical parameter value.
_Avoid_: Size step, size threshold

**Optical size value**:
An optical parameter value that is either fixed for every Glass surface size or smoothly
interpolated across two or more ordered optical size points.
_Avoid_: Size response, blur profile, adaptive optics
