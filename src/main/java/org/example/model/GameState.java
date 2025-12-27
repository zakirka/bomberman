package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameState {
    private final int width;
    private final int height;
    private final int tileSize = 48;
    private char[][] grid;
    private Map<Integer, Player> players = new HashMap<>();
    private List<Bomb> bombs = new ArrayList<>();
    private List<Explosion> explosions = new ArrayList<>();
    private int ownPlayerId;
    private String playerName;
    private int selfBombs;
    private boolean gameOver;
    private Integer winnerId;
    private String winnerName;

    public GameState(int width, int height, char[][] grid, int ownPlayerId, String playerName) {
        this.width = width;
        this.height = height;
        this.grid = grid;
        this.ownPlayerId = ownPlayerId;
        this.playerName = playerName;
        this.gameOver = false;
        this.winnerId = null;
        this.winnerName = null;
    }

    public synchronized void applySnapshot(Map<Integer, Player> players, List<Bomb> bombs, List<Explosion> explosions) {
        this.players = new HashMap<>(players);
        this.bombs = new ArrayList<>(bombs);
        this.explosions = new ArrayList<>(explosions);
        Player self = players.get(ownPlayerId);
        selfBombs = self != null ? self.bombsAvailable() : 0;
    }

    public synchronized GameState copy() {
        GameState state = new GameState(width, height, grid, ownPlayerId, playerName);
        state.applySnapshot(players, bombs, explosions);
        state.gameOver = this.gameOver;
        state.winnerId = this.winnerId;
        state.winnerName = this.winnerName;
        return state;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int tileSize() {
        return tileSize;
    }

    public synchronized char cellAt(int x, int y) {
        return grid[y][x];
    }

    public synchronized void updateGrid(char[][] newGrid) {
        this.grid = newGrid;
    }

    public synchronized Map<Integer, Player> players() {
        return new HashMap<>(players);
    }

    public synchronized List<Bomb> bombs() {
        return new ArrayList<>(bombs);
    }

    public synchronized List<Explosion> explosions() {
        return new ArrayList<>(explosions);
    }

    public int ownPlayerId() {
        return ownPlayerId;
    }

    public String playerName() {
        return playerName;
    }

    public int selfBombs() {
        return selfBombs;
    }

    public synchronized void setGameOver(Integer winnerId, String winnerName) {
        this.gameOver = true;
        this.winnerId = winnerId;
        this.winnerName = winnerName;
    }

    public synchronized boolean isGameOver() {
        return gameOver;
    }

    public synchronized Integer winnerId() {
        return winnerId;
    }

    public synchronized String winnerName() {
        return winnerName;
    }
}

