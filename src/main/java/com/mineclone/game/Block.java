package com.mineclone.game;

/**
 * Basic block types for Minecraft clone
 */
public class Block {
    public enum Type {
        // Format: id, top, bottom, side, north, south, solid, transparent, hardness
        // Hardness values from Minecraft 1.21+ (BlockBehaviour.Properties.strength)
        AIR(0, 0, 0, 0, 0, 0, false, true, 0.0f),
        STONE(1, 1, 1, 1, 1, 1, true, false, 1.5f),
        GRASS(2, 0, 1, 0, 2, 2, true, false, 0.6f),
        DIRT(3, 3, 3, 3, 3, 3, true, false, 0.5f),
        COBBLESTONE(4, 4, 4, 4, 4, 4, true, false, 2.0f),
        WOOD(5, 5, 5, 5, 6, 6, true, false, 2.0f),
        SAND(6, 7, 7, 7, 7, 7, true, false, 0.5f),
        WATER(7, 8, 8, 8, 8, 8, false, true, 100.0f),  // Can't break water
        OAK_LOG(8, 8, 8, 9, 9, 9, true, false, 2.0f),          // Top/bottom = log top texture, sides = log side
        OAK_LEAVES(9, 10, 10, 10, 10, 10, true, true, 0.2f);   // Solid but transparent (Minecraft-style)

        private final int id;
        private final int topTexture;
        private final int bottomTexture;
        private final int sideTexture;
        private final int northTexture;
        private final int southTexture;
        private final boolean solid;
        private final boolean transparent;  // Can see through (like leaves, glass)
        private final float hardness;       // How long it takes to break (seconds with bare hands)

        Type(int id, int top, int bottom, int side, int north, int south, boolean solid, boolean transparent, float hardness) {
            this.id = id;
            this.topTexture = top;
            this.bottomTexture = bottom;
            this.sideTexture = side;
            this.northTexture = north;
            this.southTexture = south;
            this.solid = solid;
            this.transparent = transparent;
            this.hardness = hardness;
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
         * Get block breaking time in seconds (Minecraft hardness).
         * This is for bare hands - tools would be faster.
         */
        public float getHardness() { return hardness; }
        
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
