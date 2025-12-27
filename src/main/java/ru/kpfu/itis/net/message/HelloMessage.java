package ru.kpfu.itis.net.message;

public class HelloMessage extends Message {
    private final String name;
    private static final String DELIMITER = "|";

    public HelloMessage(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public MessageType getType() {
        return MessageType.HELLO;
    }

    @Override
    public String serialize() {
        return MessageType.HELLO.name() + DELIMITER + name;
    }
}
