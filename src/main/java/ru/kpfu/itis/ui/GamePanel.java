package ru.kpfu.itis.ui;

import ru.kpfu.itis.model.GameState;
import ru.kpfu.itis.model.Player;
import ru.kpfu.itis.net.GameClient;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;

public class GamePanel extends JPanel {
    private final GameClient client;
    private final Timer repaintTimer;
    private volatile boolean connectionLost = false;
    private int tileSize = 48; // Начальный размер тайла
    private int offsetX = 0;
    private int offsetY = 0;
    private int gameWidth = 0;
    private int gameHeight = 0;

    public GamePanel(GameClient client) {
        this.client = client;
        setFocusable(true);
        setPreferredSize(new Dimension(800, 640));

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recalculateLayout();
                repaint();
            }
        });

        addKeyListener(new KeyHandler());
        repaintTimer = new Timer(30, e -> repaint());
        repaintTimer.start();
    }

    private void recalculateLayout() {
        GameState state = client.getStateSnapshot();
        if (state == null) return;

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        int maxTileWidth = (panelWidth - 40) / state.width();
        int maxTileHeight = (panelHeight - 60) / (state.height() + 1); // +1 для панели информации

        tileSize = Math.min(maxTileWidth, maxTileHeight);
        tileSize = Math.max(16, Math.min(64, tileSize)); // Ограничиваем min/max размер

        gameWidth = state.width() * tileSize;
        gameHeight = (state.height() * tileSize) + 40; // + панель информации

        offsetX = (panelWidth - gameWidth) / 2;
        offsetY = (panelHeight - gameHeight) / 2;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(new Color(20, 20, 20));
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (connectionLost) {
            drawConnectionLost(g2);
            return;
        }

        GameState state = client.getStateSnapshot();
        if (state == null) {
            drawCentered(g2, "Ожидание сервера...");
            return;
        }

        if (state.isGameOver()) {
            drawGameOver(g2, state);
            return;
        }

        if (tileSize == 0 || offsetX == 0) {
            recalculateLayout();
        }

        drawGameField(g2, state);

        drawUI(g2, state);
    }

    private void drawGameField(Graphics2D g2, GameState state) {
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(offsetX - 10, offsetY - 10,
                gameWidth + 20, state.height() * tileSize + 20);

        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                char c = state.cellAt(x, y);
                int px = offsetX + x * tileSize;
                int py = offsetY + y * tileSize;

                if (c == '#') {
                    g2.setColor(new Color(80, 80, 120));
                    g2.fillRect(px, py, tileSize, tileSize);
                    g2.setColor(new Color(50, 50, 90));
                    g2.fillRect(px + 2, py + 2, tileSize - 4, tileSize - 4);
                } else if (c == '*') {
                    g2.setColor(new Color(150, 110, 70));
                    g2.fillRect(px, py, tileSize, tileSize);
                    g2.setColor(new Color(120, 80, 50));
                    for (int i = 0; i < 3; i++) {
                        g2.drawRect(px + 4 + i * 3, py + 4, 2, tileSize - 8);
                        g2.drawRect(px + 4, py + 4 + i * 3, tileSize - 8, 2);
                    }
                } else {
                    g2.setColor(new Color(60, 100, 60));
                    g2.fillRect(px, py, tileSize, tileSize);
                    g2.setColor(new Color(80, 120, 80, 100));
                    g2.fillRect(px + 1, py + 1, tileSize - 2, tileSize - 2);
                }

                g2.setColor(new Color(0, 0, 0, 30));
                g2.drawRect(px, py, tileSize, tileSize);
            }
        }

        g2.setColor(new Color(255, 200, 40, 170));
        state.explosions().forEach(ex -> {
            int px = offsetX + ex.x() * tileSize;
            int py = offsetY + ex.y() * tileSize;
            g2.fillRect(px, py, tileSize, tileSize);

            int pulse = (int) (Math.sin(System.currentTimeMillis() / 100.0) * 5 + 5);
            g2.setColor(new Color(255, 100, 0, 100));
            g2.fillRect(px - pulse/2, py - pulse/2, tileSize + pulse, tileSize + pulse);
        });

        state.bombs().forEach(b -> {
            int px = offsetX + b.x() * tileSize;
            int py = offsetY + b.y() * tileSize;

            int pulse = (int) (Math.sin(System.currentTimeMillis() / 200.0) * 3 + 3);

            g2.setColor(new Color(100, 60, 20));
            g2.fillOval(px + tileSize/4, py + tileSize/4, tileSize/2, tileSize/2);

            g2.setColor(Color.ORANGE);
            g2.fillOval(px + tileSize/4 + pulse, py + tileSize/4 + pulse,
                    tileSize/2 - pulse*2, tileSize/2 - pulse*2);

            g2.setColor(Color.RED);
            g2.fillOval(px + tileSize/2 - 2, py + tileSize/4 - 4, 4, 8);
        });

        Map<Integer, Player> players = state.players();
        for (Player p : players.values()) {
            if (!p.alive()) continue;

            int px = offsetX + p.x() * tileSize;
            int py = offsetY + p.y() * tileSize;

            Color playerColor;
            if (p.id() == state.ownPlayerId()) {
                playerColor = new Color(0, 200, 255);
            } else {
                int hue = (p.id() * 137) % 360;
                playerColor = Color.getHSBColor(hue / 360f, 0.8f, 0.9f);
            }

            g2.setColor(playerColor);
            g2.fillOval(px + tileSize/4, py + tileSize/4, tileSize/2, tileSize/2);

            g2.setColor(playerColor.darker());
            g2.drawOval(px + tileSize/4, py + tileSize/4, tileSize/2, tileSize/2);

            g2.setColor(Color.WHITE);
            g2.fillOval(px + tileSize/2 - 6, py + tileSize/2 - 2, 4, 4);
            g2.fillOval(px + tileSize/2 + 2, py + tileSize/2 - 2, 4, 4);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, Math.max(10, tileSize/4)));
            FontMetrics fm = g2.getFontMetrics();
            int nameWidth = fm.stringWidth(p.name());
            g2.drawString(p.name(), px + (tileSize - nameWidth)/2, py - 5);

            if (p.id() == state.ownPlayerId()) {
                g2.setColor(Color.YELLOW);
                g2.setFont(new Font("Arial", Font.BOLD, Math.max(8, tileSize/5)));
                String bombText = "B: " + p.bombsAvailable();
                int bombWidth = g2.getFontMetrics().stringWidth(bombText);
                g2.drawString(bombText, px + (tileSize - bombWidth)/2, py + tileSize + 12);
            }
        }
    }

    private void drawUI(Graphics2D g2, GameState state) {
        int infoY = offsetY + state.height() * tileSize;

        g2.setColor(new Color(30, 30, 50, 220));
        g2.fillRect(offsetX, infoY, gameWidth, 40);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(14, tileSize/3)));

        String playerInfo = state.playerName() + " | Бомбы: " + state.selfBombs();
        String gameInfo = "Игроков онлайн: " + state.players().size();

        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(playerInfo, offsetX + 10, infoY + 25);

        int gameInfoWidth = fm.stringWidth(gameInfo);
        g2.drawString(gameInfo, offsetX + gameWidth - gameInfoWidth - 10, infoY + 25);

        g2.setColor(new Color(255, 255, 255, 50));
        g2.drawLine(offsetX, infoY, offsetX + gameWidth, infoY);

        if (tileSize > 30) {
            g2.setFont(new Font("Arial", Font.PLAIN, Math.max(10, tileSize/5)));
            g2.setColor(new Color(255, 255, 255, 150));
            String controls = "Управление: WASD/Стрелки - движение, Пробел - бомба";
            int controlsWidth = g2.getFontMetrics().stringWidth(controls);
            g2.drawString(controls, offsetX + (gameWidth - controlsWidth)/2, infoY - 10);
        }
    }

    private void drawGameOver(Graphics2D g2, GameState state) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(36, getWidth()/20)));
        String title = "ИГРА ОКОНЧЕНА";
        FontMetrics fm = g2.getFontMetrics();
        int titleW = fm.stringWidth(title);
        g2.drawString(title, (getWidth() - titleW) / 2, getHeight() / 2 - 60);

        Integer winnerId = state.winnerId();
        String winnerName = state.winnerName();

        if (winnerId != null && winnerId > 0) {
            boolean isWinner = winnerId.equals(state.ownPlayerId());
            g2.setFont(new Font("Arial", Font.BOLD, Math.max(24, getWidth()/30)));
            String result = isWinner ? "ВЫ ПОБЕДИЛИ!" : "ПОБЕДИТЕЛЬ: " + winnerName;
            g2.setColor(isWinner ? Color.GREEN : Color.YELLOW);
            int resultW = g2.getFontMetrics().stringWidth(result);
            g2.drawString(result, (getWidth() - resultW) / 2, getHeight() / 2 + 20);
        } else {
            g2.setFont(new Font("Arial", Font.BOLD, Math.max(24, getWidth()/30)));
            g2.setColor(Color.ORANGE);
            String draw = "НИЧЬЯ";
            int drawW = g2.getFontMetrics().stringWidth(draw);
            g2.drawString(draw, (getWidth() - drawW) / 2, getHeight() / 2 + 20);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, Math.max(16, getWidth()/50)));
        g2.setColor(new Color(255, 255, 255, 150));
        String hint = "Закройте окно для возврата в меню";
        int hintW = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, (getWidth() - hintW) / 2, getHeight() / 2 + 80);
    }

    private void drawConnectionLost(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(36, getWidth()/20)));
        String title = "СОЕДИНЕНИЕ РАЗОРВАНО";
        FontMetrics fm = g2.getFontMetrics();
        int titleW = fm.stringWidth(title);
        g2.drawString(title, (getWidth() - titleW) / 2, getHeight() / 2 - 60);

        g2.setFont(new Font("Arial", Font.PLAIN, Math.max(18, getWidth()/40)));
        String message = "Перезапустите игру для повторного подключения";
        int msgW = g2.getFontMetrics().stringWidth(message);
        g2.drawString(message, (getWidth() - msgW) / 2, getHeight() / 2 + 20);
    }

    private void drawCentered(Graphics2D g2, String text) {
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, Math.max(18, getWidth()/30)));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text);
        int h = fm.getHeight();
        g2.drawString(text, (getWidth() - w) / 2, getHeight() / 2 + h/2);
    }

    private class KeyHandler extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W, KeyEvent.VK_UP -> client.setInput(GameClient.InputField.UP, true);
                case KeyEvent.VK_S, KeyEvent.VK_DOWN -> client.setInput(GameClient.InputField.DOWN, true);
                case KeyEvent.VK_A, KeyEvent.VK_LEFT -> client.setInput(GameClient.InputField.LEFT, true);
                case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> client.setInput(GameClient.InputField.RIGHT, true);
                case KeyEvent.VK_SPACE -> client.setInput(GameClient.InputField.BOMB, true);
                case KeyEvent.VK_F11 -> toggleFullscreen();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W, KeyEvent.VK_UP -> client.setInput(GameClient.InputField.UP, false);
                case KeyEvent.VK_S, KeyEvent.VK_DOWN -> client.setInput(GameClient.InputField.DOWN, false);
                case KeyEvent.VK_A, KeyEvent.VK_LEFT -> client.setInput(GameClient.InputField.LEFT, false);
                case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> client.setInput(GameClient.InputField.RIGHT, false);
            }
        }

        private void toggleFullscreen() {
            GameWindow window = (GameWindow) getTopLevelAncestor();
            if (window != null) {
                window.toggleFullscreen();
            }
        }
    }
}