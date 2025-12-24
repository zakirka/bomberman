package ru.kpfu.itis.ui;

import ru.kpfu.itis.net.GameClient;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameWindow extends JFrame {
    private final GameClient client;
    private final GamePanel panel;
    private boolean isFullscreen = false;
    private GraphicsDevice device;

    public GameWindow(GameClient client) {
        this.client = client;
        setTitle("Bomberman");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        panel = new GamePanel(client);
        add(panel, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
                dispose();
                LauncherFrame launcher = new LauncherFrame();
                launcher.setVisible(true);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                if (isFullscreen) {
                    toggleFullscreen();
                }
            }
        });
    }

    public void toggleFullscreen() {
        dispose();
        setUndecorated(!isFullscreen);

        if (!isFullscreen) {
            device.setFullScreenWindow(this);
            isFullscreen = true;
        } else {
            device.setFullScreenWindow(null);
            setSize(800, 640);
            setLocationRelativeTo(null);
            isFullscreen = false;
        }

        setVisible(true);
        panel.requestFocusInWindow();
    }
}