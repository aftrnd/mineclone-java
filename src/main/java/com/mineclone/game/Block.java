package com.mineclone.game;

/**
 * Basic block types for Minecraft clone
 */
public class Block {
    public enum Type {
        // Format: id, top, bottom, side, north, south, solid, transparent
        AIR(0, 0, 0, 0, 0, 0, false, true),
        STONE(1, 1, 1, 1, 1, 1, true, false),
        GRASS(2, 0, 1, 0, 2, 2, true, false),
        DIRT(3, 3, 3, 3, 3, 3, true, false),
        COBBLESTONE(4, 4, 4, 4, 4, 4, true, false),
        WOOD(5, 5, 5, 5, 6, 6, true, false),
        SAND(6, 7, 7, 7, 7, 7, true, false),
        WATER(7, 8, 8, 8, 8, 8, false, true),
        OAK_LOG(8, 8, 8, 9, 9, 9, true, false),          // Top/bottom = log top texture, sides = log side
        OAK_LEAVES(9, 10, 10, 10, 10, 10, true, true);   // Solid but transparent (Minecraft-style)

        private final int id;
        private final int topTexture;
        private final int bottomTexture;
        private final int sideTexture;
        private final int northTexture;
        private final int southTexture;
        private final boolean solid;
        private final boolean transparent;  // Can see through (like leaves, glass)

        Type(int id, int top, int bottom, int side, int north, int south, boolean solid, boolean transparent) {
            this.id = id;
            this.topTexture = top;
            this.bottomTexture = bottom;
            this.sideTexture = side;
            this.northTexture = north;
            this.southTexture = south;
            this.solid = solid;
            this.transparent = transparent;
        }

        public int getId() { return id; }
        public boolean isSolid() { return solid; }
        public boolean isTransparent() { return transparent; }  // New method!
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
                case OAK_LOG -> new float[]{0.55f, 0.4f, 0.25f};  // Oak brown
                case OAK_LEAVES -> new float[]{0.3f, 0.6f, 0.2f};  // Green leaves
                default -> new float[]{1.0f, 1.0f, 1.0f};  // White for unknown
            };
        }
    }

    private Type type;
    
    // Light levels (0-15) - Minecraft-style lighting
    private byte skyLight = 0;      // Light from sky (sun/moon)
    private byte blockLight = 0;    // Light from blocks (torches, lava, etc.)

    public Block(Type type) {
        this.type = type;
        // Air blocks start with full sky light
        if (type == Type.AIR) {
            this.skyLight = 15;
        }
    }

    public Type getType() { return type; }
    public boolean isSolid() { return type.isSolid(); }
    public int getId() { return type.getId(); }
    
    // Light level getters and setters
    public int getSkyLight() { return skyLight & 0x0F; }
    public int getBlockLight() { return blockLight & 0x0F; }
    public void setSkyLight(int level) { this.skyLight = (byte) Math.max(0, Math.min(15, level)); }
    public void setBlockLight(int level) { this.blockLight = (byte) Math.max(0, Math.min(15, level)); }
    
    /**
     * Get the combined light level (max of sky and block light).
     * This is used for calculating brightness.
     */
    public int getLightLevel() {
        return Math.max(getSkyLight(), getBlockLight());
    }
}
