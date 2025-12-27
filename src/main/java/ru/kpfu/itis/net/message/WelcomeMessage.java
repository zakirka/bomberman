package ru.kpfu.itis.net.message;

public class WelcomeMessage extends Message {
    private final int playerId;
    private final int width;
    private final int height;
    private final String map;
    private static final String DELIMITER = "|";

    public WelcomeMessage(int playerId, int width, int height, String map) {
        this.playerId = playerId;
        this.width = width;
        this.height = height;
        this.map = map;
    }

    public int playerId() {
        return playerId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String map() {
        return map;
    }

    @Override
    public MessageType getType() {
        return MessageType.WELCOME;
    }

    @Override
    public String serialize() {
        return MessageType.WELCOME.name() + DELIMITER + playerId + DELIMITER + width + DELIMITER + height + DELIMITER + map;
    }
}
