package ru.kpfu.itis.net;

import ru.kpfu.itis.model.GameState;
import ru.kpfu.itis.net.Protocol;
import ru.kpfu.itis.net.message.*;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean connected = false;


    public GameClient(String host, int port, String name) {
        this.host = host;
        this.port = port;
        this.name = name;
    }

    public void connect() throws Exception {
        socket = new Socket(host, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer.println(new HelloMessage(name).serialize());
        running = true;
        connected = true;
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
                if (connected) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                "Соединение с сервером разорвано",
                                "Ошибка соединения",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            } finally {
                close();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void handleLine(String line) {
        Message message = Protocol.parse(line);
        if (message == null) return;

        if (message instanceof WelcomeMessage welcome) {
            handleWelcome(welcome);
        } else if (message instanceof StateMessage stateMsg) {
            handleState(stateMsg);
        } else if (message instanceof StartMessage) {
        } else if (message instanceof GameOverMessage gameOver) {
            handleGameOver(gameOver);
        }
    }

    private void handleWelcome(WelcomeMessage message) {
        String[] rows = message.map().split(",");
        char[][] grid = new char[message.height()][message.width()];
        for (int y = 0; y < message.height(); y++) {
            grid[y] = rows[y].toCharArray();
        }
        state = new GameState(message.width(), message.height(), grid, message.playerId(), name);
    }

    private void handleState(StateMessage message) {
        if (state == null) return;
        if (message.grid() != null && !message.grid().isEmpty()) {
            String[] rows = message.grid().split("/");
            char[][] grid = new char[rows.length][];
            for (int r = 0; r < rows.length; r++) {
                grid[r] = rows[r].toCharArray();
            }
            state.updateGrid(grid);
        }
        state.applySnapshot(message.players(), message.bombs(), message.explosions());
    }

    private void handleGameOver(GameOverMessage message) {
        if (state == null) return;
        state.setGameOver(message.winnerId(), message.winnerName());
    }

    private void sendInput() {
        if (!running || writer == null) return;
        GameState currentState = state;
        if (currentState == null) return;
        boolean sendBomb = bomb;
        bomb = false;
        if (currentState.isGameOver()) return;
        InputMessage inputMsg = new InputMessage(
                currentState.ownPlayerId(),
                up,
                down,
                left,
                right,
                sendBomb
        );
        writer.println(inputMsg.serialize());
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
        connected = false;
        inputTimer.cancel();

        for (Thread thread : threads) {
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }

        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }
}

