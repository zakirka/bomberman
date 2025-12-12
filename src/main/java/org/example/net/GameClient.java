package org.example.net;

import org.example.model.Bomb;
import org.example.model.Explosion;
import org.example.model.GameState;
import org.example.model.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class GameClient {
    public enum InputField {UP, DOWN, LEFT, RIGHT, BOMB}

    private final String host;
    private final int port;
    private final String name;

    private volatile Socket socket;
    private volatile PrintWriter writer;
    private volatile BufferedReader reader;
    private volatile boolean running;
    private GameState state;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean bomb;
    private final Timer inputTimer = new Timer(true);

    public GameClient(String host, int port, String name) {
        this.host = host;
        this.port = port;
        this.name = name;
    }

    public void connect() throws Exception {
        socket = new Socket(host, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer.println("HELLO|" + name);
        running = true;
        startReader();
        inputTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendInput();
            }
        }, 50, 70);
    }

    private void startReader() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                close();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void handleLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "WELCOME" -> handleWelcome(parts);
            case "STATE" -> handleState(parts);
            case "START" -> {
            }
            case "GAME_OVER" -> handleGameOver(parts);
        }
    }

    private void handleWelcome(String[] parts) {
        if (parts.length < 5) return;
        int id = Integer.parseInt(parts[1]);
        int w = Integer.parseInt(parts[2]);
        int h = Integer.parseInt(parts[3]);
        String[] rows = parts[4].split(",");
        char[][] grid = new char[h][w];
        for (int y = 0; y < h; y++) {
            grid[y] = rows[y].toCharArray();
        }
        state = new GameState(w, h, grid, id, name);
    }

    private void handleState(String[] parts) {
        if (state == null || parts.length < 2) return;
        Map<Integer, Player> players = new HashMap<>();
        List<Bomb> bombs = new ArrayList<>();
        List<Explosion> explosions = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            String token = parts[i];
            if (token.isBlank()) continue;
            String[] t = token.split(",");
            switch (t[0]) {
                case "P" -> {
                    int id = Integer.parseInt(t[1]);
                    int x = Integer.parseInt(t[2]);
                    int y = Integer.parseInt(t[3]);
                    boolean alive = Boolean.parseBoolean(t[4]);
                    int bombsAvailable = Integer.parseInt(t[5]);
                    players.put(id, new Player(id, t[6], x, y, alive, bombsAvailable));
                }
                case "B" -> {
                    int owner = Integer.parseInt(t[1]);
                    int x = Integer.parseInt(t[2]);
                    int y = Integer.parseInt(t[3]);
                    int timer = Integer.parseInt(t[4]);
                    bombs.add(new Bomb(owner, x, y, timer));
                }
                case "F" -> {
                    int x = Integer.parseInt(t[1]);
                    int y = Integer.parseInt(t[2]);
                    int ttl = Integer.parseInt(t[3]);
                    explosions.add(new Explosion(x, y, ttl));
                }
                case "M" -> {
                    String[] rows = t[1].split("/");
                    char[][] grid = new char[rows.length][];
                    for (int r = 0; r < rows.length; r++) grid[r] = rows[r].toCharArray();
                    state.updateGrid(grid);
                }
            }
        }
        state.applySnapshot(players, bombs, explosions);
    }

    private void handleGameOver(String[] parts) {
        if (state == null || parts.length < 3) return;
        int winnerId = Integer.parseInt(parts[1]);
        String winnerName = parts[2];
        state.setGameOver(winnerId, winnerName);
    }

    private void sendInput() {
        if (!running || writer == null) return;
        GameState currentState = state;
        if (currentState == null) return;
        boolean sendBomb = bomb;
        bomb = false;
        if (currentState.isGameOver()) return;
        writer.println(String.join("|",
                "INPUT",
                String.valueOf(currentState.ownPlayerId()),
                String.valueOf(up),
                String.valueOf(down),
                String.valueOf(left),
                String.valueOf(right),
                String.valueOf(sendBomb)
        ));
    }

    public void setInput(InputField field, boolean value) {
        switch (field) {
            case UP -> up = value;
            case DOWN -> down = value;
            case LEFT -> left = value;
            case RIGHT -> right = value;
            case BOMB -> bomb = value;
        }
    }

    public GameState getStateSnapshot() {
        return state == null ? null : state.copy();
    }

    public void close() {
        running = false;
        inputTimer.cancel();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}

