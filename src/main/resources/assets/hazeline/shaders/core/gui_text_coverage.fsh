#version 330

// Hazeline GUI text. Same inputs and uniform block as vanilla core/position_tex_color.fsh
// (whose vertex shader this pipeline uses), but the texture is a single-channel (RED8) glyph
// atlas holding 8-bit anti-aliasing coverage:
//  - coverage multiplies only the alpha, so edge pixels keep the text colour and blend with
//    standard alpha blending (vanilla's text_intensity shader multiplies RGB too and darkens edges);
//  - only fully transparent pixels are discarded (vanilla's text shaders discard alpha < 0.1,
//    which removes the faint pixels thin strokes are made of).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).r;
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
