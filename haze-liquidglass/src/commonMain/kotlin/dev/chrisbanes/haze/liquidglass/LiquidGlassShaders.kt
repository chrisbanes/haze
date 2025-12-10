// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

internal object LiquidGlassShaders {
  /**
   * SKSL that simulates a layered glass effect with refraction, specular highlights,
   * Fresnel-based ambient response, and soft edges.
   */
  val LIQUID_GLASS_SKSL: String by lazy(mode = LazyThreadSafetyMode.NONE) {
    """
    uniform shader content;
    uniform shader blurredContent;
    uniform float2 layerSize;
    uniform float refractionStrength;
    uniform float specularIntensity;
    uniform float depth;
    uniform float ambientResponse;
    uniform float edgeSoftness;
    uniform float2 lightPosition;
    uniform vec4 tintColor;

    float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

    vec2 clampCoord(vec2 coord) {
      return clamp(coord, vec2(0.5, 0.5), layerSize - vec2(0.5, 0.5));
    }

    // Bezel width as fraction of smallest dimension
    const float BEZEL_WIDTH_RATIO = 0.25;

    // Circle map - displacement profile
    // Creates a smooth curve from edge to interior
    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    // Compute displacement vector
    vec2 computeDisplacement(vec2 coord, float refractionHeight) {
      // Distance from each edge
      vec2 distFromEdge = min(coord, layerSize - coord);
      float minDistFromEdge = min(distFromEdge.x, distFromEdge.y);

      // If we're beyond the refraction zone, no displacement
      if (minDistFromEdge >= refractionHeight) {
        return vec2(0.0);
      }

      // Calculate displacement magnitude using circle map
      // Maps distance (0=edge to 1=interior) to displacement amount
      float normalizedDist = clamp(minDistFromEdge / refractionHeight, 0.0, 1.0);
      float displacementMagnitude = circleMap(1.0 - normalizedDist);

      // Determine gradient direction (perpendicular to nearest edge, pointing inward)
      vec2 grad;
      if (distFromEdge.x < distFromEdge.y) {
        // Closer to left/right edge
        grad = vec2(coord.x < layerSize.x * 0.5 ? 1.0 : -1.0, 0.0);
      } else {
        // Closer to top/bottom edge
        grad = vec2(0.0, coord.y < layerSize.y * 0.5 ? 1.0 : -1.0);
      }

      return displacementMagnitude * grad;
    }

    vec3 computeShapeNormal(vec2 coord) {
      // For lighting calculations, compute normal from displacement gradient
      float bezelWidth = min(layerSize.x, layerSize.y) * BEZEL_WIDTH_RATIO;
      vec2 displacement = computeDisplacement(coord, bezelWidth);

      // If no displacement, return flat normal
      if (length(displacement) < 0.001) {
        return vec3(0.0, 0.0, 1.0);
      }

      // Approximate normal from displacement direction
      return normalize(vec3(displacement, 0.5));
    }

    vec3 computeContentNormal(vec2 coord) {
      // Add subtle content-based distortion for texture
      float l = luma(content.eval(clampCoord(coord + vec2(1.0, 0.0))).rgb);
      float r = luma(content.eval(clampCoord(coord - vec2(1.0, 0.0))).rgb);
      float t = luma(content.eval(clampCoord(coord + vec2(0.0, 1.0))).rgb);
      float b = luma(content.eval(clampCoord(coord - vec2(0.0, 1.0))).rgb);
      vec2 grad = vec2(r - l, b - t);
      return normalize(vec3(grad, 1.0));
    }

    vec3 computeNormal(vec2 coord) {
      // Blend shape-based normals (primary) with content-based normals (subtle texture)
      vec3 shapeNormal = computeShapeNormal(coord);
      vec3 contentNormal = computeContentNormal(coord);
      // Use mostly shape normal, with a hint of content-based distortion
      return normalize(mix(shapeNormal, contentNormal, 0.15));
    }

    float edgeMask(vec2 coord) {
      if (edgeSoftness <= 0.0) return 1.0;
      vec2 dist = min(coord, layerSize - coord);
      float e = min(dist.x, dist.y) / max(edgeSoftness, 0.0001);
      return clamp(e, 0.0, 1.0);
    }

    vec4 main(vec2 coord) {
      // Get base color
      vec4 base = content.eval(coord);

      // Compute normal for lighting calculations
      vec3 normal = computeNormal(coord);

      // Apply refraction displacement
      float bezelWidth = min(layerSize.x, layerSize.y) * BEZEL_WIDTH_RATIO;
      vec2 baseDisplacement = computeDisplacement(coord, bezelWidth);
      vec2 displacement = baseDisplacement * refractionStrength * bezelWidth;
      vec2 refractCoord = clampCoord(coord + displacement);

      // Sample refracted content
      vec4 refracted = content.eval(refractCoord);

      // Sample blurred content at refracted position
      vec4 blurred = blurredContent.eval(refractCoord);

      // Mix depth: blend between base and blurred based on depth parameter
      vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));

      // Apply tint
      vec3 tinted = mix(mixedColor, tintColor.rgb, tintColor.a);

      // Add specular highlights
      vec2 lightDir2D = normalize(lightPosition - coord);
      vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
      float spec = pow(max(dot(normal, lightDir), 0.0), 24.0) * specularIntensity;

      // Apply Fresnel ambient lighting
      float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 3.0);
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));

      vec3 finalColor = tinted * ambient + spec;

      // Apply edge softness mask
      float edge = edgeMask(coord);
      return vec4(finalColor, base.a) * edge;
    }
    """
  }
}
