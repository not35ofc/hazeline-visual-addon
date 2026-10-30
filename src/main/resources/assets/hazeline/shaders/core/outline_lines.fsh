#version 330

// Hazeline block outline lines (fragment). Same as vanilla core/rendertype_lines.fsh, self-contained.

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    float fogValue = max(linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd),
            linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd));
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fogValue * FogColor.a), color.a);
}
