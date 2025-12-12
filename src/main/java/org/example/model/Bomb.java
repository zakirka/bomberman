package org.example.model;

public record Bomb(int ownerId, int x, int y, int timer) {
    public Bomb tick() {
        return new Bomb(ownerId, x, y, timer - 1);
    }
}

