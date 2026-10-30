#version 330

// Hazeline custom sky dome. Vertex colour, plus +-0.5/255 of interleaved gradient noise
// (Jimenez 2014) per pixel: breaks up the 8-bit steps of slow gradients, so no banding shows.
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb + (noise - 0.5) / 255.0, color.a);
}
