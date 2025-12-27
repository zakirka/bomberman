package ru.kpfu.itis.net.message;

public class InputMessage extends Message {
    private final int playerId;
    private final boolean up;
    private final boolean down;
    private final boolean left;
    private final boolean right;
    private final boolean bomb;
    private static final String DELIMITER = "|";

    public InputMessage(int playerId, boolean up, boolean down, boolean left, boolean right, boolean bomb) {
        this.playerId = playerId;
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.bomb = bomb;
    }

    public int playerId() {
        return playerId;
    }

    public boolean up() {
        return up;
    }

    public boolean down() {
        return down;
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    public boolean bomb() {
        return bomb;
    }

    @Override
    public MessageType getType() {
        return MessageType.INPUT;
    }

    @Override
    public String serialize() {
        return MessageType.INPUT.name() + DELIMITER + playerId + DELIMITER + up + DELIMITER + down + DELIMITER + left + DELIMITER + right + DELIMITER + bomb;
    }
}
