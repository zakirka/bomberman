package ru.kpfu.itis.model;

public record Explosion(int x, int y, int ttl) {
    public Explosion tick() {
        return new Explosion(x, y, ttl - 1);
    }
}

