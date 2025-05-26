# MineClone

A Minecraft clone written in Java using LWJGL 3.

## Project Structure

```
src/main/java/com/mineclone/
├── core/           # Core game engine components
│   ├── engine/     # Game engine implementation
│   ├── math/       # Math utilities and vector classes
│   └── utils/      # Utility classes
├── game/           # Game-specific implementations
│   ├── blocks/     # Block definitions and behaviors
│   ├── world/      # World generation and management
│   └── player/     # Player-related classes
└── render/         # Rendering system
    ├── shaders/    # GLSL shaders
    ├── models/     # 3D models and meshes
    └── textures/   # Texture management
```

## Requirements

- Java 17 or later
- Gradle 8.5 or later
- LWJGL 3.3.2

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run
```

## Development

This project uses:
- LWJGL 3 for OpenGL bindings
- Gradle for build management
- Java 17 for development

## License

MIT License 