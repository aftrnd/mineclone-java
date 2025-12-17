#version 330 core

in vec2 fragTexCoord;
in float fragBrightness;
out vec4 outColor;

uniform sampler2D textureSampler;

void main() {
    vec4 texColor = texture(textureSampler, fragTexCoord);
    if (texColor.a < 0.1) discard;  // Alpha test
    
    // Apply brightness as vertex color (Minecraft's exact method)
    // The brightness is interpolated across the face from vertex shader
    vec3 litColor = texColor.rgb * fragBrightness;
    
    outColor = vec4(litColor, texColor.a);
}

