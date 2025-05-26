package com.mineclone;

import com.mineclone.core.engine.GameEngine;
import com.mineclone.core.engine.IGameLogic;
import com.mineclone.core.engine.Window;
import com.mineclone.game.DummyGame;

public class Main {
    public static void main(String[] args) {
        try {
            boolean vSync = true;
            IGameLogic gameLogic = new DummyGame();
            GameEngine gameEng = new GameEngine("MineClone", 1280, 720, vSync, gameLogic);
            gameEng.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
} 