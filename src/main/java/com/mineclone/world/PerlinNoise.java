package com.mineclone.world;

import java.util.Random;

/**
 * Simple Perlin noise implementation for terrain generation.
 * Based on Ken Perlin's improved noise (2002).
 */
public class PerlinNoise {
    private final int[] permutation = new int[512];
    
    public PerlinNoise(long seed) {
        Random random = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        
        // Shuffle using Fisher-Yates
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
        
        // Duplicate for easy wrapping
        for (int i = 0; i < 512; i++) {
            permutation[i] = p[i & 255];
        }
    }
    
    /**
     * Get 2D Perlin noise value at (x, y).
     * @return value between -1 and 1
     */
    public double noise(double x, double y) {
        // Find unit square that contains point
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        
        // Find relative x,y in square
        x -= Math.floor(x);
        y -= Math.floor(y);
        
        // Compute fade curves
        double u = fade(x);
        double v = fade(y);
        
        // Hash coordinates of square corners
        int aa = permutation[permutation[X] + Y];
        int ab = permutation[permutation[X] + Y + 1];
        int ba = permutation[permutation[X + 1] + Y];
        int bb = permutation[permutation[X + 1] + Y + 1];
        
        // Blend results from corners
        return lerp(v,
            lerp(u, grad(aa, x, y), grad(ba, x - 1, y)),
            lerp(u, grad(ab, x, y - 1), grad(bb, x - 1, y - 1))
        );
    }
    
    /**
     * Get octave noise (multiple frequencies combined).
     * @param octaves number of noise layers
     * @param persistence how much each octave contributes
     * @param scale overall scale
     */
    public double octaveNoise(double x, double y, int octaves, double persistence, double scale) {
        double total = 0.0;
        double frequency = scale;
        double amplitude = 1.0;
        double maxValue = 0.0;  // Used for normalizing
        
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        
        return total / maxValue;
    }
    
    private double fade(double t) {
        // 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
    
    private double grad(int hash, double x, double y) {
        // Convert low 4 bits of hash into gradient direction
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : 0;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}

