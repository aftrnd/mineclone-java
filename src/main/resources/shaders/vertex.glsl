#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aColor;
layout (location = 2) in vec2 aTexCoord;

out vec3 vertexColor;
out vec2 texCoord;
out float sphericalFogDistance;  // Minecraft-style: straight-line distance
out float cylindricalFogDistance;  // Minecraft-style: horizontal + vertical distance

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

// Minecraft fog distance calculations
float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

void main() {
    // Transform vertex position to view space (camera space)
    vec4 viewPos = view * model * vec4(aPos, 1.0);
    
    // Calculate fog distances in view space (Minecraft-style)
    sphericalFogDistance = fog_spherical_distance(viewPos.xyz);
    cylindricalFogDistance = fog_cylindrical_distance(viewPos.xyz);
    
    gl_Position = projection * viewPos;
    vertexColor = aColor;  // Pass lighting color
    texCoord = aTexCoord;   // Pass texture coordinates
}