package org.example.ui;

import org.example.net.GameClient;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameWindow extends JFrame {
    private final GameClient client;

    public GameWindow(GameClient client) {
        this.client = client;
        setTitle("Bomberman");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        GamePanel panel = new GamePanel(client);
        add(panel, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
            }
        });
    }
}

