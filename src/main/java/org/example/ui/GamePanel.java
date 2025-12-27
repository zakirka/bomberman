package org.example.ui;

import org.example.model.GameState;
import org.example.model.Player;
import org.example.net.GameClient;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;

public class GamePanel extends JPanel {
    private final GameClient client;
    private final Timer repaintTimer;

    public GamePanel(GameClient client) {
        this.client = client;
        setFocusable(true);
        setPreferredSize(new Dimension(800, 640));
        addKeyListener(new KeyHandler());
        repaintTimer = new Timer(30, e -> repaint());
        repaintTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GameState state = client.getStateSnapshot();
        if (state == null) {
            drawCentered(g2, "Ожидание сервера...");
            return;
        }
        if (state.isGameOver()) {
            drawGameOver(g2, state);
            return;
        }
        int tile = state.tileSize();
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                char c = state.cellAt(x, y);
                if (c == '#') g2.setColor(new Color(60, 60, 60));
                else if (c == '*') g2.setColor(new Color(130, 90, 50));
                else g2.setColor(new Color(30, 160, 70));
                g2.fillRect(x * tile, y * tile, tile, tile);
                g2.setColor(new Color(0, 0, 0, 40));
                g2.drawRect(x * tile, y * tile, tile, tile);
            }
        }
        g2.setColor(Color.ORANGE);
        state.bombs().forEach(b -> {
            int px = b.x() * tile;
            int py = b.y() * tile;
            g2.fillOval(px + tile / 6, py + tile / 6, tile * 2 / 3, tile * 2 / 3);
        });
        g2.setColor(new Color(255, 200, 40, 170));
        state.explosions().forEach(ex -> {
            int px = ex.x() * tile;
            int py = ex.y() * tile;
            g2.fillRect(px, py, tile, tile);
        });
        Map<Integer, Player> players = state.players();
        for (Player p : players.values()) {
            if (!p.alive()) continue;
            int px = p.x() * tile;
            int py = p.y() * tile;
            g2.setColor(p.id() == state.ownPlayerId() ? Color.CYAN : Color.RED);
            g2.fillOval(px + tile / 8, py + tile / 8, tile * 3 / 4, tile * 3 / 4);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.drawString(p.name(), px + 4, py + 14);
        }
        g2.setColor(Color.WHITE);
        g2.fillRect(0, state.height() * tile, getWidth(), 40);
        g2.setColor(Color.BLACK);
        g2.drawString("Игрок: " + state.playerName() + " | Бомбы: " + state.selfBombs(), 10, state.height() * tile + 20);
    }

    private void drawGameOver(Graphics2D g2, GameState state) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "ИГРА ОКОНЧЕНА";
        int titleW = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (getWidth() - titleW) / 2, getHeight() / 2 - 60);
        Integer winnerId = state.winnerId();
        String winnerName = state.winnerName();
        if (winnerId != null && winnerId > 0) {
            boolean isWinner = winnerId.equals(state.ownPlayerId());
            g2.setFont(new Font("Arial", Font.BOLD, 24));
            String result = isWinner ? "ВЫ ПОБЕДИЛИ!" : "ПОБЕДИТЕЛЬ: " + winnerName;
            g2.setColor(isWinner ? Color.GREEN : Color.YELLOW);
            int resultW = g2.getFontMetrics().stringWidth(result);
            g2.drawString(result, (getWidth() - resultW) / 2, getHeight() / 2 + 20);
        } else {
            g2.setFont(new Font("Arial", Font.BOLD, 24));
            g2.setColor(Color.ORANGE);
            String draw = "НИЧЬЯ";
            int drawW = g2.getFontMetrics().stringWidth(draw);
            g2.drawString(draw, (getWidth() - drawW) / 2, getHeight() / 2 + 20);
        }
    }

    private void drawCentered(Graphics2D g2, String text) {
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        int w = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (getWidth() - w) / 2, getHeight() / 2);
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
    }
}

