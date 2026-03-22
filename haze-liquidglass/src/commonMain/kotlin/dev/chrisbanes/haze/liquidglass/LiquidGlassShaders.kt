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
    uniform float refractionHeight;
    uniform float chromaticAberrationStrength;
    uniform float2 lightPosition;
    uniform vec4 cornerRadii; // topLeft, topRight, bottomRight, bottomLeft
    uniform vec4 tintColor;

    float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

    vec2 clampCoord(vec2 coord) {
      return clamp(coord, vec2(0.5, 0.5), layerSize - vec2(0.5, 0.5));
    }

    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float distanceToRoundedRect(vec2 coord) {
      float width = layerSize.x;
      float height = layerSize.y;

      float tl = cornerRadii.x;
      float tr = cornerRadii.y;
      float br = cornerRadii.z;
      float bl = cornerRadii.w;

      if (coord.x < tl && coord.y < tl) {
        vec2 c = vec2(tl, tl);
        return max(tl - length(coord - c), 0.0);
      }
      if (coord.x > width - tr && coord.y < tr) {
        vec2 c = vec2(width - tr, tr);
        return max(tr - length(coord - c), 0.0);
      }
      if (coord.x > width - br && coord.y > height - br) {
        vec2 c = vec2(width - br, height - br);
        return max(br - length(coord - c), 0.0);
      }
      if (coord.x < bl && coord.y > height - bl) {
        vec2 c = vec2(bl, height - bl);
        return max(bl - length(coord - c), 0.0);
      }

      float distLeft = coord.x;
      float distRight = width - coord.x;
      float distTop = coord.y;
      float distBottom = height - coord.y;
      return max(min(min(distLeft, distRight), min(distTop, distBottom)), 0.0);
    }

    vec2 inwardNormal(vec2 coord) {
      float width = layerSize.x;
      float height = layerSize.y;

      float tl = cornerRadii.x;
      float tr = cornerRadii.y;
      float br = cornerRadii.z;
      float bl = cornerRadii.w;

      if (coord.x < tl && coord.y < tl) {
        vec2 c = vec2(tl, tl);
        vec2 dir = c - coord;
        return normalize(dir + vec2(1e-4));
      }
      if (coord.x > width - tr && coord.y < tr) {
        vec2 c = vec2(width - tr, tr);
        vec2 dir = c - coord;
        return normalize(dir + vec2(1e-4));
      }
      if (coord.x > width - br && coord.y > height - br) {
        vec2 c = vec2(width - br, height - br);
        vec2 dir = c - coord;
        return normalize(dir + vec2(1e-4));
      }
      if (coord.x < bl && coord.y > height - bl) {
        vec2 c = vec2(bl, height - bl);
        vec2 dir = c - coord;
        return normalize(dir + vec2(1e-4));
      }

      float dx = (width - 2.0 * coord.x);
      float dy = (height - 2.0 * coord.y);
      vec2 n = vec2(dx, dy);
      if (dot(n, n) < 1e-8) n = vec2(0.0, 1.0);
      return normalize(n);
    }

    vec3 computeContentNormal(vec2 coord) {
      float l = luma(content.eval(clampCoord(coord + vec2(1.0, 0.0))).rgb);
      float r = luma(content.eval(clampCoord(coord - vec2(1.0, 0.0))).rgb);
      float t = luma(content.eval(clampCoord(coord + vec2(0.0, 1.0))).rgb);
      float b = luma(content.eval(clampCoord(coord - vec2(0.0, 1.0))).rgb);
      vec2 grad = vec2(r - l, b - t);
      return normalize(vec3(grad, 1.0));
    }

    vec3 computeNormal(vec2 coord, vec2 shapeNormal2D) {
      vec3 shapeNormal = normalize(vec3(shapeNormal2D, 0.5));
      vec3 contentNormal = computeContentNormal(coord);
      return normalize(mix(shapeNormal, contentNormal, 0.15));
    }

    float edgeMask(vec2 coord, float distToEdge) {
      if (edgeSoftness <= 0.0) return 1.0;
      float e = distToEdge / max(edgeSoftness, 0.0001);
      return clamp(e, 0.0, 1.0);
    }

    vec4 sampleChroma(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationStrength <= 0.0001) return content.eval(clampCoord(coord));
      vec2 forward = clampCoord(coord + chromaOffset);
      vec2 backward = clampCoord(coord - chromaOffset);
      vec4 base = content.eval(clampCoord(coord));
      return vec4(content.eval(forward).r, base.g, content.eval(backward).b, base.a);
    }

    vec4 main(vec2 coord) {
      vec4 base = content.eval(coord);

      float distToEdge = distanceToRoundedRect(coord);
      vec2 normal2D = inwardNormal(coord);

      float refractionZone = max(refractionHeight, 0.0001);
      float normalizedDist = clamp(distToEdge / refractionZone, 0.0, 1.0);
      float displacementMagnitude = distToEdge >= refractionZone ? 0.0 : circleMap(1.0 - normalizedDist);
      vec2 displacement = normal2D * displacementMagnitude * refractionZone * refractionStrength;

      vec2 refractCoord = clampCoord(coord + displacement);

      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5;
      vec4 refracted = sampleChroma(refractCoord, chromaOffset);
      vec4 blurred = blurredContent.eval(refractCoord);

      vec3 normal = computeNormal(coord, normal2D);

      vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));
      vec3 tinted = mix(mixedColor, tintColor.rgb, tintColor.a);

      vec2 lightDir2D = normalize(lightPosition - coord);
      vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
      float spec = pow(max(dot(normal, lightDir), 0.0), 24.0) * specularIntensity;

      float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 3.0);
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));

      vec3 finalColor = mix(tinted, refracted.rgb, refractionStrength) * ambient + spec;

      float edge = edgeMask(coord, distToEdge);
      return vec4(finalColor, base.a) * edge;
    }
    """
  }
}
