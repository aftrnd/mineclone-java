package com.mineclone;

import com.mineclone.engine.Engine;
import com.mineclone.game.Game;

public class Main {
    public static void main(String[] args) {
        try {
            Game game = new Game();
            Engine engine = new Engine("MineClone", 1280, 720, true, game);
            engine.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
} 