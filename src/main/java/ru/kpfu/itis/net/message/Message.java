package ru.kpfu.itis.net.message;

public abstract class Message {
    public abstract MessageType getType();
    public abstract String serialize();
}
