package org.example.model;

public record InputState(boolean up, boolean down, boolean left, boolean right, boolean bomb) {
    public static InputState empty() {
        return new InputState(false, false, false, false, false);
    }
}

