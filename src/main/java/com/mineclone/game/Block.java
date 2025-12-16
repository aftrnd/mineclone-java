package com.mineclone.game;

/**
 * Basic block types for Minecraft clone
 */
public class Block {
    public enum Type {
        AIR(0, 0, 0, 0, 0, 0, false),
        STONE(1, 1, 1, 1, 1, 1, true),
        GRASS(2, 0, 1, 0, 2, 2, true),
        DIRT(3, 3, 3, 3, 3, 3, true),
        COBBLESTONE(4, 4, 4, 4, 4, 4, true),
        WOOD(5, 5, 5, 5, 6, 6, true),
        SAND(6, 7, 7, 7, 7, 7, true),
        WATER(7, 8, 8, 8, 8, 8, false);

        private final int id;
        private final int topTexture;
        private final int bottomTexture;
        private final int sideTexture;
        private final int northTexture;
        private final int southTexture;
        private final boolean solid;

        Type(int id, int top, int bottom, int side, int north, int south, boolean solid) {
            this.id = id;
            this.topTexture = top;
            this.bottomTexture = bottom;
            this.sideTexture = side;
            this.northTexture = north;
            this.southTexture = south;
            this.solid = solid;
        }

        public int getId() { return id; }
        public boolean isSolid() { return solid; }
        public int getTopTexture() { return topTexture; }
        public int getBottomTexture() { return bottomTexture; }
        public int getSideTexture() { return sideTexture; }
        public int getNorthTexture() { return northTexture; }
        public int getSouthTexture() { return southTexture; }
        
        /**
         * Get RGB color for a block face.
         * Returns different colors for different block types (Minecraft-style).
         */
        public float[] getColor(boolean isTop, boolean isBottom) {
            return switch (this) {
                case GRASS -> isTop ? 
                    new float[]{0.4f, 0.8f, 0.3f} :  // Bright green for grass top
                    new float[]{0.55f, 0.4f, 0.2f};  // Brown for grass sides
                case DIRT -> new float[]{0.55f, 0.4f, 0.2f};  // Brown
                case STONE -> new float[]{0.5f, 0.5f, 0.5f};  // Gray
                case COBBLESTONE -> new float[]{0.4f, 0.4f, 0.4f};  // Dark gray
                case WOOD -> new float[]{0.6f, 0.4f, 0.2f};  // Wood brown
                case SAND -> new float[]{0.9f, 0.85f, 0.6f};  // Sandy yellow
                case WATER -> new float[]{0.2f, 0.4f, 0.8f};  // Blue
                default -> new float[]{1.0f, 1.0f, 1.0f};  // White for unknown
            };
        }
    }

    private Type type;

    public Block(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }
    public boolean isSolid() { return type.isSolid(); }
    public int getId() { return type.getId(); }
}
