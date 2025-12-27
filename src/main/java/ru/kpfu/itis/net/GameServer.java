package ru.kpfu.itis.net;

import ru.kpfu.itis.model.Bomb;
import ru.kpfu.itis.model.Explosion;
import ru.kpfu.itis.model.InputState;
import ru.kpfu.itis.model.Player;
import ru.kpfu.itis.net.Protocol;
import ru.kpfu.itis.net.message.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private final int port;
    private final int width = 15;
    private final int height = 13;
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final Map<Integer, Player> players = new ConcurrentHashMap<>();
    private final Map<Integer, ClientHandler> handlers = new ConcurrentHashMap<>();
    private final Map<Integer, InputState> inputs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> moveCooldown = new ConcurrentHashMap<>();
    private final List<Bomb> bombs = Collections.synchronizedList(new ArrayList<>());
    private final List<Explosion> explosions = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final char[][] grid;
    private long tick;
    private volatile boolean gameOver;
    private volatile Integer winnerId;
    private volatile String winnerName;

    public GameServer(int port) {
        this.port = port;
        this.grid = generateMap();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop);
        acceptThread.setDaemon(true);
        acceptThread.start();
        loop.scheduleAtFixedRate(this::gameTick, 100, 70, TimeUnit.MILLISECONDS);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            } catch (IOException ignored) {
            }
        }
    }

    private void gameTick() {
        tick++;
        updatePlayers();
        updateBombs();
        updateExplosions();
        checkGameOver();
        broadcastState();
    }

    private void updatePlayers() {
        for (Player p : players.values()) {
            if (!p.alive()) continue;
            InputState input = inputs.getOrDefault(p.id(), InputState.empty());
            long last = moveCooldown.getOrDefault(p.id(), 0L);
            if (tick - last < 2) continue;
            int dx = 0;
            int dy = 0;
            if (input.up()) dy = -1;
            else if (input.down()) dy = 1;
            else if (input.left()) dx = -1;
            else if (input.right()) dx = 1;
            int nx = p.x() + dx;
            int ny = p.y() + dy;
            if (canStep(nx, ny)) {
                players.put(p.id(), new Player(p.id(), p.name(), nx, ny, true, p.bombsAvailable()));
                moveCooldown.put(p.id(), tick);
            }
            if (input.bomb()) placeBomb(p);
            inputs.put(p.id(), new InputState(input.up(), input.down(), input.left(), input.right(), false));
        }
    }

    private boolean canStep(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        char cell = grid[y][x];
        if (cell == '#' || cell == '*') return false;
        synchronized (bombs) {
            for (Bomb b : bombs) {
                if (b.x() == x && b.y() == y) return false;
            }
        }
        return true;
    }

    private void placeBomb(Player p) {
        synchronized (bombs) {
            for (Bomb b : bombs) {
                if (b.x() == p.x() && b.y() == p.y()) return;
            }
            if (p.bombsAvailable() <= 0) return;
            bombs.add(new Bomb(p.id(), p.x(), p.y(), 35));
            players.put(p.id(), new Player(p.id(), p.name(), p.x(), p.y(), p.alive(), p.bombsAvailable() - 1));
        }
    }

    private void updateBombs() {
        synchronized (bombs) {
            List<Bomb> expired = new ArrayList<>();
            List<Bomb> updated = new ArrayList<>();
            for (Bomb b : bombs) {
                Bomb next = b.tick();
                if (next.timer() <= 0) expired.add(next);
                else updated.add(next);
            }
            bombs.clear();
            bombs.addAll(updated);
            for (Bomb b : expired) explode(b);
        }
    }

    private void explode(Bomb bomb) {
        List<int[]> cells = new ArrayList<>();
        cells.add(new int[]{bomb.x(), bomb.y()});
        int power = 3;
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int i = 1; i <= power; i++) {
                int nx = bomb.x() + dir[0] * i;
                int ny = bomb.y() + dir[1] * i;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) break;
                char cell = grid[ny][nx];
                cells.add(new int[]{nx, ny});
                if (cell == '#') break;
                if (cell == '*') {
                    grid[ny][nx] = '.';
                    break;
                }
            }
        }
        for (int[] c : cells) {
            explosions.add(new Explosion(c[0], c[1], 10));
            for (Player p : players.values()) {
                if (p.alive() && p.x() == c[0] && p.y() == c[1]) {
                    players.put(p.id(), new Player(p.id(), p.name(), p.x(), p.y(), false, p.bombsAvailable()));
                }
            }
        }
        Player owner = players.get(bomb.ownerId());
        if (owner != null) {
            players.put(owner.id(), new Player(owner.id(), owner.name(), owner.x(), owner.y(), owner.alive(), owner.bombsAvailable() + 1));
        }
    }

    private void updateExplosions() {
        synchronized (explosions) {
            List<Explosion> next = new ArrayList<>();
            for (Explosion e : explosions) {
                Explosion n = e.tick();
                if (n.ttl() > 0) next.add(n);
            }
            explosions.clear();
            explosions.addAll(next);
        }
    }

    private void checkGameOver() {
        if (gameOver) return;
        if (players.size() < 2) return;
        List<Player> alive = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.alive()) alive.add(p);
        }
        if (alive.size() == 1) {
            gameOver = true;
            Player winner = alive.getFirst();
            winnerId = winner.id();
            winnerName = winner.name();
            for (ClientHandler h : handlers.values()) {
                GameOverMessage gameOverMsg = new GameOverMessage(winnerId, winnerName);
                h.send(gameOverMsg.serialize());
            }
        } else if (alive.isEmpty()) {
            gameOver = true;
            for (ClientHandler h : handlers.values()) {
                GameOverMessage gameOverMsg = new GameOverMessage(0, "DRAW");
                h.send(gameOverMsg.serialize());
            }
        }
    }

    private void broadcastState() {
        if (gameOver) return;
        StateMessage stateMsg = createStateMessage();
        String packet = stateMsg.serialize();
        for (ClientHandler h : handlers.values()) {
            h.send(packet);
        }
    }

    private StateMessage createStateMessage() {
        Map<Integer, Player> playersMap = new HashMap<>(players);
        List<Bomb> bombsList;
        List<Explosion> explosionsList;
        synchronized (bombs) {
            bombsList = new ArrayList<>(bombs);
        }
        synchronized (explosions) {
            explosionsList = new ArrayList<>(explosions);
        }
        return new StateMessage(tick, playersMap, bombsList, explosionsList, encodeGrid());
    }

    private char[][] generateMap() {
        char[][] m = new char[height][width];
        Random random = new Random();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y == 0 || x == 0 || y == height - 1 || x == width - 1) m[y][x] = '#';
                else if (x % 2 == 0 && y % 2 == 0) m[y][x] = '#';
                else {
                    if (random.nextDouble() < 0.35) m[y][x] = '*';
                    else m[y][x] = '.';
                }
            }
        }
        clearSpawn(m, 1, 1);
        clearSpawn(m, width - 2, height - 2);
        clearSpawn(m, 1, height - 2);
        clearSpawn(m, width - 2, 1);
        return m;
    }

    private void clearSpawn(char[][] m, int sx, int sy) {
        Set<int[]> cells = Set.of(
                new int[]{sx, sy},
                new int[]{sx + 1, sy},
                new int[]{sx, sy + 1}
        );
        for (int[] c : cells) {
            if (c[0] >= 0 && c[0] < width && c[1] >= 0 && c[1] < height) m[c[1]][c[0]] = '.';
        }
    }

    private String encodeGrid() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            sb.append(new String(grid[y]));
            if (y < height - 1) sb.append(Protocol.getGridRowDelimiter());
        }
        return sb.toString();
    }

    private class ClientHandler {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private int playerId;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        void start() {
            Thread t = new Thread(this::run);
            t.setDaemon(true);
            t.start();
        }

        void run() {
            try {
                String line = in.readLine();
                if (line == null) return;
                Message message = Protocol.parse(line);
                if (message instanceof HelloMessage hello) {
                    playerId = idGen.getAndIncrement();
                    players.put(playerId, new Player(playerId, hello.name(), spawnX(playerId), spawnY(playerId), true, 2));
                    inputs.put(playerId, InputState.empty());
                    handlers.put(playerId, this);
                    sendWelcome();
                    sendInitialState();
                }
                while ((line = in.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException ignored) {
            } finally {
                disconnect();
            }
        }

        void handle(String line) {
            Message message = Protocol.parse(line);
            if (message instanceof InputMessage input) {
                if (!players.containsKey(input.playerId())) return;
                inputs.put(input.playerId(), new InputState(input.up(), input.down(), input.left(), input.right(), input.bomb()));
            }
        }

        void sendWelcome() {
            StringBuilder mapBuilder = new StringBuilder();
            for (int y = 0; y < height; y++) {
                mapBuilder.append(new String(grid[y]));
                if (y < height - 1) mapBuilder.append(",");
            }
            WelcomeMessage welcome = new WelcomeMessage(playerId, width, height, mapBuilder.toString());
            out.println(welcome.serialize());
            out.flush();
            StartMessage start = new StartMessage();
            out.println(start.serialize());
            out.flush();
        }

        void sendInitialState() {
            if (gameOver) return;
            StateMessage stateMsg = createStateMessage();
            send(stateMsg.serialize());
        }

        void send(String packet) {
            out.println(packet);
            out.flush();
        }

        void disconnect() {
            handlers.remove(playerId);
            players.remove(playerId);
            inputs.remove(playerId);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private int spawnX(int id) {
        return switch (id % 4) {
            case 1 -> 1;
            case 2 -> width - 2;
            case 3 -> 1;
            default -> width - 2;
        };
    }

    private int spawnY(int id) {
        return switch (id % 4) {
            case 1 -> 1;
            case 2 -> height - 2;
            case 3 -> height - 2;
            default -> 1;
        };
    }
}

