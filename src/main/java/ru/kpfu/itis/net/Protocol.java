package ru.kpfu.itis.net;

import ru.kpfu.itis.model.Bomb;
import ru.kpfu.itis.model.Explosion;
import ru.kpfu.itis.model.Player;
import ru.kpfu.itis.net.message.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Protocol {
    private static final String DELIMITER = "|";
    private static final String TOKEN_DELIMITER = ",";
    private static final String GRID_ROW_DELIMITER = "/";
    private static final String MAP_ROW_DELIMITER = ",";

    private static final String TOKEN_PLAYER = "P";
    private static final String TOKEN_BOMB = "B";
    private static final String TOKEN_EXPLOSION = "F";
    private static final String TOKEN_MAP = "M";

    public static Message parse(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\" + DELIMITER);
        if (parts.length == 0) return null;

        try {
            MessageType type = MessageType.valueOf(parts[0]);
            return switch (type) {
                case HELLO -> parseHello(parts);
                case WELCOME -> parseWelcome(parts);
                case START -> new StartMessage();
                case INPUT -> parseInput(parts);
                case STATE -> parseState(parts);
                case GAME_OVER -> parseGameOver(parts);
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static HelloMessage parseHello(String[] parts) {
        if (parts.length < 2) return null;
        return new HelloMessage(parts[1]);
    }

    private static WelcomeMessage parseWelcome(String[] parts) {
        if (parts.length < 5) return null;
        int id = Integer.parseInt(parts[1]);
        int w = Integer.parseInt(parts[2]);
        int h = Integer.parseInt(parts[3]);
        String map = parts[4];
        return new WelcomeMessage(id, w, h, map);
    }

    private static InputMessage parseInput(String[] parts) {
        if (parts.length < 7) return null;
        int id = Integer.parseInt(parts[1]);
        boolean up = Boolean.parseBoolean(parts[2]);
        boolean down = Boolean.parseBoolean(parts[3]);
        boolean left = Boolean.parseBoolean(parts[4]);
        boolean right = Boolean.parseBoolean(parts[5]);
        boolean bomb = Boolean.parseBoolean(parts[6]);
        return new InputMessage(id, up, down, left, right, bomb);
    }

    private static StateMessage parseState(String[] parts) {
        if (parts.length < 2) return null;
        long tick = Long.parseLong(parts[1]);
        Map<Integer, Player> players = new HashMap<>();
        List<Bomb> bombs = new ArrayList<>();
        List<Explosion> explosions = new ArrayList<>();
        String grid = null;

        for (int i = 2; i < parts.length; i++) {
            String token = parts[i];
            if (token.isBlank()) continue;
            String[] t = token.split(TOKEN_DELIMITER);
            if (t.length == 0) continue;

            switch (t[0]) {
                case TOKEN_PLAYER -> {
                    if (t.length < 7) continue;
                    int id = Integer.parseInt(t[1]);
                    int x = Integer.parseInt(t[2]);
                    int y = Integer.parseInt(t[3]);
                    boolean alive = Boolean.parseBoolean(t[4]);
                    int bombsAvailable = Integer.parseInt(t[5]);
                    players.put(id, new Player(id, t[6], x, y, alive, bombsAvailable));
                }
                case TOKEN_BOMB -> {
                    if (t.length < 5) continue;
                    int owner = Integer.parseInt(t[1]);
                    int x = Integer.parseInt(t[2]);
                    int y = Integer.parseInt(t[3]);
                    int timer = Integer.parseInt(t[4]);
                    bombs.add(new Bomb(owner, x, y, timer));
                }
                case TOKEN_EXPLOSION -> {
                    if (t.length < 4) continue;
                    int x = Integer.parseInt(t[1]);
                    int y = Integer.parseInt(t[2]);
                    int ttl = Integer.parseInt(t[3]);
                    explosions.add(new Explosion(x, y, ttl));
                }
                case TOKEN_MAP -> {
                    if (t.length < 2) continue;
                    grid = t[1];
                }
            }
        }

        return new StateMessage(tick, players, bombs, explosions, grid != null ? grid : "");
    }

    private static GameOverMessage parseGameOver(String[] parts) {
        if (parts.length < 3) return null;
        int winnerId = Integer.parseInt(parts[1]);
        String winnerName = parts[2];
        return new GameOverMessage(winnerId, winnerName);
    }

    public static String getDelimiter() {
        return DELIMITER;
    }

    public static String getTokenDelimiter() {
        return TOKEN_DELIMITER;
    }

    public static String getGridRowDelimiter() {
        return GRID_ROW_DELIMITER;
    }

    public static String getMapRowDelimiter() {
        return MAP_ROW_DELIMITER;
    }

    public static String getTokenPlayer() {
        return TOKEN_PLAYER;
    }

    public static String getTokenBomb() {
        return TOKEN_BOMB;
    }

    public static String getTokenExplosion() {
        return TOKEN_EXPLOSION;
    }

    public static String getTokenMap() {
        return TOKEN_MAP;
    }
}
