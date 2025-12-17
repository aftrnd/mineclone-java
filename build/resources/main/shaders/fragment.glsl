#version 330 core

in vec3 vertexColor;
in vec2 texCoord;
in float sphericalFogDistance;
in float cylindricalFogDistance;
out vec4 FragColor;

uniform sampler2D blockTexture;

// Minecraft fog uniforms
uniform vec4 fogColor;  // Sky/fog color (RGB + alpha for intensity)
uniform float fogStart;  // Distance where fog starts
uniform float fogEnd;    // Distance where fog is fully opaque

// Minecraft's linear fog calculation
float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;  // No fog
    } else if (vertexDistance >= fogEnd) {
        return 1.0;  // Full fog
    }
    // Linear interpolation between start and end
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

// Minecraft uses max of spherical and cylindrical distances
// This prevents fog in caves while maintaining proper outdoor fog
float calculate_fog_value(float spherical, float cylindrical) {
    // Use cylindrical for most cases (prevents cave fog)
    // Could also use max(spherical, cylindrical) for Minecraft's exact behavior
    return linear_fog_value(cylindrical, fogStart, fogEnd);
}

void main() {
    // Sample texture and multiply by lighting (vertex color)
    vec4 texColor = texture(blockTexture, texCoord);
    
    // Minecraft-style alpha cutout: discard pixels below threshold (for leaves)
    if (texColor.a < 0.1) {
        discard;  // Don't render this pixel at all
    }
    
    // Apply lighting
    vec4 color = vec4(texColor.rgb * vertexColor, 1.0);
    
    // Apply Minecraft-style fog
    float fogValue = calculate_fog_value(sphericalFogDistance, cylindricalFogDistance);
    
    // Mix block color with fog color based on distance
    vec3 finalColor = mix(color.rgb, fogColor.rgb, fogValue * fogColor.a);
    
    FragColor = vec4(finalColor, 1.0);
}