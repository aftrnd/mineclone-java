package com.mineclone;

import com.mineclone.engine.Engine;
import com.mineclone.game.Game;

public class Main {
    public static void main(String[] args) {
        try {
            Game game = new Game();
            // Minecraft's default window size: 854x480
            Engine engine = new Engine("MineClone", 854, 480, true, game);
            engine.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
} 