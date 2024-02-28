package dev.chrisbanes.haze

internal const val CLIPPING_SHADER_SKSL = """
  uniform shader content;

  uniform vec4 rectangle;
  uniform vec4 radius;

  // https://www.iquilezles.org/www/articles/distfunctions2d/distfunctions2d.htm
  float sdRoundedBox(vec2 position, vec2 box, vec4 radius) {
    radius.xy = (position.x > 0.0) ? radius.xy : radius.zw;
    radius.x = (position.y > 0.0) ? radius.x : radius.y;
    vec2 q = abs(position) - box + radius.x;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - radius.x;
  }

  bool rectContains(vec4 rectangle, vec2 coord) {
      vec2 shiftRect = (rectangle.zw - rectangle.xy) / 2.0;
      vec2 shiftCoord = coord - rectangle.xy;
      return sdRoundedBox(shiftCoord - shiftRect, shiftRect, radius) <= 0.0;
  }

  vec4 main(vec2 coord) {

    if (rectContains(rectangle, coord)) {
        // If we're not drawing in the rectangle, return transparent
        return vec4(0.0, 0.0, 0.0, 0.0);
    }

    return content.eval(coord);
  }
"""
internal const val SHADER_SKSL = """
  uniform shader content;
  uniform shader blur;
  uniform shader noise;

  uniform vec4 rectangle;
  uniform vec4 radius;
  uniform vec4 color;
  uniform float colorShift;
  uniform float noiseFactor;

  uniform float dropShadowSize;

  // https://www.iquilezles.org/www/articles/distfunctions2d/distfunctions2d.htm
  float sdRoundedBox(vec2 position, vec2 box, vec4 radius) {
    radius.xy = (position.x > 0.0) ? radius.xy : radius.zw;
    radius.x = (position.y > 0.0) ? radius.x : radius.y;
    vec2 q = abs(position) - box + radius.x;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - radius.x;
  }

  vec4 main(vec2 coord) {
    float distanceToClosestEdge = 0.0;
    vec2 shiftRect = (rectangle.zw - rectangle.xy) / 2.0;
    vec2 shiftCoord = coord - rectangle.xy;
    distanceToClosestEdge = sdRoundedBox(shiftCoord - shiftRect, shiftRect, radius);

    if(distanceToClosestEdge > 0.0){

      //make shadow
      if(distanceToClosestEdge < dropShadowSize){
        // Emulate drop shadow around the filtered area
        float darkenFactor = (dropShadowSize-distanceToClosestEdge)/dropShadowSize;
        // Use exponential drop shadow decay for more pleasant visuals
        darkenFactor = pow(darkenFactor, 2.0) / 4.0;
        // Shift towards black, by 10% around the edge, dissipating to 0% further away
        return vec4(0.0, 0.0, 0.0, darkenFactor);
       }

       // If we're not drawing in the rectangle, return transparent
      return vec4(0.0, 0.0, 0.0, 0.0);
    }

    vec4 b = blur.eval(coord);
    vec4 n = noise.eval(coord);

    // Add noise for extra texture
    float noiseLuma = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Calculate our overlay (tint + noise)
    float overlay = min(1.0, colorShift + (noiseLuma * noiseFactor));

    // Apply the overlay (noise + tint)
    b = b + ((color - b) * overlay);
    //b.a = 1.0;
    return b;
  }
"""
