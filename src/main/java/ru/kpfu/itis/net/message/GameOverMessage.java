package ru.kpfu.itis.net.message;

public class GameOverMessage extends Message {
    private final int winnerId;
    private final String winnerName;
    private static final String DELIMITER = "|";

    public GameOverMessage(int winnerId, String winnerName) {
        this.winnerId = winnerId;
        this.winnerName = winnerName;
    }

    public int winnerId() {
        return winnerId;
    }

    public String winnerName() {
        return winnerName;
    }

    @Override
    public MessageType getType() {
        return MessageType.GAME_OVER;
    }

    @Override
    public String serialize() {
        return MessageType.GAME_OVER.name() + DELIMITER + winnerId + DELIMITER + winnerName;
    }
}
