#version 330 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec2 texCoord;
layout (location = 2) in vec3 normal;

out vec2 fragTexCoord;
out float fragBrightness;

uniform mat4 projectionMatrix;

void main() {
    gl_Position = projectionMatrix * vec4(position, 1.0);
    fragTexCoord = texCoord;
    
    // Minecraft's EXACT face brightness values (calculated per-vertex)
    float brightness = 1.0;
    vec3 norm = normalize(normal);
    if (abs(norm.y) > 0.9) {
        brightness = norm.y > 0.0 ? 1.0 : 0.5;  // Top: 1.0, Bottom: 0.5
    } else if (abs(norm.z) > 0.9) {
        brightness = 0.8;  // North/South: 0.8
    } else if (abs(norm.x) > 0.9) {
        brightness = 0.6;  // East/West: 0.6
    }
    fragBrightness = brightness;
}

