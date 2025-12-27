package org.example.net;

import org.example.model.Bomb;
import org.example.model.Explosion;
import org.example.model.InputState;
import org.example.model.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
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
                h.send("GAME_OVER|" + winnerId + "|" + winnerName);
            }
        } else if (alive.isEmpty()) {
            gameOver = true;
            for (ClientHandler h : handlers.values()) {
                h.send("GAME_OVER|0|DRAW");
            }
        }
    }

    private void broadcastState() {
        if (gameOver) return;
        StringBuilder sb = new StringBuilder();
        sb.append("STATE|").append(tick);
        for (Player p : players.values()) {
            sb.append("|P,").append(p.id()).append(",").append(p.x()).append(",").append(p.y())
                    .append(",").append(p.alive()).append(",").append(p.bombsAvailable()).append(",").append(p.name());
        }
        synchronized (bombs) {
            for (Bomb b : bombs) {
                sb.append("|B,").append(b.ownerId()).append(",").append(b.x()).append(",").append(b.y()).append(",").append(b.timer());
            }
        }
        synchronized (explosions) {
            for (Explosion e : explosions) {
                sb.append("|F,").append(e.x()).append(",").append(e.y()).append(",").append(e.ttl());
            }
        }
        sb.append("|M,").append(encodeGrid());
        String packet = sb.toString();
        for (ClientHandler h : handlers.values()) {
            h.send(packet);
        }
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
            if (y < height - 1) sb.append("/");
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
                if (line.startsWith("HELLO")) {
                    String name = line.split("\\|")[1];
                    playerId = idGen.getAndIncrement();
                    players.put(playerId, new Player(playerId, name, spawnX(playerId), spawnY(playerId), true, 2));
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
            String[] p = line.split("\\|");
            if (p.length < 2) return;
            if ("INPUT".equals(p[0])) {
                int id = Integer.parseInt(p[1]);
                if (!players.containsKey(id)) return;
                boolean up = Boolean.parseBoolean(p[2]);
                boolean down = Boolean.parseBoolean(p[3]);
                boolean left = Boolean.parseBoolean(p[4]);
                boolean right = Boolean.parseBoolean(p[5]);
                boolean bomb = Boolean.parseBoolean(p[6]);
                inputs.put(id, new InputState(up, down, left, right, bomb));
            }
        }

        void sendWelcome() {
            StringBuilder mapBuilder = new StringBuilder();
            for (int y = 0; y < height; y++) {
                mapBuilder.append(new String(grid[y]));
                if (y < height - 1) mapBuilder.append(",");
            }
            out.println("WELCOME|" + playerId + "|" + width + "|" + height + "|" + mapBuilder);
            out.flush();
            out.println("START");
            out.flush();
        }

        void sendInitialState() {
            if (gameOver) return;
            StringBuilder sb = new StringBuilder();
            sb.append("STATE|").append(tick);
            for (Player p : players.values()) {
                sb.append("|P,").append(p.id()).append(",").append(p.x()).append(",").append(p.y())
                        .append(",").append(p.alive()).append(",").append(p.bombsAvailable()).append(",").append(p.name());
            }
            synchronized (bombs) {
                for (Bomb b : bombs) {
                    sb.append("|B,").append(b.ownerId()).append(",").append(b.x()).append(",").append(b.y()).append(",").append(b.timer());
                }
            }
            synchronized (explosions) {
                for (Explosion e : explosions) {
                    sb.append("|F,").append(e.x()).append(",").append(e.y()).append(",").append(e.ttl());
                }
            }
            sb.append("|M,").append(encodeGrid());
            send(sb.toString());
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

