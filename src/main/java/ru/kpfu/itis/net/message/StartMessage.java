package ru.kpfu.itis.net.message;

public class StartMessage extends Message {
    @Override
    public MessageType getType() {
        return MessageType.START;
    }

    @Override
    public String serialize() {
        return MessageType.START.name();
    }
}
